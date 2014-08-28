(ns protean.core.codex.placeholder
  "Placeholder functionality, swapping codex examples, generating."
  (:require [protean.core.transformation.coerce :as c]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(def psv "psv+")


;; =============================================================================
;; Truthiness functions
;; =============================================================================

(defn holder?
  "Does a simple value contain a placeholder ?"
  [v]
  (.contains v psv))

(defn uri-ns-holder?
  "Does a uri contain a ns prefixed wildcard placeholder ?"
  [v]
  (.contains v (str "/" psv)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn uri-ns-holder
  "Get ns prefixed wildcard portion of uri, E.G. things/psv+."
  [uri]
  (-> uri (.split "/psv\\+") first (.split "/") last (str "/psv+")))

(defn encode-value
  "Encode body items as clojure, they are Json initially."
  [k x]
  (if (= k :body) (c/clj-> x) x))

(defn holders-swap
  "Swap all placeholders with available seed, example or generated substitutes."
  [ph swp-fn m]
  (into {} (for [[k v] ph] [k (swp-fn k v m)])))
