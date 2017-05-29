(ns protean.core.command.test
  "Launch tests and do some basic formatting of results.

   N.B. currently locked in with clj-http."
  (:require [clj-http.client :as clt]))

(defn test! [request]
  (let [the-request (assoc request
                      :url (:uri request)
                      :throw-exceptions false
                      :follow-redirects false
                      :redirect-strategy :none)
        response (try
          (clt/request the-request)
          (catch Exception e {:error (.getMessage e)}))]
    [request response]))
