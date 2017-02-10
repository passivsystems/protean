(ns protean.core.command.docprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as dsk]
            [silk.cli.api :as silk]
            [protean.config :as cfg]
            [protean.core.codex.document :as d]
            [protean.core.codex.placeholder :as ph]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.coerce :as co]
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

(defn- prep-staging [silk-template]
  (doseq [f (dsk/paths silk-template)]
    (dsk/copy-recursive f silk-staging-dir)))

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
       :type (:type v "Undefined")
       :regx (cond
               (:regx v) (str "Custom type defined by regx: " (:regx v))
               (:type v) (str "Standard type: " (name (:type v)))
               :else     "The type was not defined")
       :doc (:doc v "")
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

(defn- doc-status-codes [tree method statuses]
  (vec (for [[rsp-code v] statuses]
    (let [schema (d/get-in-tree tree [:rsp rsp-code :body-schema])
          default-doc (d/get-in-tree tree [method :rsp rsp-code :doc])]
      {:code (name rsp-code)
       :doc (or (:doc v) default-doc "N/A")
       :sample-response (if-let [s (first (:body-examples v))] (slurp-file s tree) "N/A")
       :headers (if-let [h (d/rsp-hdrs rsp-code tree)] (pr-str h) "N/A")
       :rsp-body-schema-id (str "schema-" (name rsp-code))
       :#rsp-body-schema-id (str "#schema-" (name rsp-code))
       :rsp-body-schema-title (if schema (fname schema) "N/A")
       :rsp-body-schema (if schema (slurp-file schema tree) "N/A")}))))

(defn- input-params [tree uri]
  (let [inputs (concat
                 (list uri)
                 (map val (d/qps tree true))
                 (map val (d/fps tree true))
                 (map val (d/get-in-tree tree [:req :body]))
                 (->> (d/get-in-tree tree [:req :body-examples])
                      (map #(d/to-path % tree))
                      (map #(slurp %)))
                 (map val (d/get-in-tree tree [:req :headers])))
        extract-ph-names (fn [input] (map second (ph/holder? input)))
        ph-names (filter identity (reduce concat (map extract-ph-names inputs)))
        get-attr (fn [varname]
          (into [] (concat
            (drop 1 (d/get-in-tree tree [:req :query-params varname]))
            (drop 1 (d/get-in-tree tree [:req :form-params varname])))))
        to-map (fn [varname]
          {varname (-> (d/get-in-tree tree [:vars varname])
                       (merge {:attr (get-attr varname)})
                       (merge {:regx (d/get-in-tree tree [:types (d/get-in-tree tree [:vars varname :type])])}))})]
  (reduce merge (map to-map ph-names))))

(defmethod pb/build :doc [_ {:keys [locs] :as corpus} entry]
  (println "building a doc probe to visit " (:method entry) ":" locs)
  (let [silk-template (d/to-path "silk_templates" (:tree entry))]
    ; TODO review this
    ;      we should prepare staging in config step, since build is executed
    ;      multiple times for each entry.
    ;      However to resolve the silk-template we need tree (for :codex-dir)
    ;      an alternative is to store :codex-dir in corpus, but this assumes
    ;      probes never run against entries from multiple codices
    ;      (which certainly is the assumption for docprobe, which generates a single output doc)
    (prep-staging silk-template)
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
                  :site-doc (if-let [d (d/get-in-tree main [:doc])] d "")}
            full {:id id
                  :#id (str "#" id )
                  :path (str svc "/" path)
                  :codex-order codex-order
                  :curl (c/curly-entry-> (assoc-in e [:uri] uri))
                  :doc (d/get-in-tree tree [:doc])
                  :desc (if-let [d (d/get-in-tree tree [:description])] d "")
                  :method (name method)
                  :req-body-schema-id (str "schema-" id)
                  :#req-body-schema-id (str "#schema-" id)
                  :req-body-schema-title (if schema (fname schema) "N/A")
                  :req-body-schema (if schema (slurp-file schema tree) "N/A")
                  :req-params (doc-params (input-params tree uri))
                  :req-headers (doc-hdrs (d/req-hdrs tree))
                  :req-body-examples (doc-body-examples id tree (d/get-in-tree tree [:req :body-examples]))
                  :rsp-success-codes (doc-status-codes tree method (d/success-status tree))
                  :rsp-error-codes (doc-status-codes tree method (d/error-status tree))}]
        (spit-to (str data-dir "/global/site.edn") (pr-str site))
        (spit-to (str data-dir "/api/" id ".edn") (pr-str full))))}))

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
