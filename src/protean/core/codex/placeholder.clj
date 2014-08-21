(ns protean.core.codex.placeholder
  "Placeholder functionality, swapping codex examples, generating")

;; =============================================================================
;; Helper functions
;; =============================================================================

(def psv "psv+")


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn holder?
  "Does a simple value contain a placeholder ?"
  [v]
  (.contains v psv))

(defn uri-ns-holder?
  "Does a uri contain a ns prefixed wildcard placeholder ?"
  [v]
  (.contains v (str "/" psv)))

(defn uri-ns-holder
  "Get ns prefixed wildcard portion of uri, E.G. things/psv+."
  [uri]
  (-> uri (.split "/psv\\+") first (.split "/") last (str "/psv+")))
