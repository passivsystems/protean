(ns protean.core.command.probe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [ring.util.codec :as cod]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as d]
            [protean.core.codex.document :as doc]
            [protean.core.codex.placeholder :as p]
            [protean.core.protocol.http :as pth]
            [protean.core.transformation.coerce :as co]
            [protean.core.transformation.analysis :as a]
            [protean.core.transformation.curly :as c]
            [protean.core.transformation.testy-cljhttp :as tc]
            [protean.core.command.test :as t]
            [protean.core.command.seed :as s]
            [protean.core.command.exemplify :as e]
            [protean.core.command.generate :as g])
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

(defn- tl-negotiation
  "Only ever translate placeholders with seed items, no generation."
  [tests {:keys [seed] :as corpus} codices]
  (s/seeds tests (:seed corpus)))

(defn- tl-testdoc
  "Prefer seed items in placeholder translation then codex examples then
   generated values."
  [tests {:keys [seed] :as corpus} codices]
  (->> (s/seeds tests seed)
       (e/examples codices :required)
       (g/generations codices)))

(defn- translate
  "Translate placeholders when visiting real nodes."
  [tests command corpus codices]
  (if (some #{:doc :test} (list command))
    (tl-testdoc tests corpus codices)
    (tl-negotiation tests corpus codices)))

(defn- body [tree v]
  (if-let [b (:body v)]
    (let [ctype (doc/get-in-tree tree [:codex :headers "Content-Type"])]
      (cond
        (= ctype pth/xml) (co/pretty-xml b)
        (= ctype pth/txt) b
        :else (co/pretty-js b)))
    "N/A"))

(defn- bomb [msg]
  (println (aa/red msg))
    (System/exit 0))

(defn- prep-docs [{:keys [directory]}]
  (if (not directory)
    (bomb "please provide \"directory\" config to generate docs")
    (if (d/exists-dir? directory)
      (do (d/delete-directory (file directory))
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

(defn- name-param [title]
  (if (.contains title "psv+") (stg/replace title "psv+" "*") title))

(defn- doc-params [target-dir params]
  "Doc query params for a given node.
   target-dir is the directory to write to.
   Params is the gen information for a resources params."
  (.mkdirs (File. target-dir))
  (doseq [[k v] params]
    (let [qm {:title (name-param k) :type (:type v) :doc (:doc v)}]
      (spit (str target-dir (UUID/randomUUID) ".edn") (pr-str qm)))))

(defn- doc-hdrs [target-dir hdrs]
  "Doc response headers for a given node.
   target-dir is the directory to write to.
   hdrs is the codex rsp headers."
  (.mkdirs (File. target-dir))
  (doseq [[k v] hdrs]
    (spit (str target-dir (UUID/randomUUID) ".edn")
          (pr-str {:title k :value v}))))

(defn- doc-status-codes [target-dir tree filter-exp]
  "Doc response headers for a given node.
   Directory is the data directory root.
   Resource is the current endpoint (parent of headers).
   filter-exp is a regular expression to match the status codes to include."
  (let [filter (fn [m] (seq (filter #(re-matches filter-exp (name (key %))) (:rsp m))))
        statuses (some identity (map filter tree))]
    (.mkdirs (File. target-dir))
    (doseq [[k v] statuses]
      (spit (str target-dir (UUID/randomUUID) ".edn")
            (pr-str
              {:code (name k) :doc (:doc v) :sample-response (body tree v)})))))

(defmethod build :doc [_ {:keys [locs] :as corpus} codices]
  (println "building a doc probe to visit : " locs)
  (prep-docs corpus)
  [corpus
   (fn engage [{:keys [locs directory] :as corpus} codices]
     (doseq [{:keys [uri method tree] :as e} (a/analysis-> "host" 1234 codices corpus)]
       (let [uri-path (-> (URI. uri) (.getPath))
             id (str (name method) (stg/replace uri-path #"/" "-"))
             full {:id id
                   :path (subs uri-path 1)
                   :curl (cod/url-decode (c/curly-> e))
                   :doc (doc/get-in-tree tree [:doc])
                   :desc (doc/get-in-tree tree [:description])
                   :method (name method)}]
         (spit-to (str directory "/global/site.edn") (pr-str {:site-name (doc/get-in-tree tree [:title])}))
         (spit-to (str directory "/api/" id ".edn") (pr-str full))
         (doc-params (str directory "/" id "/params/") (doc/get-in-tree tree [:req :vars]))
         (doc-hdrs (str directory "/" id "/headers/") (doc/get-in-tree tree [:rsp :headers]))
         (doc-status-codes (str directory "/" id "/status-codes-success/") tree #"2\d\d")
         (doc-status-codes (str directory "/" id "/status-codes-error/") tree #"[1345]\d\d"))))])

(defmethod build :test [_ {:keys [locs] :as corpus} codices]
  (println "building a test probe to visit : " locs)
  [corpus
   (fn engage [{:keys [locs host port] :as corpus} codices res-fn]
     (let [h (or host "localhost")
           p (or port 3000)
           tests (tc/clj-httpify h p codices corpus)
           seeded (translate tests :test corpus codices)
           results (map #(t/test! %) seeded)]
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
  (let [res
          (doall (map (fn [x] ((last x) (first x) codices res-persist!)) probes))
        raw-posts (filter #(= (first %) 'client/post) (apply concat res))
        ps (filter #(or (:location (nth % 2)) (:body (nth % 2))) raw-posts)
        vs (remove nil? (map #(or (:location (nth % 2)) (:body (nth % 2))) ps))
        locs (for [[m p] probes] (:locs m))
        bag (assoc-in corpus [:seed "bag"] (vec vs))
        np (doall (map #(build :test (assoc-in bag [:locs] %) codices) locs))
        nr (doall (map (fn [x] ((last x) (first x) codices res-persist!)) np))
        fr (remove #(or (= (first %) 'client/post) (not (some #{"seed"} (last %))))
                   (apply concat nr))]
    (concat (apply concat res) fr)))


;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defmulti analyse (fn [command & _] command))

(defmethod analyse :doc [_ corpus codices result]
  (hlg "analysing probe data")
  (println "documentation has been produced at" (.getAbsolutePath (file (:directory corpus))))
  (println "Now you can produce HTML via Silk.")) ; TODO can we integrate with Silk directly?

(defn- get? [m] (= m 'client/get))

(defn- put? [m] (= m 'client/put))

(defn- del? [m] (= m 'client/delete))

(defn- assess [m s phs]
  (if phs
    (if (some #{"dyn"} phs)
      (cond
        (and (get? m) (= s 200)) "fail"
        (and (put? m) (= s 204)) "fail"
        (and (del? m) (= s 204)) "fail"
        :else "pass")
      (if (= s 500) "error" "pass"))
    "pass"))

(defmethod analyse :test [_ corpus codices results]
  (hlg "analysing probe data")
  (doseq [[method uri mp phs] results]
    (let [status (:status mp)
          ass (assess method status phs)
          so (if (or (p/uri-ns-holder? uri) (p/authzn-holder? mp))
               (aa/bold-red "error - untested")
               (if (= ass "pass") (aa/bold-green ass) (aa/bold-red ass)))]
      (println "Test : " method " - " uri ", status - " status ": " so))))
