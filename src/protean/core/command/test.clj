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

(defn test! [[t1 t2 t3 :as t]]
  (require '[clj-http.client :as client])
  (let [res (eval t)]
    [t1 t2 (result res) (get-in t3 [:codex :ph-swaps])]))
