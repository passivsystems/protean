(ns protean.transformations.testapi
  "Uses output from the analysis transformations to generate a
   datastructure which can drive automated testing.  This variant
   tests the live API surface area."
  (:require [protean.transformations.test :as tst])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)]))

;; =============================================================================
;; Helper functions
;; =============================================================================


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn testapi-analysis-> [host port codices corpus]
  (info "testing the API")
  (let [tests (tst/test-> host port codices corpus)]
    (map #(tst/test! %) tests)))
