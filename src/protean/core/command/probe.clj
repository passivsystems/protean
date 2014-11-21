(ns protean.core.command.probe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [ring.util.codec :as cod]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as dsk]
            [silk.cli.api :as silk]
            [protean.core.codex.document :as d]
            [protean.core.codex.placeholder :as ph]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.coerce :as co]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.curly :as c]
            [protean.core.transformation.testy-cljhttp :as tc]
            [protean.core.command.test :as t])
  (:import java.io.File java.net.URI java.util.UUID))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- hlr [t] (println (aa/bold-red t)))

(defn- hlg [t] (println (aa/bold-green t)))


(defn- body [tree v] (if-let [bf (:body v)] (slurp bf) "N/A"))

(defn- bomb [msg]
  (println (aa/red msg))
    (System/exit 0))

(defn- prep-docs [{:keys [directory]}]
  (if (not directory)
    (bomb "please provide \"directory\" config to generate docs")
    (if (dsk/exists-dir? directory)
      (do (dsk/delete-directory (file directory))
          (.mkdirs (file (str directory "/api"))))
      (.mkdirs (file (str directory "/api"))))))

(defn spit-to
  "Will make directory if does not exist before spitting to file."
  [target content]
  (.mkdirs (file (.getParent (.getAbsoluteFile (File. target)))))
  (spit target content))

;; =============================================================================
;; Probe config
;; =============================================================================

(defmulti config (fn [command & _] command))

(defmethod config :doc [_ corpus] (hlg "building probes"))


;; =============================================================================
;; Probe construction
;; =============================================================================

(defmulti build (fn [command & _] command))

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
   hdrs is the codex rsp headers."
  (.mkdirs (File. target-dir))
  (doseq [[k v] hdrs]
    (spit (str target-dir (UUID/randomUUID) ".edn")
          (pr-str {:title k :value v}))))

(defn- doc-status-codes [target-dir tree statuses]
  "Doc response headers for a given node.
   Directory is the data directory root.
   Resource is the current endpoint (parent of headers).
   filter-exp is a regular expression to match the status codes to include."
    (.mkdirs (File. target-dir))
    (doseq [[k v] statuses]
      (spit (str target-dir (name k) ".edn")
            (pr-str
               {:code (name k) :doc (:doc v) :sample-response (body tree v)}))
      (if (:headers v)
          (doc-hdrs (str target-dir (name k) "/" "headers" "/") (:headers v)))))

(defn- input-params [tree uri]
  (let [inputs (concat
                 (list uri)
                 (map val (d/get-in-tree tree [:req :query-params :required]))
                 (map val (d/get-in-tree tree [:req :query-params :optional]))
                 (map val (d/get-in-tree tree [:req :body]))
                 (map val (d/get-in-tree tree [:req :headers])))
        extract-ph-names (fn [input]
            (map #(nth % 1) (ph/holder? input)))
        ph-names (filter identity (reduce concat (map extract-ph-names inputs)))
        to-map (fn [varname] {varname (d/get-in-tree tree [:vars varname])})]
  (reduce merge (map to-map ph-names))))

(defmethod build :doc [_ {:keys [locs] :as corpus} codices]
  (println "building a doc probe to visit : " locs)
  (prep-docs corpus)
  [corpus
   (fn engage [{:keys [locs directory] :as corpus} codices]
     (doseq [{:keys [uri method tree] :as e} (p/analysis-> "host" 1234 codices corpus)]
       (let [safe-uri (fn [uri] (ph/replace-all-with uri #(str "_" % "_")))
             uri-path (-> (URI. (safe-uri uri)) (.getPath))
             id (str (name method) (stg/replace uri-path #"/" "-"))
             full {:id id
                   :path (subs uri-path 1)
                   :curl (cod/url-decode (c/curly-> e))
                   :doc (d/get-in-tree tree [:doc])
                   :desc (d/get-in-tree tree [:description])
                   :method (name method)}]
         (spit-to (str directory "/global/site.edn") (pr-str {:site-name (d/get-in-tree tree [:title])}))
         (spit-to (str directory "/api/" id ".edn") (pr-str full))
         (doc-params (str directory "/" id "/params/") (input-params tree uri))
         (doc-hdrs (str directory "/" id "/headers/") (d/get-in-tree tree [:rsp :headers]))
         (doc-status-codes (str directory "/" id "/status-codes-success/") tree (d/success-status tree))
         (doc-status-codes (str directory "/" id "/status-codes-error/") tree (d/error-status tree)))))])


(defmethod build :negotiate [_ {:keys [locs]} codices]
  (println "building a negotiation probe to visit : " locs))


;; =============================================================================
;; Probe result handlers
;; =============================================================================

(defn- res-simple! [result] (println "doing nothing"))

(defn- res-persist!
  "Persist result in its interim state to a store.
   In this protoype the store is the disk."
  [result]
  ;;(println "TODO: reminder placeholder for persisting results")
  )


;; =============================================================================
;; Probe dispatch
;; =============================================================================

(defmulti dispatch (fn [command & _] command))

(defmethod dispatch :doc [_ corpus codices probes]
  (hlg "dispatching probes")
  (doall (map (fn [x] ((last x) (first x) codices)) probes)))


;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defmulti analyse (fn [command & _] command))

(defmethod analyse :doc [_ corpus codices result]
  (hlg "analysing probe data")
  (let [path (.getAbsolutePath (file (:directory corpus)))
        silk-path (subs path 0 (.indexOf path (str (dsk/fs) "data" (dsk/fs))))]
    (silk/spin-or-reload false silk-path false false)))



