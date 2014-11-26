(ns protean.core.command.test
  "Launch tests and do some basic formatting of results.

   N.B. currently locked in with clj-http."
  (:require [clj-http.client :as clt]))

(defn test! [request]
  (let [the-request (assoc request
                      :url (:uri request)
                      :throw-exceptions false)
        response (clt/request the-request)]
;    (println "test! evaluating:" the-request)
    ; TODO if ConnectException - shouldn't fall over - just report test failure
;    (println "response" response)
    [request response]))

