(ns protean.core.command.probe
  "Building probes and handling persisting/presenting raw results."
  (:require [protean.core.transformation.testy-cljhttp :as tc]
            [protean.core.command.test :as t]
            [protean.core.command.seed :as s]
            [protean.core.command.exemplify :as e]
            [protean.core.command.generate :as g]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- tl-negotiation
  "Only ever translate placeholders with seed items, no generation."
  [tests {:keys [seed] :as corpus} codices]
  (s/seeds tests (:seed corpus)))

(defn- tl-testdoc
  "Prefer seed items in placeholder translation then codex examples then
   generated values."
  [tests {:keys [seed] :as corpus} codices]
  (->> (s/seeds tests (:seed corpus))
       (e/examples codices)
       (g/generations codices)))

(defn- translate
  "Translate placeholders when visiting real nodes."
  [tests command corpus codices]
  (if (some #{:doc :test} (list command))
    (tl-testdoc tests corpus codices)
    (tl-negotiation tests corpus codices)))


;; =============================================================================
;; Probe construction
;; =============================================================================

(defmulti build (fn [command & _] command))

(defmethod build :doc [_ corpus codices]
  (println "building a doc probe")
  (println "corpus : " corpus))

(defmethod build :test [_ {:keys [locs] :as corpus} codices]
  (println "building a test probe to visit : " locs)
  [corpus
   (fn engage [{:keys [locs host port] :as corpus} codices res-fn]
     (println "dispatching a test probe to visit : " locs)
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
;; Probe data analysis
;; =============================================================================

(defmulti analyse (fn [command & _] command))

(defmethod analyse :doc [_ corpus codices result]
  (println "analysing doc probe data"))

(defmethod analyse :test [_ corpus codices results]
  (doseq [r results]
    (let [fr (first r)
          t (first fr)
          s (:status (last fr))]
      (println "Test : " t ", status : " s))))
