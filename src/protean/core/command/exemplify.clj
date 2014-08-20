(ns protean.core.command.exemplify
  "Replace plapceholder values with codex provided examples
   for given types.

   This translation task occurs after seeding has completed
   (probably while some nodes are not visitable)."
  (:require [protean.core.transformation.coerce :as c]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(def psv "psv+")

(def psv-exp "psv\\+")

(defn- holder? [v] (.contains v psv))

(defn body-encode
  "Encode body items as clojure they are Json initially."
  [k x]
  (if (= k :body) (c/clj-> x) x))

(defn- query-param [k p] (body-encode k (k p)))

(defn- v-swap [k v m]
  (if (holder? v)
    (if-let [ev (get-in m [:gen k :examples])] (first ev) v)
    v))

(defn- holders-swap [qp m] (into {} (for [[k v] qp] [k (v-swap k v m)])))

(defn- body [k codices payload]
  (let [m (last payload)]
    (if-let [qp (query-param k m)]
      (list
       (first payload)
       (second payload)
       (assoc m k (body-encode k (holders-swap qp m))))
      payload)))

(defn- example [test codices]
  (->> test
       (body :query-params codices)))

(defn examples [codices tests] (map #(example % codices) tests))
