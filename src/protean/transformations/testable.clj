(ns protean.transformations.testable
  "Uses output from the analysis transformations to generate a
   datastructure which can drive automated testing."
  (:require [clojure.string :as stg]
            [clojure.set :as st]
            [clojure.data.xml :as xml]
            [ring.util.codec :as cod]
            [cheshire.core :as jsn]
            [protean.transformations.analysis :as txan]
            [clj-http.client :as client])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.net.InetAddress))

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
    (assoc payload in-k v)
    payload))

(defn- body-> [entry payload]
  (if (:body-keys entry)
    (assoc payload :body (jsn/generate-string (:body-keys entry)))
    payload))

(defn- testy-map-> [entry payload]
  (conj payload (->> {}
                     (assoc-tx-> entry :headers :headers)
                     (assoc-tx-> entry :req-params :query-params)
                     (assoc-tx-> entry :form-keys :form-params)
                     (body-> entry))))

(defn testy-> [entry]
  (->> []
       (testy-method-> entry)
       (testy-uri-> entry)
       (testy-map-> entry)
       seq))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn testy-analysis-> [project proj-payload port]
  (let [paths (:paths proj-payload)]
    (let [analysed (txan/analysis-> project proj-payload port)]
      (let [testy (map #(testy-> %) analysed)]
        (doseq [t testy]
          (info "executing test : " t)
          (require '[clj-http.client :as client])
          (eval t)))
      {:status "passed"})))
