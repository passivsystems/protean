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

(defn- doc-params [target-dir params]
  "Doc query params for a given node.
   target-dir is the directory to write to.
   Params is the gen information for a resources params."
  (.mkdirs (File. target-dir))
  (doseq [[k v] params]
    (let [qm {:title k :type (:type v) :doc (:doc v)}]
      (spit (str target-dir (UUID/randomUUID) ".edn") (pr-str qm)))))

(defn- doc-hdrs [target-dir hdrs]
  "Doc headers for a given node.
   target-dir is the directory to write to.
   hdrs is the codex req/rsp headers."
  (.mkdirs (File. target-dir))
  (doseq [[k v] hdrs]
    (spit (str target-dir (UUID/randomUUID) ".edn")
          (pr-str {:title k :value v}))))

(defn- doc-body-examples [target-dir full tree paths]
  "Doc body examples for a given node.
  target-dir is the directory to write to.
  paths is a list rsp body example paths."
  (.mkdirs (File. target-dir))
  (doseq [p paths]
    (let [id (UUID/randomUUID)]
      (spit (str target-dir id ".edn")
      (pr-str {
        :id id
        :title (fname p)
        :method (get full :method)
        :path (get full :path)
        :value (slurp-file p tree)})))))

(defn- doc-status-codes [target-dir tree statuses]
  "Doc response headers for a given node.
   Directory is the data directory root.
   Resource is the current endpoint (parent of headers).
   filter-exp is a regular expression to match the status codes to include."
  (.mkdirs (File. target-dir))
  (doseq [[rsp-code v] statuses]
    (spit (str target-dir (name rsp-code) ".edn")
      (pr-str {:code (name rsp-code)
               :doc (if-let [d (:doc v)] d "N/A")
               :sample-response (if-let [s (:body-example v)] (slurp-file s tree) "N/A")
               :headers (if-let [h (d/rsp-hdrs rsp-code tree)] (pr-str h) "N/A")}))))

(defn- input-params [tree uri]
  (let [inputs (concat
                 (list uri)
                 (map val (d/get-in-tree tree [:req :query-params :required]))
                 (map val (d/get-in-tree tree [:req :query-params :optional]))
                 (map val (d/get-in-tree tree [:req :form-params :required]))
                 (map val (d/get-in-tree tree [:req :form-params :optional]))
                 (map val (d/get-in-tree tree [:req :body]))
                 (map val (d/get-in-tree tree [:req :headers])))
        extract-ph-names (fn [input]
            (map second (ph/holder? input)))
        ph-names (filter identity (reduce concat (map extract-ph-names inputs)))
        to-map (fn [varname] {varname (d/get-in-tree tree [:vars varname])})]
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
      (let [{:keys [svc method tree path] :as e} entry
            uri (p/uri "host" 1234 svc path)
            safe-uri (fn [uri] (ph/replace-all-with uri #(str "_" % "_")))
            uri-path (-> (URI. (safe-uri uri)) (.getPath))
            id (str (name method) (stg/replace uri-path #"/" "-"))
            main (filter #(get-in % [:title]) tree)
            schema (d/get-in-tree tree [:req :body-schema])
            site {:site-name (d/get-in-tree main [:title])
                  :site-doc (if-let [d (d/get-in-tree main [:doc])] d "")}
            full {:id id
                  :path (subs uri-path 1)
                  :curl (c/curly-entry-> (assoc-in e [:uri] uri))
                  :doc (d/get-in-tree tree [:doc])
                  :desc (if-let [d (d/get-in-tree tree [:description])] d "")
                  :method (name method)
                  :req-body-schema-id (str "schema-" id)
                  :req-body-schema-title (if schema (fname schema) "N/A")
                  :req-body-schema (if schema (slurp-file schema tree) "N/A")}]
        (spit-to (str data-dir "/global/site.edn") (pr-str site))
        (spit-to (str data-dir "/api/" id ".edn") (pr-str full))
        (doc-params (str data-dir "/" id "/params/") (input-params tree uri))
        (doc-hdrs (str data-dir "/" id "/headers/") (d/req-hdrs tree))
        (doc-body-examples (str data-dir "/" id "/body-examples/") full tree (d/get-in-tree tree [:req :body-example]))
        (doc-status-codes (str data-dir "/" id "/status-codes-success/") tree (d/success-status tree))
        (doc-status-codes (str data-dir "/" id "/status-codes-error/") tree (d/error-status tree))))
    }))

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
