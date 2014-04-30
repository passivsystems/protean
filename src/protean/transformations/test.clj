(ns protean.transformations.test
  "Generic machinery for testing."
  (:require [clojure.string :as stg]
            [cheshire.core :as jsn]
            [protean.transformations.analysis :as txan])
  (:use [taoensso.timbre :as timbre :only [trace debug info warn error]]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- testy-method-> [entry payload]
  (let [method
         (cond
           (= (:method entry) :post) 'client/post
           (= (:method entry) :put) 'client/put
           :else 'client/get)]
    (conj payload method)))

(defn- testy-uri-> [entry payload]
  (conj payload (stg/replace (:uri entry) "*" "1")))

(defn- assoc-tx->
  "Extracts out-k out of entry and assocs to payload as in-k."
  [entry out-k in-k payload]
  (if-let [v (out-k entry)]
    (if (empty? v) payload (assoc payload in-k v))
    payload))

(defn- body-> [entry payload]
  (if (:body-keys entry)
    (assoc payload :body (jsn/generate-string (:body-keys entry)))
    payload))

(defn- testy-map-> [entry payload]
  (conj payload (->> {:throw-exceptions false}
                     (assoc-tx-> entry :headers :headers)
                     (assoc-tx-> entry :req-params :query-params)
                     (assoc-tx-> entry :form-keys :form-params)
                     (body-> entry))))

(defn- testy-> [entry]
  (->> []
       (testy-method-> entry)
       (testy-uri-> entry)
       (testy-map-> entry)
       seq))

(defn- res-location-> [res payload]
  (if-let [loc (get-in res [:headers "Location"])]
    (assoc payload :location loc)
    payload))

(defn- result-> [res]
  (->> {:status (:status res)}
       (assoc-tx-> res :body :body)
       (res-location-> res)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn test-> [host port codices corpus]
  (let [analysed (txan/analysis-> host port codices corpus)]
    (map #(testy-> %) analysed)))


;; =============================================================================
;; Test functions
;; =============================================================================

(defn test! [t]
  (info "executing test : " t)
  (require '[clj-http.client :as client])
  (let [res (eval t)] [(second t) (result-> res)]))
