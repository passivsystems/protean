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
            [protean.core.transformation.analysis :as a]
            [protean.core.transformation.coerce :as c]
            [protean.core.codex.document :as d]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- uri
  "Swap wildcard characters for Protean Separated Value token.
   This is subsequently seeded during an incremental test or API negotiation."
  [s]
  (s/replace s "*" "psv+"))

(defn- body [entry payload]
  (if (:body-keys entry)
    (assoc payload :body (c/js (:body-keys entry)))
    payload))

;; add json ctype to request headers if there is no ctype and we are post or put
(defn- postprocess [entry payload]
  (if (and (some #{(:method entry)} [:post :put])
           (not (get-in payload [:headers "Content-Type"])))
    (assoc-in payload [:headers h/ctype]  h/jsn-simple )
    payload))

(defn- options [{:keys [tree] :as entry} corpus payload]
  (assoc payload :options
         (->> {}
              (d/assoc-tree-item-> tree [:req :headers] [:headers])
              (d/assoc-tree-item-> tree [:req :query-params :required] [:query-params])
              (d/assoc-tree-item-> tree [:req :query-params :optional] [:query-params]) ; TODO only include when (corpus) test level is 2?
              (d/assoc-tree-item-> tree [:req :form-params] [:form-params])
              (d/assoc-tree-item-> tree [:req :vars] [:vars])
              (body entry)
              (d/assoc-item-> entry [:codex] [:codex])
              (postprocess entry))))

(defn- payload [entry corpus]
  (->> (update-in entry [:uri] uri)
       (options entry corpus)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn build-payload [host port codices corpus]
  (let [analysed (a/analysis-> host port codices corpus)]
    (map #(payload % corpus) analysed)))
