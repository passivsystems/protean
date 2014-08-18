(ns protean.core.command.probe
  "Building probes and handling persisting/presenting raw results."
  (:require [protean.core.transformation.testy-cljhttp :as tc]
            [protean.core.command.test :as t]))

;; =============================================================================
;; Probe construction
;; =============================================================================

(defmulti build (fn [command & _] command))

(defmethod build :doc [_ corpus codices]
  (println "building a doc probe")
  (println "corpus : " corpus))

(defmethod build :test [_ corpus codices]
  (println "building a test probe")
  (fn engage [corpus codices res-fn]
    (println "engage corpus : " corpus)
    (let [h (:host corpus "localhost")
          p (:port corpus 3000)
          tests (tc/clj-httpify h p codices corpus)
          results (map #(t/test! %) tests)]
      (res-fn results))))


;; =============================================================================
;; Probe result handlers
;; =============================================================================

(defn res-simple! [result] (println "I am handling result : " result))

(defn res-stdout!
  "Take result data and process it for semi nice runtime CLI output.
   In this first form for use when integration testing the sim."
  [result]
  (doseq [r result]
    (let [t (first r)
          s (:status (last r))]
      (println "Test : " t ", status : " s))))
