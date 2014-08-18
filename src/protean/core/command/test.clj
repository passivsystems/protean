(ns protean.core.command.test
  (:require [protean.core.transformation.payload :as p]))

(defn- res-location [res payload]
  (if-let [loc (get-in res [:headers "Location"])]
    (assoc payload :location loc)
    payload))

(defn- result [res]
  (->> {:status (:status res)}
       (p/assoc-item res :body :body)
       (res-location res)))

(defn test! [t]
  (require '[clj-http.client :as client])
  (let [res (eval t)] [(second t) (result res)]))
