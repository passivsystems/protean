(ns protean.core.command.probe
  "Building probes and handling persisting/presenting raw results."
  (:require [protean.core.transformation.testy-cljhttp :as tc]
            [protean.core.command.test :as t]
            [protean.core.command.seed :as s]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- translate
  "Translate placeholders when visiting real nodes.
   If boolean api is true we are visiting a real node.
   Selects whether to seed, generate or use examples from codices as needed."
  [tests corpus codices api]
  (if api (s/seeds tests (:seed corpus)) tests))


;; =============================================================================
;; Probe construction
;; =============================================================================

(defmulti build (fn [command & _] command))

(defmethod build :doc [_ corpus codices]
  (println "building a doc probe")
  (println "corpus : " corpus))

(defmethod build :test [_ corpus codices]
  (println "building a test probe to visit : " (:locs corpus))
  [corpus
   (fn engage [corpus codices res-fn]
     (println "dispatching a test probe to visit : " (:locs corpus))
     (let [h (:host corpus "localhost")
           p (:port corpus 3000)
           tests (tc/clj-httpify h p codices corpus)
           seeded (translate tests corpus codices (or h p))
           results (map #(t/test! %) seeded)]
       (res-fn results)
       results))])


;; =============================================================================
;; Probe result handlers
;; =============================================================================

(defn res-simple! [result] (println "doing nothing"))

(defn res-persist!
  "Persist result in its interim state to a store.
   In this protoype the store is the disk."
  [result]
  (println "persisting results"))


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
