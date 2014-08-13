(ns protean.transformations.test
  "Generic machinery for testing."
  (:require [clojure.string :as stg]
            [cheshire.core :as jsn]
            [protean.transformation.analysis :as txan]
            [protean.transformation.coerce :as ptc]
            [protean.transformation.payload :as p])
  (:use [taoensso.timbre :as timbre :only [trace debug info warn error]]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- res-location-> [res payload]
  (if-let [loc (get-in res [:headers "Location"])]
    (assoc payload :location loc)
    payload))

(defn- result-> [res]
  (->> {:status (:status res)}
       (p/assoc-item res :body :body)
       (res-location-> res)))


;; =============================================================================
;; Test functions
;; =============================================================================

(defn test! [t]
  (info "executing test : " t)
  (require '[clj-http.client :as client])
  (let [res (eval t)] [(second t) (result-> res)]))
