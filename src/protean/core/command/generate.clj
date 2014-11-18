(ns protean.core.command.generate
  "Generate values for placeholders, only when integration testing."
  (:refer-clojure :exclude [long int])
  (:require [protean.core.transformation.coerce :as c]
            [protean.core.codex.placeholder :as ph]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- track-payload-swap [k mp res]
  (-> mp
      (assoc k (first res))
      (update-in [:codex :ph-swaps] conj (second res))
      (update-in [:codex :ph-swaps] vec)))

(defn- swap-placeholders [k tree [method uri mp :as payload]]
  (if-let [phs (ph/encode-value k (k mp))]
    (let [swapped (ph/holders-swap phs ph/holder-swap-gen k :vars tree)
          svs (if (= k :body) (c/js (first swapped)) (first swapped))
          res [svs (last swapped)]]
      (list method uri (track-payload-swap k mp res)))
    payload))

(defn- uri [codices tree payload]
  (let [uri (second payload)]
    (if-let [v (ph/uri-ns-holder uri)]
      (ph/holder-swap-uri v payload tree)
    payload)))

(defn- generate [[_ _ {tree :tree} :as test] codices]
  (->> test
       (swap-placeholders :query-params tree)
       (swap-placeholders :body tree)
       (uri codices tree)))

(defn generations [codices tests]
  (map #(generate % codices) tests))
