(ns protean.transformations.testsim
  "Uses output from the analysis transformations to generate a
   datastructure which can drive automated testing."
  (:require [protean.transformations.test :as tst])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)]))

;; =============================================================================
;; Helper functions
;; =============================================================================


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn testsim-analysis-> [host port codices corpus]
  (info "testing the SIM")
  (let [tests (tst/test-> host port codices corpus)]
    (map #(tst/test! %) tests)))
