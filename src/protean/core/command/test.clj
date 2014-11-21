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

(defn test! [[action uri tree :as t]]
  (require '[clj-http.client :as client])
;  (println "\ntest! evaluating:")
;  (println "\ntest! action:" action)
;  (println "\ntest! uri:" uri)
;  (println "\ntest! tree:" tree)
  ; TODO if ConnectException - shouldn't fall over - just report test failure
  (let [res (eval t)]
;    (println "\ntest res:" res)
    [action uri (result res) (get-in tree [:codex :ph-swaps])]))
