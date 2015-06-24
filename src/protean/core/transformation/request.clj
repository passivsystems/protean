(ns protean.core.transformation.request
  "Building Ring requests."
  (:require [clojure.string :as s]
    [protean.core.codex.document :as d]
    [protean.core.protocol.http :as h]
    [protean.core.protocol.protean :as pp]
    [protean.core.transformation.coerce :as co]
    [protean.core.transformation.paths :as p]
    [protean.core.generation.json :as gen-jsn]
    [cheshire.core :as cc]))

(defn- copy-> [payload kvs target-keys]
  (if kvs
    (update-in payload target-keys #(merge kvs %))
    payload))

(defn- content-> [payload tree gen-from-schema]
  (let [example (first (d/get-in-tree tree [:req :body-example]))
        schema (d/get-in-tree tree [:req :body-schema])
        body (d/get-in-tree tree [:req :body])
        ctype (pp/ctype payload)
        f (cond
            (h/txt? ctype) identity
            (h/xml? ctype) co/xml
            :else co/js)
        body-val (cond
          (and schema gen-from-schema) (gen-jsn/gen (d/to-path schema tree))
          example (-> example (d/to-path tree) slurp s/trim)
          :else (f body))]
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
  [method uri tree & {:keys [include-optional gen-from-schema] :or {include-optional false gen-from-schema false}}]
  (-> {:method method :uri uri}
    (copy-> (d/get-in-tree tree [:req :query-params :required]) [:query-params])
    (copy-> (d/get-in-tree tree [:req :form-params :required]) [:form-params])
    (copy-optional-params-> tree include-optional)
    (copy-> (d/req-hdrs tree) [:headers])
    (transform-query-params-> tree)
    (content-> tree gen-from-schema)))

(defn- missing-qps [request tree]
  (for [required-qp (keys (d/get-in-tree tree [:req :query-params :required]))]
    [(str "missing required query-param: " required-qp)
     (update-in request [:query-params] dissoc required-qp)]))

(defn- missing-fps [request tree]
  (for [required-fp (keys (d/get-in-tree tree [:req :form-params :required]))]
    [(str "missing required form-param: " required-fp)
     (update-in request [:form-params] dissoc required-fp)]))

(defn- missing-json-fields [request tree]
  (let [remove-json-field (fn [body field]
          (-> body cc/parse-string (dissoc field) cc/generate-string))]
    (if-let [schema (d/get-in-tree tree [:req :body-schema])]
      ; TODO this should work for required at all levels - here just top level
      (for [required-field (:required (cc/parse-string (slurp (d/to-path schema tree)) true))]
        [(str "missing required json field: " required-field)
          (update-in request [:body] remove-json-field required-field)]))))

; TODO also include missing headers, and missing xml fields in body
(defn prepare-invalid-requests
  "Create requests with one invalid form or query parameter."
  [method uri tree & {:keys [include-optional gen-from-schema] :or {include-optional false gen-from-schema false}}]
  (let [request (prepare-request method uri tree include-optional gen-from-schema)]
    (concat
      (missing-qps request tree)
      (missing-fps request tree)
      (missing-json-fields request tree))))
