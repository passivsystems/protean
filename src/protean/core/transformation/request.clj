(ns protean.core.transformation.request
  "Building Ring requests."
  (:require [protean.core.codex.document :as d]
    [protean.core.protocol.http :as h]
    [protean.core.protocol.protean :as pp]
    [protean.core.transformation.coerce :as co]
    [protean.core.transformation.paths :as p]))

(defn- copy-> [payload tree source-keys target-keys]
  (if-let [kvs (d/get-in-tree tree source-keys)]
    (assoc-in payload target-keys kvs)
    payload))

  (defn- content-type-> [payload]
    (if (and (:body payload)
            (not (pp/ctype payload)))
      (assoc-in payload [:headers h/ctype] h/jsn-simple)
      payload))

(defn- content-> [payload]
  (let [ctype (pp/ctype payload)
        f (cond
          (h/txt? ctype) identity
          (h/xml? ctype) co/xml
          :else co/js)]
    (update-in payload [:body] f)))

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
    ; TODO only include when (corpus) test level is 2?
    (copy-> tree [:req :query-params :required] [:query-params])
    (copy-> tree [:req :query-params :optional] [:query-params])
    (copy-> tree [:req :form-params :required] [:form-params])
    (copy-> tree [:req :form-params :optional] [:form-params])
    (copy-> tree [:req :headers] [:headers])
    (copy-> tree [:req :body] [:body])
    (transform-query-params-> tree)
    (content-type->)
    (content->)))
