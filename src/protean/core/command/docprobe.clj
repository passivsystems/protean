(ns protean.core.command.docprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as dsk]
            [silk.cli.api :as silk]
            [protean.config :as conf]
            [protean.utils :as u]
            [protean.api.codex.document :as d]
            [protean.api.codex.placeholder :as ph]
            [protean.api.protocol.http :as h]
            [protean.core.transformation.paths :as p]
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

(defn- doc-hdrs [hdrs]
  (for [[k v] hdrs] {:title k :value v}))

(defn- doc-body-examples [id type tree paths]
  (defn handle [idx itm]
    {:id (str id "-" type "-" idx)
     :#id (str "#" id "-" type "-" idx)
     :title (fname itm)
     :value (slurp-file itm tree)})
  (map-indexed handle paths))

(defn- name-pun [k] (if k (name k) ""))

; TODO: this conflates things - separate
(defn- doc-status-codes [id tree method statuses]
  (for [[rsp-code v] statuses]
    (let [schema (d/get-in-tree tree [:rsp :200 :body-schema])
          examples (doc-body-examples id "rsp" tree (:body-examples v))
          success-body (re-matches #"[2]\d\d" (name-pun (ffirst statuses)))
          add-schema (and success-body schema)]
      {:code (name rsp-code)
       :doc-md (:doc v (rsp-code h/status-docs))
       :headers (when-let [h (d/rsp-hdrs rsp-code tree)] (pr-str h))
       :rsp-first-body-example (first examples)
       :rsp-body-examples (drop 1 examples)
       :rsp-body-schema-id (when add-schema (str "schema-" (name rsp-code) "-" id))
       :#rsp-body-schema-id (when add-schema (str "#schema-" (name rsp-code) "-" id))
       :rsp-body-schema-title (when add-schema (fname schema))
       :rsp-body-schema (when add-schema (slurp-file schema tree))})))

(defn- doc-params [tree type params]
  (defn trunc [s n] (str (subs s 0 (min (count s) n))
                         (when (> (count s) n) "...")))
  (for [[k v] params]
    (let [placeholders (map second (ph/holder? (first v)))
          var-value (d/get-in-tree tree [:vars (first placeholders)])
          regex-str (str (ph/regex-pattern tree (first v)))]
      {:title k
       :param-type type
       :value-type  (trunc regex-str 100)
       :regx regex-str
       :doc-md (:doc var-value "")
       :attr (stg/join ", " (map #(stg/capitalize (name %)) (drop 1 v)))})))

(defmethod pb/build :doc [_ {:keys [locs host port] :as corpus} entry]
  (println "building a doc probe to visit " (:method entry) ":" locs)
  ; TODO review this
  ;      we should prepare staging in config step, since build is executed
  ;      multiple times for each entry.
  ;      However to resolve the silk-template we need tree (for :codex-dir)
  ;      an alternative is to store :codex-dir in corpus, but this assumes
  ;      probes never run against entries from multiple codices
  ;      (which certainly is the assumption for docprobe, which generates a single output doc)
  (prep-staging "silk_templates" (:tree entry))
  {:entry entry
   :engage (fn []
    (let [{:keys [svc method tree path codex-order] :as e} entry
          uri (p/uri (or host "host") (or port 1234) svc path)
          id (str (name method) (-> uri
                                    (ph/replace-all-with #(str "_" % "_"))
                                    (stg/replace #";" "")
                                    (URI.)
                                    (.getPath)
                                    (stg/replace #"/" "-")))
          main (filter #(get-in % [:title]) tree)
          schema (d/get-in-tree tree [:req :body-schema])
          site {:site-name (d/get-in-tree main [:title])
                :site-doc-md (if-let [d (d/get-in-tree main [:doc])] d "")}
          holders (ph/holder? uri)
          pps (into {} (for [[k v] (remove #(stg/starts-with? (second %) ";") holders)]
                         {v [k :required]}))
          mps (d/mps tree (vals (into {} holders)))
          full {:id id
                :#id (str "#" id )
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
                              (doc-params tree "Path"   pps)
                              (doc-params tree "Matrix" mps)
                              (doc-params tree "Query"  (d/qps tree))
                              (doc-params tree "Form"   (d/fps tree)))
                ; :req-headers (doc-hdrs (d/req-hdrs tree))
                :req-body-examples (doc-body-examples id "req" tree (d/get-in-tree tree [:req :body-examples]))
                :responses (concat
                            (map #(assoc % :class "success") (doc-status-codes id tree method (d/success-status tree)))
                            (map #(assoc % :class "danger") (doc-status-codes id tree method (d/error-status tree))))}]
      (spit-to (str data-dir "/global/site.edn") (pr-str site))
      (spit-to (str data-dir "/api/" id ".edn") (pr-str full))))})

;; =============================================================================
;; Probe dispatch
;; =============================================================================

(defmethod pb/dispatch :doc [_ corpus probes]
  (hlg "dispatching probes")
  (doall (map (fn [x] [(:entry x) ((:engage x))]) probes)))


;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defmethod pb/analyse :doc [_ corpus results]
  (hlg "analysing probe data")
  (silk/spin silk-staging-dir)
  (dsk/copy-recursive (str silk-staging-dir "/site") (conf/target-dir)))
