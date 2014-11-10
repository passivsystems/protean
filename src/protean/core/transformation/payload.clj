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

(defn assoc-item
  "Extracts first out-k in tree and assocs to payload as in-k."
  [tree out-ks in-ks corpus payload]
  (if-let [v (d/get-in-tree tree out-ks)]
    (if (empty? v) payload (assoc-in payload in-ks v))
    payload))

(defn- body [entry payload]
  (if (:body-keys entry)
    (assoc payload :body (c/js (:body-keys entry)))
    payload))

(defn- codex-rsp [entry payload] (assoc payload :codex (:codex entry)))

;; add json ctype to request headers if there is no ctype and we are post or put
(defn- postprocess [entry payload]
  (if (and (some #{(:method entry)} [:post :put])
           (not (get-in payload [:headers "Content-Type"])))
    (assoc-in payload [:headers h/ctype]  h/jsn-simple )
    payload))

(defn- options [{:keys [tree] :as entry} corpus payload]
  (assoc payload :options
         (->> {}
              (assoc-item tree [:req :headers] [:headers] corpus)
              (assoc-item tree [:req :query-params :required] [:query-params] corpus)
              (assoc-item tree [:req :query-params :optional] [:query-params] corpus) ; TODO only include when test level is 2?
              (assoc-item tree [:req :form-params] [:form-params] corpus)
              (assoc-item tree [:req :vars] [:vars] corpus)
              (body entry)
              (codex-rsp entry)
              (postprocess entry))))

(defn- payload [entry corpus]
  (->> (update-in entry [:uri] uri) (options entry corpus)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn build-payload [host port codices corpus]
  (let [analysed (a/analysis-> host port codices corpus)]
    (map #(payload % corpus) analysed)))
