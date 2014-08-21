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

(defn body-encode
  "Encode body items as clojure they are Json initially."
  [k x]
  (if (= k :body) (c/clj-> x) x))

(defn- body [k codices payload]
  (let [m (last payload)]
    (if-let [qp (body-encode k (k m))]
      (list
       (first payload)
       (second payload)
       (assoc m k (body-encode k (p/holders-swap qp m))))
      payload)))

(defn- example [test codices]
  (->> test
       (body :query-params codices)))

(defn examples [codices tests] (map #(example % codices) tests))
