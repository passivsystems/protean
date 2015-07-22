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
  (if (not (empty? kvs))
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

(defn prepare-request
  "Prepare payload - may still contain placeholders."
  [method uri tree & {:keys [include-optional gen-from-schema] :or {include-optional false gen-from-schema false}}]
  (-> {:method method :uri uri}
    (copy-> (d/qps tree include-optional) [:query-params])
    (copy-> (d/fps tree include-optional) [:form-params])
    (copy-> (d/req-hdrs tree) [:headers])
    (content-> tree gen-from-schema)))

(defn- missing-qps [request tree]
  (for [required-qp (keys (d/qps tree false))]
    [(str "missing required query-param: " required-qp)
     (update-in request [:query-params] dissoc required-qp)]))

(defn- missing-fps [request tree]
  (for [required-fp (keys (d/qps tree false))]
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
