(ns protean.core.command.exemplify
  "Replace plapceholder values with codex provided examples
   for some more complex types.

   N.B. we are more likely to use seed or generated examples
   for simple (language provided) types.

   This translation task occurs after seeding has completed
   (probably while some nodes are not visitable)."
  (:require [protean.core.transformation.coerce :as c]
            [protean.core.codex.placeholder :as p]
            [protean.core.codex.examples :as e]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- swap-placeholders [k p-type [method uri mp :as payload]]
  (let [v (if (= k :query-params) (get-in mp [k p-type]) (k mp))]
    (if-let [ph (p/encode-value k v)]
      (list method uri
        (assoc mp k (p/encode-value k (p/holders-swap ph e/holder-swap mp))))
      payload)))

(defn- example [test p-type]
  (->> test
       (swap-placeholders :query-params p-type)))

(defn examples [codices p-type tests] (map #(example % p-type) tests))
