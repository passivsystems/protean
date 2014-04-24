(ns protean.transformations.analysis
  "Creates a datastructure which can be used in subsequent pipelines.
   Lowest common denomiator language describing a specification for a
   request/response."
  (:require [clojure.string :as stg]
            [clojure.set :as st]
            [clojure.data.xml :as xml]
            [ring.util.codec :as cod]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn method-> [path]
  (if-let [method (:method (:req (val path)))]
    {:method method}
    {:method :get}))

(defn assoc-tx->
  "Extracts out-k out of path and assocs to payload as in-k."
  [path out-k in-k payload]
  (if-let [ext-out (out-k (:req (val path)))]
    (assoc payload in-k ext-out)
    payload))

(defn doc-> [path payload]
  (if-let [doc (:doc (val path))]
    (assoc payload :doc doc)
    payload))

(defn uri-> [project path host port payload]
  (let [uri (str "http://" host ":" port "/" project "/" (key path))]
    (assoc payload :uri uri)))

(defn analyse-> [project path host port]
  (->> (method-> path)
       (assoc-tx-> path :headers :headers)
       (assoc-tx-> path :form :form-keys)
       (assoc-tx-> path :body :body-keys)
       (uri-> project path host port)
       (assoc-tx-> path :req-params :req-params)
       (doc-> path)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn analysis-> [project proj-payload host port]
  (let [paths (:paths proj-payload)]
    (map #(analyse-> project % host port) paths)))
