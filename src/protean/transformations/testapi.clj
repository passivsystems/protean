(ns protean.transformations.testapi
  "Uses output from the analysis transformations to generate a
   datastructure which can drive automated testing.  This variant
   tests the live API surface area."
  (:require [protean.transformations.test :as tst])
  (:use [clojure.string :only [split]]
        [taoensso.timbre :as timbre :only (trace debug info warn error)]))

;; =============================================================================
;; Helper functions
;; =============================================================================

; refactor put in a common place
(defn substring? [sub st] (not= (.indexOf st sub) -1))

(defonce azn "Authorization")

; needs refactoring, trying to get a prototype out
; strat is either Basic or Bearer
(defn- header-authzn-> [strat seed payload]
  (let [m (last payload)]
    (if-let [auth (get-in m [:headers azn])]
      (if (and (substring? "psv+" auth) (substring? strat auth))
        (if-let [sauth (first (get-in seed [azn]))] ; note first is temporary here
          (let [n (assoc-in m [:headers azn] (str strat " " (last (split sauth #" "))))]
            (list (first payload) (second payload) n))
          payload)
        payload)
      payload)))

;(defn- query-params-> [seed payload])

;(defn- uri-> [seed payload])

(defn- seed-> [test seed]
  (->> test
       (header-authzn-> "Basic" seed)
       (header-authzn-> "Bearer" seed)
       ;query-params
       ;uri
       ))

(defn- seeds [tests seed] (if seed (map #(seed-> % seed) tests) tests))

;; =============================================================================
;; Transformation functions
;; =============================================================================

;; First sow all seed items

    ;; Loop
      ; Call whatever we can and grow seed from result if applicable
      ; Track what we have called
(defn testapi-analysis-> [host port codices corpus]
  (info "testing the API")
  (prn "seed is : " (corpus "seed"))
  (let [tests (tst/test-> host port codices corpus)
        seeded (seeds tests (corpus "seed"))]
    (println "***********************************************************")
    (prn "!!!!! ***** tests : " tests)
    (prn "!!!!! ***** seed : " seeded)

    (println "***********************************************************")


    (map #(tst/test! %) tests)))
