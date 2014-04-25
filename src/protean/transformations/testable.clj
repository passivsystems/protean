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

;; side effecting
;;;;;;;;;;;;;;;;;

(defn- test! [t]
  (info "executing test : " t)
  (require '[clj-http.client :as client])
  [(second t) (:status (eval t))])

(defn- test-api! [tests body]
  (println "testing API")
  (println "body : " body)
  (map #(test! %) tests))

;; base test analysis
;;
;; satisfies testing codex simulations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;; real API
;;
;; enhances the result of the base test analysis
;; sows in seed data fed in as JSON payload
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- testy-api-> [testy body]
  (println "body : " body)
  testy)

;; configuration etc
;;;;;;;;;;;;;;;;;;;;

(defn- body-item [body item] (if body (or (body item) nil) nil))


;; =============================================================================
;; Transformation functions
;; =============================================================================



(defn testy-analysis->
  "If we have a non nil body param test real API resources - post process test
   analysis stitching in config and seed data from the body, then agggregate
   state while testing, order of tests may be overriden etc."
  [project proj-payload host port body]
  (println "type of body : " (type body))
  (let [h (or (body-item body "host") host)
        p (or (body-item body "port") port)
        analysed (txan/analysis-> project proj-payload h p)
        testy (map #(testy-> %) analysed)
        tests (if body (testy-api-> testy body) testy)
        res (if body (test-api! tests body) (map #(test! %) tests))]
    {:results res}))
