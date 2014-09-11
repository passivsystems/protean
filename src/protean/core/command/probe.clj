(ns protean.core.command.probe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [ring.util.codec :as cod]
            [io.aviso.ansi :as aa]
            [protean.core.protocol.http :as pth]
            [protean.core.transformation.coerce :as ptc]
            [protean.core.transformation.analysis :as a]
            [protean.core.transformation.curly :as c]
            [protean.core.transformation.testy-cljhttp :as tc]
            [protean.core.command.test :as t]
            [protean.core.command.seed :as s]
            [protean.core.command.exemplify :as e]
            [protean.core.command.generate :as g])
  (:import java.net.URI))

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

(defn- body [ctype body]
  (if-let [b body]
    (cond
      (= ctype pth/xml) (ptc/pretty-xml-> b)
      (= ctype pth/txt) b
      :else (ptc/js-> b))
    "N/A"))


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

(defmethod build :doc [_ {:keys [locs] :as corpus} codices]
  (println "building a doc probe to visit : " locs)
  [corpus
   (fn engage [{:keys [locs directory] :as corpus} codices]
     (let [e (first (a/analysis-> "host" 1234 codices corpus))
           uri-path (-> (URI. (:uri e)) (.getPath))
           path  (stg/replace uri-path #"/" "-")
           id (str (name (:method e)) path)
           body (body (get-in e [:codex :content-type]) (get-in e [:codex :body]))
           full (assoc e :id id :path (subs uri-path 1)
                         :curl (cod/url-decode (c/curly-> e))
                         :sample-response body)]
       (spit (str directory "/" id ".edn") (pr-str (update-in full [:method] name)))))])

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

(defn res-simple! [result] (println "doing nothing"))

(defn res-persist!
  "Persist result in its interim state to a store.
   In this protoype the store is the disk."
  [result]
  (println "TODO: reminder placeholder for persisting results"))


;; =============================================================================
;; Probe dispatch
;; =============================================================================

(defmulti dispatch (fn [command & _] command))

(defmethod dispatch :doc [_ corpus codices probes]
  (println "dispatching probes")
  (doall (map (fn [x] ((last x) (first x) codices)) probes)))

(defmethod dispatch :test [_ corpus codices probes]
  (hlg "dispatching probes")
  (let [res
          (doall (map (fn [x] ((last x) (first x) codices res-persist!)) probes))
        raw-posts (filter #(= (first %) 'client/post) (apply concat res))
        ps (filter #(or (:location (nth % 2)) (:body (nth % 2))) raw-posts)
        vs (remove nil? (map #(or (:location (nth % 2)) (:body (nth % 2))) ps))
        locs (for [[m p] probes] (:locs m))
        bag (assoc-in corpus [:seed] {"bag" (vec vs)})
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
  (println "analysing doc probe data"))

(defn- assess [m s phs]
  (if phs
    (if (some #{"dyn"} phs)
      (if (and (= m 'client/get) (= s 200)) false true)
      true)
    true))

(defmethod analyse :test [_ corpus codices results]
  (doseq [[method uri mp phs] results]
    (let [s (:status mp)]
      (println "Test : " method " - " uri ", status : " s ", pass : " (assess method s phs)))))
