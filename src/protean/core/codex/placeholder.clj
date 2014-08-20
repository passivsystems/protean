(ns protean.core.codex.placeholder
  "Placeholder functionality, swapping codex examples, generating")

;; =============================================================================
;; Helper functions
;; =============================================================================

(def psv "psv+")

(defn- holder? [v] (.contains v psv))

(defn v-swap [k v m]
  (if (holder? v)
    (if-let [ev (get-in m [:gen k :examples])] (first ev) v)
    v))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn holders-swap [qp m] (into {} (for [[k v] qp] [k (v-swap k v m)])))
