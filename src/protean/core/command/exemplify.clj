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

(defn- holder-swap [k v m]
  (if (p/holder? v)
    (if-let [ev (get-in m [:gen k :examples])] (first ev) v)
    v))

(defn- holders-swap [qp m] (into {} (for [[k v] qp] [k (holder-swap k v m)])))

(defn- encode-swapped-value
  "Encode body items as clojure, they are Json initially."
  [k x]
  (if (= k :body) (c/clj-> x) x))

(defn- swap-placeholders [k [p1 p2 p3 :as payload]]
  (let [m p3]
    (if-let [qp (encode-swapped-value k (k m))]
      (list p1 p2
       (assoc m k (encode-swapped-value k (holders-swap qp m))))
      payload)))

(defn- example [test]
  (->> test
       (swap-placeholders :query-params)))

(defn examples [codices tests] (map #(example %) tests))
