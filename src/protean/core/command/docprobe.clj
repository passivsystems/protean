(ns protean.core.command.docprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as dsk]
            [silk.cli.api :as silk]
            [protean.config :as cfg]
            [protean.api.codex.document :as d]
            [protean.api.codex.placeholder :as ph]
            [protean.api.protocol.http :as h]
            [protean.api.transformation.coerce :as co]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.curly :as c]
            [protean.core.command.test :as t]
            [protean.core.command.probe :as pb])
  (:import java.io.File java.net.URI java.util.UUID))

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

(def silk-staging-dir (str (cfg/target-dir) "/silk_staging"))

(def data-dir (str silk-staging-dir "/data/protean-api"))

(defn- prep-staging [path tree]
  (let [codex-dir (d/get-in-tree tree [:codex-dir])
        locations (reverse (d/get-path-locations path codex-dir))]
    (if (empty? locations)
      (throw (Exception.
        (str "Could not find relative path: '" path "', looked in " locations))))
    (doseq [silk-template locations
            f (dsk/paths silk-template)]
      (dsk/copy-recursive f silk-staging-dir))))

(defn spit-to
  "Will make directory if does not exist before spitting to file."
  [target content]
  (.mkdirs (file (.getParent (.getAbsoluteFile (File. target)))))
  (spit target content))

(defn slurp-file [p tree] (slurp (d/to-path p tree)))

(defn fname [p]
  (subs p (+ (.lastIndexOf p (dsk/fs)) 1) (.lastIndexOf p ".")))

;; =============================================================================
;; Probe config
;; =============================================================================

(defmethod pb/config :doc [_ corpus]
  (hlg "building probes")
  (clean-dir silk-staging-dir)
  (clean-dir (str (cfg/target-dir) "/site"))
  (.mkdirs (file data-dir)))

;; =============================================================================
;; Probe construction
;; =============================================================================

(defn- doc-params [params]
  (if (nil? params)
    [{:title "N/A" :type "" :regx "" :doc "" :attr ""}]
    (vec (for [[k v] params]
      {:title k
       :param-type (:ptype v)
       :value-type (:type v "Undefined")
       :regx (cond
               (:regx v) (str "Custom type defined by regx: " (:regx v))
               (:type v) (str "Standard type: " (name (:type v)))
               :else     "The type was not defined")
       :doc-md (:doc v "")
       :attr (stg/join " " (:attr v))}))))

(defn- doc-hdrs [hdrs]
  (if (nil? hdrs)
    [{:title "N/A" :value ""}]
    (vec (for [[k v] hdrs] {:title k :value v}))))

(defn- doc-body-examples [id tree paths]
  (if (nil? paths)
    [{:id (str id "-" "NA") :#id (str "#" id "-" "NA") :title "N/A" :value "N/A"}]
    (vec (for [p paths]
      {:id (str id "-" (fname p))
       :#id (str "#" id "-" (fname p))
       :title (fname p)
       :value (slurp-file p tree)}))))

(defn- doc-status-codes [id tree method statuses]
  (vec (for [[rsp-code v] statuses]
    (let [schema (d/get-in-tree tree [:rsp rsp-code :body-schema])
          examples (doc-body-examples id tree (:body-examples v))]
      {:code (name rsp-code)
       :doc-md (:doc v (rsp-code h/status-docs))
       :headers (if-let [h (d/rsp-hdrs rsp-code tree)] (pr-str h) "N/A")
       :rsp-first-body-example (first examples)
       :rsp-body-examples (vec (drop 1 examples))
       :rsp-body-schema-id (str "schema-" (name rsp-code))
       :#rsp-body-schema-id (str "#schema-" (name rsp-code))
       :rsp-body-schema-title (if schema (fname schema) "N/A")
       :rsp-body-schema (if schema (slurp-file schema tree) "N/A")}))))

(defn- input-params [tree uri]
  (let [inputs {:path (list uri)
                :header (map val (d/get-in-tree tree [:req :headers]))
                :query (map val (d/qps tree true))
                :form (map val (d/fps tree true))
                :body (concat
                        (map val (d/get-in-tree tree [:req :body]))
                        (->> (d/get-in-tree tree [:req :body-examples])
                             (map #(d/to-path % tree))
                             (map #(slurp %))))}
        raw (for [[k v] inputs]
              (for [ph (seq (map second (ph/holder? v)))] {ph k}))
        placeholders (into {} (filter identity (reduce concat raw)))]
  (reduce merge
    (for [[varname type] placeholders]
      {varname
        (-> (d/get-in-tree tree [:vars varname])
            (merge {:attr (cond
                            (= type :form)  (drop 1 (d/get-in-tree tree [:req :form-params  varname]))
                            (= type :query) (drop 1 (d/get-in-tree tree [:req :query-params varname]))
                            :else           nil)})
            (merge {:ptype (stg/capitalize (name type))})
            (merge {:regx (d/get-in-tree tree [:types (d/get-in-tree tree [:vars varname :type])])}))}))))

(defmethod pb/build :doc [_ {:keys [locs] :as corpus} entry]
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
          uri (p/uri "host" 1234 svc path)
          safe-uri (fn [uri] (-> uri
                               (ph/replace-all-with #(str "_" % "_"))
                               (stg/replace #";" "")))
          uri-path (-> (URI. (safe-uri uri)) (.getPath))
          id (str (name method) (stg/replace uri-path #"/" "-"))
          main (filter #(get-in % [:title]) tree)
          schema (d/get-in-tree tree [:req :body-schema])
          site {:site-name (d/get-in-tree main [:title])
                :site-doc-md (if-let [d (d/get-in-tree main [:doc])] d "")}
          full {:id id
                :#id (str "#" id )
                :path (if (= path "/") (str svc) (str svc "/" path))
                :codex-order codex-order
                :curl (c/curly-entry-> (assoc-in e [:uri] uri))
                :doc-md (d/get-in-tree tree [:doc])
                :method (name method)
                :req-body-schema-id (str "schema-" id)
                :#req-body-schema-id (str "#schema-" id)
                :req-body-schema-title (if schema (fname schema) "N/A")
                :req-body-schema (if schema (slurp-file schema tree) "N/A")
                :req-params (doc-params (input-params tree uri))
                :req-headers (doc-hdrs (d/req-hdrs tree))
                :req-body-examples (doc-body-examples id tree (d/get-in-tree tree [:req :body-examples]))
                :responses (vec (concat
                            (map #(assoc % :class "success") (doc-status-codes id tree method (d/success-status tree)))
                            (map #(assoc % :class "danger") (doc-status-codes id tree method (d/error-status tree)))))}]
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
  (silk/spin-or-reload false silk-staging-dir false false)
  (dsk/copy-recursive (str silk-staging-dir "/site") (cfg/target-dir)))
