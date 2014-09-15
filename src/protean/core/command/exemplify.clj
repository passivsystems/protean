(ns protean.core.command.exemplify
  "Replace plapceholder values with codex provided examples
   for some more complex types.

   N.B. we are more likely to use seed or generated examples
   for simple (language provided) types.

   This translation task occurs after seeding has completed
   (probably while some nodes are not visitable)."
  (:require [protean.core.transformation.coerce :as c]
            [protean.core.codex.placeholder :as p]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- tx-payload-map [k mp res]
  (-> mp
      (assoc k (p/encode-value k (first res)))
      (update-in [:codex :ph-swaps] conj (second res))
      (update-in [:codex :ph-swaps] vec)))

(defn- swap-placeholders [k p-type [method uri mp :as payload]]
  (let [v (if (= k :query-params) (get-in mp [k p-type]) (k mp))]
    (if-let [phs (p/encode-value k v)]
      (let [res (p/holders-swap phs p/holder-swap-exp mp k :exp)]
        (list method uri (tx-payload-map k mp res)))
      payload)))

(defn- example [test p-type]
  (->> test
       (swap-placeholders :query-params p-type)
       (swap-placeholders :body p-type)))

(defn examples [codices p-type tests]
  (map #(example % p-type) tests))