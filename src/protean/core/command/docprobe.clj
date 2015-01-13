(ns protean.core.command.docprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [ring.util.codec :as cod]
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

(defn- bomb [msg]
  (println (aa/red msg))
    (System/exit 0))

(defn staging-directory []
  (let [target (cfg/target-dir)]
    (str target "/silk_staging")))

(defn spit-to
  "Will make directory if does not exist before spitting to file."
  [target content]
  (.mkdirs (file (.getParent (.getAbsoluteFile (File. target)))))
  (spit target content))

(defn fname [p]
  (subs p (+ (.lastIndexOf p (dsk/fs)) 1) (.lastIndexOf p ".")))

;; =============================================================================
;; Probe config
;; =============================================================================

(defmethod pb/config :doc [_ corpus] (hlg "building probes"))

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

(defn- doc-body-examples [target-dir full paths]
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
        :value (slurp p)})))))

(defn- doc-status-codes [target-dir tree statuses]
  "Doc response headers for a given node.
   Directory is the data directory root.
   Resource is the current endpoint (parent of headers).
   filter-exp is a regular expression to match the status codes to include."
  (.mkdirs (File. target-dir))
  (doseq [[rsp-code v] statuses]
    (spit (str target-dir (name rsp-code) ".edn")
      (pr-str { :code (name rsp-code)
                :doc (if-let [d (:doc v)] d "N/A")
                :sample-response (if-let [s (:body-example v)] (slurp s) "N/A")
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
  {:entry entry
   :engage (fn []
    (let [directory (str (staging-directory) "/data/protean-api")
          {:keys [svc method tree path] :as e} entry
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
                :curl (cod/url-decode (c/curly-entry-> (assoc-in e [:uri] uri)))
                :doc (d/get-in-tree tree [:doc])
                :desc (if-let [d (d/get-in-tree tree [:description])] d "")
                :method (name method)
                :req-body-schema-id (str "schema-" id)
                :req-body-schema-title (if schema (fname schema) "N/A")
                :req-body-schema (if schema (slurp schema) "N/A")}]
      (spit-to (str directory "/global/site.edn") (pr-str site))
      (spit-to (str directory "/api/" id ".edn") (pr-str full))
      (doc-params (str directory "/" id "/params/") (input-params tree uri))
      (doc-hdrs (str directory "/" id "/headers/") (d/req-hdrs tree))
      (doc-body-examples (str directory "/" id "/body-examples/") full (d/get-in-tree tree [:req :body-example]))
      (doc-status-codes (str directory "/" id "/status-codes-success/") tree (d/success-status tree))
      (doc-status-codes (str directory "/" id "/status-codes-error/") tree (d/error-status tree))))
  })

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
  (let [target-dir (cfg/target-dir)
        silk-staging (staging-directory)
        ; TODO should codex-dir be stored in corpus? think it is stored there since corpus not always available?
        [{:keys [tree] :as entry} _] (first results) ; can use any result since all have :codex-dir
        silk-template (d/to-path "silk_templates" tree)]
    (doseq [f (dsk/path-list silk-template)]
      (dsk/copy-recursive f silk-staging))
    (silk/spin-or-reload false silk-staging false false)
    (dsk/copy-recursive (str silk-staging "/site") target-dir)))
