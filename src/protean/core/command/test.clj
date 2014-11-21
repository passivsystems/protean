(ns protean.core.command.test
  "Launch tests and do some basic formatting of results.

   N.B. currently locked in with clj-http.")

(defn- res-location [res payload]
  (if-let [loc (get-in res [:headers "Location"])]
    (assoc payload :location loc)
    payload))


(defn- body-> [res payload]
  (if-let [v (:body-example res)]
    (if (empty? v) payload (assoc payload :body v))
    payload))

(defn- result [res]
  (->> {:status (:status res)}
       (body-> res)
       (res-location res)))

(defn test! [[t1 t2 t3 :as t]]
  (require '[clj-http.client :as client])
  (let [res (eval t)]
    [t1 t2 (result res) (get-in t3 [:codex :ph-swaps])]))
