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
            [protean.core.command.test :as t])
  (:import java.io.File java.net.URI java.util.UUID))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- hlr [t] (println (aa/bold-red t)))

(defn- hlg [t] (println (aa/bold-green t)))

(defn- show-test [level]
  (cond
    (= level 1) (hlr "ʘ‿ʘ I'm too young to die")
    (= level 2) (hlr "⊙︿⊙ Hey not too rough")
    (= level 3) (hlr "ミ●﹏☉ミ Hurt me plenty")
    (= level 4) (hlr "✖_✖ Ultra violence")))

(defn- swap [ph tree]
  (-> ph
     (ph/holder-swap ph/holder-swap-gen tree)
     (ph/holder-swap ph/holder-swap-exp tree)))

(defn- copy-and-swap [options tree source-keys target-keys]
  (if-let [ph (d/get-in-tree tree source-keys)]
    (assoc-in options target-keys (swap ph tree))
    options))

(defn- body-to-string [options]
  (update-in options [:body] co/js)) ; TODO check content-type and set as appropriate..

(defn- content-type [options method]
  (if (and (some #{method} [:post :put])
           (not (get-in options [:headers "Content-Type"])))
    (assoc-in options [:headers h/ctype]  h/jsn-simple)
    options))

(defn- swap-options [options tree]
  (-> options
    (copy-and-swap tree [:req :query-params :required] [:query-params])
    (copy-and-swap tree [:req :query-params :optional] [:query-params]) ; TODO only include when (corpus) test level is 2?
    (copy-and-swap tree [:req :form-params] [:form-params])
    (copy-and-swap tree [:req :headers] [:headers])
    (copy-and-swap tree [:req :body] [:body])
    (body-to-string)))

(defn- prepare-requests
  "Translate placeholders when visiting real nodes."
  [analysed {:keys [seed] :as corpus}]
  (let [to-request (fn [{:keys [method uri options tree] :as entry}]
    (let [parsed-uri (:uri (swap {:uri uri} tree))] ; wrapping and unwrapping uri in map to reuse holder-swap
      (-> {:method method :uri parsed-uri :tree tree} ; TODO currently storing tree in request - should be passed separately but needs to be preserved down pipeline - should be provided to probe methods instead of codex
          (update-in [:options] swap-options tree)
          (update-in [:options] content-type method))))]
    (map to-request analysed)))

(defn- body-example [tree v] (if-let [bf (:body-example v)] (slurp bf) "N/A"))

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

(defmethod config :test [_ corpus]
  (show-test (get-in corpus [:config "test-level"] 1))
  (hlg "building probes"))

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
              {:code (name k) :doc (:doc v) :sample-response (body-example tree v)}))))
      ;(if (:headers v)
      ;    (doc-hdrs (str target-dir (name k) "/" "headers" "/") (:headers v)))))

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

(defmethod build :test [_ {:keys [locs] :as corpus} codices]
  (println "building a test probe to visit : " locs)
  [corpus
    (fn engage [{:keys [locs host port] :as corpus} codices res-fn]
     (let [h (or host "localhost")
           p (or port 3000)
           analysed (p/analysis-> host port codices corpus)
           requests (prepare-requests analysed corpus)
           results (map #(t/test! %) requests)]
       (res-fn results)
       results))])

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

(defmethod dispatch :test [_ corpus codices probes]
  (hlg "dispatching probes")
  (let [res (doall (map (fn [x] ((last x) (first x) codices res-persist!)) probes))
        raw-posts (filter #(= (:method (first %)) :post) (apply concat res))
        ps (filter #(or (:location (nth % 1)) (:body (nth % 1))) raw-posts)
        vs (remove nil? (map #(or (:location (nth % 1)) (:body (nth % 1))) ps))
        locs (for [[m p] probes] (:locs m))
        bag (assoc-in corpus [:seed "bag"] (vec vs))
        np (doall (map #(build :test (assoc-in bag [:locs] %) codices) locs))
        nr (doall (map (fn [x] ((last x) (first x) codices res-persist!)) np))
        fr (remove #(or (= (:method (first %)) :post) (not (some #{"seed"} (last %))))  (apply concat nr))]
;    (println "\nres" res)
;    (println "\nraw-posts" raw-posts)
;    (println "\nps" ps)
;    (println "\nvs" vs)
;    (println "\nlocs" locs)
;    (println "\nbag" bag)
;    (println "\nnp" np)
;    (println "\nnr" nr)
;    (println "\nfr" fr)
    (concat (apply concat res) fr)))

;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defmulti analyse (fn [command & _] command))

(defmethod analyse :doc [_ corpus codices results]
  (hlg "analysing probe data")
  (let [path (.getAbsolutePath (file (:directory corpus)))
        silk-path (subs path 0 (.indexOf path (str (dsk/fs) "data" (dsk/fs))))]
    (silk/spin-or-reload false silk-path false false)))

(defn- assess [method status tree]
  (let [expected-status (name (key (first (d/success-status tree))))]
    (if (= (str status) expected-status)
      "pass"
      (str "fail - expected status " expected-status))))

(defmethod analyse :test [_ corpus codices results]
  (hlg "analysing probe data")
;  (println "analyse" results)
  (doseq [[{:keys [method uri tree] :as request} {:keys [status] :as response}] results]
;    (println "result - request:" (dissoc request :tree))
;    (println "result - response:" response)
    (let [ass (assess method status tree)
          so (if (= ass "pass") (aa/bold-green ass) (aa/bold-red ass))]
      (println "Test : " method " - " uri ", status - " status ": " so))))

;  (doseq [[method uri mp phs] results]
;    (let [status (:status mp)
;          ass (assess method status phs)
;          so (if (or (ph/holder? uri) (ph/authzn-holder? mp)) (aa/bold-red "error - untested") (if (= ass "pass") (aa/bold-green ass) (aa/bold-red ass)))]
;      (println "Test : " method " - " uri ", status - " status ": " so))))
