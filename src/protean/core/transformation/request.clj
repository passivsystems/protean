(ns protean.core.transformation.request
  "Building Ring requests."
  (:require [clojure.string :as s]
    [protean.core.codex.document :as d]
    [protean.core.protocol.http :as h]
    [protean.core.protocol.protean :as pp]
    [protean.core.transformation.coerce :as co]
    [protean.core.transformation.paths :as p]))

(defn- copy-> [payload tree source-keys target-keys]
  (if-let [kvs (d/get-in-tree tree source-keys)]
    (update-in payload target-keys #(merge kvs %))
    payload))

(defn- headers-> [payload tree]
  (if-let [headers (d/req-hdrs tree)]
    (assoc-in payload [:headers] headers)
    payload))

(defn- content-> [payload tree]
  (let [example (d/get-in-tree tree [:req :body-example])
        body (d/get-in-tree tree [:req :body])
        ctype (pp/ctype payload)
        f (cond
          (h/txt? ctype) identity
          (h/xml? ctype) co/xml
          :else co/js)
        body-val (if example
          (-> (first example) (d/to-path tree) slurp s/trim)
          (f body))]
    (assoc-in payload [:body] body-val)))

(defn- transform-query-params-> [payload tree]
  (if (d/qp-json? tree)
    (let [to-json (fn [[k v]] [k (co/js v)])
          qp-to-json (fn [m] (into {} (map to-json m)))]
      (update-in payload [:query-params] qp-to-json))
    payload))

(defn prepare-request
  "Prepare payload - may still contain placeholders."
  [method uri tree]
  (-> {:method method :uri uri}
    (copy-> tree [:req :query-params :required] [:query-params])
    (copy-> tree [:req :form-params :required] [:form-params])
    ; TODO activate optional when (corpus) test level is 2
    ;(copy-> tree [:req :query-params :optional] [:query-params])
    ;(copy-> tree [:req :form-params :optional] [:form-params])
    (headers-> tree)
    (transform-query-params-> tree)
    (content-> tree)))
