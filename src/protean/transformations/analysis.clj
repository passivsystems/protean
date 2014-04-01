(ns protean.transformations.analysis
  "Creates a datastructure which can be used in subsequent pipelines.
   Lowest common denomiator language describing a specification for a
   request/response."
  (:require [clojure.string :as stg]
            [clojure.set :as st]
            [clojure.data.xml :as xml]
            [ring.util.codec :as cod])
  (:import java.net.InetAddress))

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

(defn uri-> [project path port payload]
  (let [uri (str "http://"
                 (.getCanonicalHostName (InetAddress/getLocalHost))
                 ":" port "/" project "/" (key path))]
    (assoc payload :uri uri)))

(defn analyse-> [project path port]
  (->> (method-> path)
       (assoc-tx-> path :headers :headers)
       (assoc-tx-> path :form :form-keys)
       (assoc-tx-> path :body :body-keys)
       (uri-> project path port)
       (assoc-tx-> path :req-params :req-params)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn analysis-> [project proj-payload port]
  (let [paths (:paths proj-payload)]
    (map #(analyse-> project % port) paths)))
