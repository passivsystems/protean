(ns protean.core.transformation.analysis
  "Accepts codices and a corpus of information describing what to do.
   Creates a datastructure which can be used in subsequent pipelines.
   Lowest common denomiator language describing a specification for a
   request/response.

   Codices here may be the entire body of codices Protean includes.

   The corpus may then contain instructions including the locations 'locs'
   in the API surface area to range over in order to build the analysis.

   If no range is specified every resource under every service Protean knows
   about will be included in the analysis.

   If the corpus includes a 'locs' specification 'sample get/simple' an
   analysis will be generated for one 'sample' service resource like:

     {
       :codex {:body-res nil :success-code nil :content-type nil}
       :doc 'Simplext example of a resource - doc is optional'
       :uri 'http://localhost:3000/sample/get/simple'
       :method :get
     }
  "
  (:require [protean.core.transformation.paths :as p]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn method-> [resource]
  (if-let [method (:method resource)]
    {:method method}
    {:method :get}))

(defn assoc-tx->
  "Extracts out-k out of path and assocs to payload as in-k."
  [resource out-k in-k payload]
  (if-let [ext-out (out-k (:req (:spec resource)))]
    (assoc payload in-k ext-out)
    payload))

(defn doc-> [resource payload]
  (if-let [doc (:doc (:spec resource))]
    (assoc payload :doc doc)
    payload))

(defn desc-> [resource payload]
  (if-let [desc (:description (:spec resource))]
    (assoc payload :desc desc)
    payload))

(defn uri-> [{:keys [svc path]} host port payload]
  (let [uri (str "http://" host ":" port "/" (name svc) "/" path)]
    (assoc payload :uri uri)))

(defn codex-rsp-> [resource payload]
  (println "resource : " resource)
  (assoc payload :codex
    {:q-params-type (get-in resource [:spec :req :query-params-type])
     :body (get-in resource [:spec :rsp :body])
     :rsp (get-in resource [:spec :rsp])
     :body-res (get-in resource [:spec :rsp :body-res])
     :success-code (get-in resource [:spec :rsp :status])
     :errors (get-in resource [:spec :rsp :errors :status])
     :content-type-req (get-in resource [:spec :req :headers "Content-Type"])
     :content-type (get-in resource [:spec :rsp :headers "Content-Type"])
     :headers (get-in resource [:spec :rsp :headers])}))

(defn analyse-> [resource host port]
  (->> (method-> resource)
       (assoc-tx-> resource :headers :headers)
       (assoc-tx-> resource :form-params :form-params)
       (assoc-tx-> resource :body :body-keys)
       (assoc-tx-> resource :format :format)
       (uri-> resource host port)
       (assoc-tx-> resource :query-params :query-params)
       (doc-> resource)
       (desc-> resource)
       (codex-rsp-> resource)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn analysis-> [host port codices corpus]
  (let [p (p/paths-> codices (:locs corpus))]
    (map #(analyse-> % host port) p)))
