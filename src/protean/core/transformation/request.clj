(ns protean.core.transformation.request
  "Building Ring requests."
  (:require [clojure.string :as s]
    [protean.core.codex.document :as d]
    [protean.core.protocol.http :as h]
    [protean.core.protocol.protean :as pp]
    [protean.core.transformation.coerce :as co]
    [protean.core.transformation.paths :as p]))

(defn- copy-> [payload kvs target-keys]
  (if kvs
    (update-in payload target-keys #(merge kvs %))
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

(defn- copy-optional-params-> [payload tree include-optional]
  (if include-optional
    (-> payload
      (copy-> (d/get-in-tree tree [:req :query-params :optional]) [:query-params])
      (copy-> (d/get-in-tree tree [:req :form-params :optional]) [:form-params]))
    payload))

(defn prepare-request
  "Prepare payload - may still contain placeholders."
  [method uri tree include-optional]
  (-> {:method method :uri uri}
    (copy-> (d/get-in-tree tree [:req :query-params :required]) [:query-params])
    (copy-> (d/get-in-tree tree [:req :form-params :required]) [:form-params])
    (copy-optional-params-> tree include-optional)
    (copy-> (d/req-hdrs tree) [:headers])
    (transform-query-params-> tree)
    (content-> tree)))

(defn- prepare-request2
  [method uri tree {:keys [without-qps without-fps without-hdrs]}]
  (-> {:method method :uri uri}
    (copy-> (dissoc (d/get-in-tree tree [:req :query-params :required]) without-qps) [:query-params])
    (copy-> (dissoc (d/get-in-tree tree [:req :form-params :required]) without-fps) [:form-params])
    (copy-> (dissoc (d/req-hdrs tree) without-hdrs) [:headers])
    (transform-query-params-> tree)
    (content-> tree)))

(defn- remove-qp [method uri tree]
  (for [missing-qp (keys (d/get-in-tree tree [:req :query-params :required]))]
    [(str "missing required query-param: " missing-qp) (prepare-request2 method uri tree {:without-qps missing-qp})]))

(defn- remove-fp [method uri tree]
  (for [missing-fp (keys (d/get-in-tree tree [:req :form-params :required]))]
    [(str "missing required form-param: " missing-fp) (prepare-request2 method uri tree {:without-qfs missing-fp})]))

; TODO also include missing headers, and missing json/xml fields in body
(defn prepare-invalid-requests
  "Create cartesian product of invalid parameters/headers."
  [method uri tree]
  (concat
    (remove-qp method uri tree)
    (remove-fp method uri tree)))
