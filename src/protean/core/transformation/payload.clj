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
            [protean.core.transformation.analysis :as a]
            [protean.core.transformation.coerce :as c]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- uri
  "Swap wildcard characters for Protean Separated Value token.
   This is subsequently seeded during an incremental test or API negotiation."
  [s]
  (s/replace s "*" "psv+"))

(defn assoc-item
  "Extracts out-k out of entry and assocs to payload as in-k."
  [entry out-k in-k payload]
  (if-let [v (if (= out-k :query-params)
               (get-in entry [out-k :required])
               (out-k entry))]
    (if (empty? v) payload (assoc payload in-k v))
    payload))

(defn- body [entry payload]
  (if (:body-keys entry)
    (assoc payload :body (c/js-> (:body-keys entry)))
    payload))

(defn- codex-rsp [entry payload] (assoc payload :codex (:codex entry)))

(defn- options [entry payload]
  (assoc payload :options (->> {}
                               (assoc-item entry :headers :headers)
                               (assoc-item entry :query-params :query-params)
                               (assoc-item entry :form-params :form-params)
                               (assoc-item entry :gen :gen)
                               (body entry)
                               (codex-rsp entry))))

(defn- payload [entry] (->> (update-in entry [:uri] uri) (options entry)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn build-payload [host port codices corpus]
  (let [analysed (a/analysis-> host port codices corpus)]
    (map #(payload %) analysed)))
