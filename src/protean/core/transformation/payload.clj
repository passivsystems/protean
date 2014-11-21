(ns protean.core.transformation.payload
  "Transforms datastructures from analysis to API call payload.
   Deliberately protocol, concurrency mechanism and client agnostic.  This base
   payload format is incidentally compatible with two potential client
   libraries.

   Produces a map containing :method, :uri and :options for the API call and
   some residual codex information for results interpretation in :codex.

     {
       :method :get
       :uri 'http://localhost:3000/sample/get/simple'
       :options {:headers {'Authorization' 'token'}}
       :codex {:body-res nil :success-code nil :content-type nil}
     }
  "
  (:require [clojure.string :as s]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.coerce :as c]
            [protean.core.codex.document :as d]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- body [tree payload]
  (if-let [b (d/get-in-tree tree [:req :body])]
    (assoc payload :body (c/js b))
    payload))

;; add json ctype to request headers if there is no ctype and we are post or put
(defn- postprocess [entry payload]
  (if (and (some #{(:method entry)} [:post :put])
           (not (get-in payload [:headers "Content-Type"])))
    (assoc-in payload [:headers h/ctype]  h/jsn-simple )
    payload))

(defn- options [{:keys [tree] :as entry} corpus payload]
  (def x (assoc payload :options
    (->> {}
      (d/assoc-tree-item-> tree [:req :headers] [:headers])
      (d/assoc-tree-item-> tree [:req :query-params :required] [:query-params])
      (d/assoc-tree-item-> tree [:req :query-params :optional] [:query-params]) ; TODO only include when (corpus) test level is 2?
      (d/assoc-tree-item-> tree [:req :form-params] [:form-params])
      (body tree)
      (d/assoc-item-> entry [:codex] [:codex])
      (postprocess entry))))
;    (println "x:" x)
    x)

(defn- payload [{:keys [tree] :as entry} corpus]
  (->> entry
    (options entry corpus)))

;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn build-payload [corpus analysed]
  (map #(payload % corpus) analysed))
