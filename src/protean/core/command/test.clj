(ns protean.core.command.test
  "Launch tests and do some basic formatting of results.

   N.B. currently locked in with clj-http."
  (:require [clj-http.client :as clt]))

;(defn- res-location [res payload]
;  (if-let [loc (get-in res [:headers "Location"])]
;    (assoc payload :location loc)
;    payload))

;(defn- body-> [res payload]
;  (if-let [v (:body-example res)]
;    (if (empty? v) payload (assoc payload :body v))
;    payload))

;(defn- result [res]
;  (->> {:status (:status res)}
;       (body-> res)
;       (res-location res)))


(defn test! [request]
;  (println "test! evaluating:" request)
  (let [the-request (assoc request
                      :url (:uri request)
                      :throw-exceptions false)
        response (clt/request the-request)]
    ; TODO if ConnectException - shouldn't fall over - just report test failure
;    (println "response" response)
    [request response]))


 ; (let [res (eval t)]
;    (println "\ntest res:" res)
;    (def res [(action method) uri (result res) (get-in tree [:codex :ph-swaps])])
;    (println "res" res)
;    res))
