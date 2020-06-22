(ns protean.core.command.docprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as dsk]
            [silk.cli.api :as silk]
            [protean.config :as conf]
            [protean.api.codex.document :as d]
            [protean.api.codex.placeholder :as ph]
            [protean.api.protocol.http :as h]
            [protean.api.transformation.paths :as p]
            [protean.core.transformation.curly :as c]
            [protean.core.command.probe :as pb])
  (:import java.net.URI))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- hlr [t] (println (aa/bold-red t)))
(defn- hlg [t] (println (aa/bold-green t)))

(defn- bomb [msg] (println (aa/red msg)) (System/exit 0))

(defn- clean-dir [directory]
  (if (dsk/exists-dir? directory) (dsk/delete-directory (file directory)))
  (let [created (.mkdirs (file directory))]
    (when-not created (throw (Error. "Setup failed - permissions problem ?")))))

(def silk-staging-dir (str (conf/target-dir) "/silk_staging"))

(def data-dir (str silk-staging-dir "/data/protean-api"))

(defn- prep-staging [path tree]
  (let [codex-dir (d/get-in-tree tree [:codex-dir])
        locations (reverse (d/get-path-locations (conf/protean-home) path codex-dir))]
    (if (empty? locations)
      (throw (Exception.
        (str "Could not find relative path: '" path "', looked in " locations))))
    (doseq [silk-template locations
            f (dsk/paths silk-template)]
      (dsk/copy-recursive f silk-staging-dir))))

(defn spit-to
  "Will make directory if does not exist before spitting to file."
  [target content]
  (.mkdirs (file (.getParent (.getAbsoluteFile (file target)))))
  (spit target content))

(defn slurp-file [p tree] (slurp (d/to-path (conf/protean-home) p tree)))

(defn fname [p]
  (subs p (+ (.lastIndexOf p (dsk/fs)) 1) (.lastIndexOf p ".")))

;; =============================================================================
;; Probe config
;; =============================================================================

(defmethod pb/config :doc [_ corpus]
  (hlg "building probes")
  (clean-dir silk-staging-dir)
  (clean-dir (str (conf/target-dir) "/site"))
  (.mkdirs (file data-dir)))

;; =============================================================================
;; Probe construction
;; =============================================================================

(defn- doc-body-examples [id type tree paths]
  (defn handle [idx itm]
    {:id (str id "-" type "-" idx)
     :#id (str "#" id "-" type "-" idx)
     :title (fname itm)
     :value (ph/swap (slurp-file itm tree) tree nil :gen-all true)})
  (map-indexed handle paths))

(defn- name-pun [k] (if k (name k) ""))

(defn- doc-params [tree type params]
  (defn trunc [s n] (str (subs s 0 (min (count s) n))
                         (when (> (count s) n) "...")))
  (for [[k v] params]
    (let [placeholders (map second (ph/holder? (first v)))
          var-value (d/get-in-tree tree [:vars (first placeholders)])
          regex-str (str (ph/regex-pattern tree (first v)))]
      {:title k
       :param-type type
       :value-type (trunc regex-str 50)
       :regx regex-str
       :doc-md (:doc var-value "")
       :attr (stg/join ", " (map #(stg/capitalize (name %)) (drop 1 v)))})))

; TODO: this conflates things - separate
(defn- doc-status-codes [id css tree method statuses]
  (for [[rsp-code v] statuses]
    (let [schema (:body-schema v)
          examples (doc-body-examples id "rsp" tree (:body-examples v))]
      {:code (name rsp-code)
       :class css
       :doc-md (:doc v (rsp-code h/status-docs))
       :headers (doc-params tree "Header" (d/rsp-hdrs rsp-code tree))
       :rsp-first-body-example (first examples)
       :rsp-body-examples (drop 1 examples)
       :rsp-body-schema-id (when schema (str "schema-" (name rsp-code) "-" id))
       :#rsp-body-schema-id (when schema (str "#schema-" (name rsp-code) "-" id))
       :rsp-body-schema-title (when schema (fname schema))
       :rsp-body-schema (when schema (slurp-file schema tree))})))

(defmethod pb/build :doc [_ {:keys [locs host port] :as corpus} entry]
  (println "building a doc probe to visit " (:method entry) ":" locs)
  (let [{:keys [svc method tree path codex-order] :as e} entry
        uri (p/uri (or host "host") (or port 1234) svc path)
        id (str (name method) (-> uri
                                  (ph/replace-all-with #(str "_" % "_"))
                                  (stg/replace #";" "")
                                  (URI.)
                                  (.getPath)
                                  (stg/replace #"/" "-")))
        schema (d/get-in-tree tree [:req :body-schema])
        holders (ph/holder? uri)]
    {:entry entry
     :engage
      {:id id
       :#id (str "#" id)
       :collapseId (str "collapse-" id)
       :#collapseId (str "#collapse-" id)
       :path (if (= path "/") (str svc) (str svc "/" path))
       :codex-order codex-order
       :curl (c/curly-entry-> (assoc-in e [:uri] uri))
       :doc-md (d/get-in-tree tree [:doc])
       :method (name method)
       :req-body-schema-id (when schema (str "schema-" id))
       :#req-body-schema-id (when schema (str "#schema-" id))
       :req-body-schema-title (when schema (fname schema))
       :req-body-schema (when schema (slurp-file schema tree))
       :req-params (concat
                     (doc-params tree "Header" (d/req-hdrs tree))
                     (doc-params tree "Path" (into {} (for [[k v] (remove #(stg/starts-with? (second %) ";") holders)]
                                               {v [k :required]})))
                     (doc-params tree "Matrix" (d/mps tree (vals (into {} holders))))
                     (doc-params tree "Query" (d/qps tree))
                     (doc-params tree "Form" (d/fps tree)))
       :req-body-examples (doc-body-examples id "req" tree (d/get-in-tree tree [:req :body-examples]))
       :responses (concat
                    (doc-status-codes id "table-success" tree method (d/success-status tree))
                    (doc-status-codes id "table-danger" tree method (d/error-status tree)))}}))

;; =============================================================================
;; Probe dispatch
;; =============================================================================

(defmethod pb/dispatch :doc [_ corpus probes]
  (hlg "dispatching probes")
  (doall (map (fn [x] [(:entry x) (:engage x)]) probes)))

;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defmethod pb/analyse :doc [_ corpus results]
  (hlg "analysing probe data")
  (let [tree (:tree (ffirst results))
        main (filter #(:title %) tree)
        site {:site-name (d/get-in-tree main [:title])
              :site-doc-md (str (d/get-in-tree main [:doc]))}]
    (prep-staging "silk_templates" tree)
    (spit-to (str data-dir "/global/site.edn") site)
    (doseq [[_ api] results]
      (spit-to (str data-dir "/api/" (:id api) ".edn") api)))

  (silk/spin silk-staging-dir)
  (dsk/copy-recursive (str silk-staging-dir "/site") (conf/target-dir)))
