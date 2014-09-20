(ns protean.core.transformation.sim
  (:require [clojure.string :as stg]
            [clojure.set :as st]
            [cheshire.core :as jsn]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.coerce :as txco]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- err-status? [payload] (some #{(:status payload)} h/errs))

(defn- req-err-status? [payload] (some #{(:status payload)} h/req-errs))

(defn- percentage? [x] (if (< (rand-int 100) x) true false))

(defn- partial-path? [key path]
  (let [split-path (set (stg/split path #"/"))]
    (>= (count (st/intersection (set (stg/split key #"/")) split-path))
       (dec (count split-path)))))

(defn- wild-path? [k paths]
  (let [candidates (filter #(partial-path? k %) paths)]
    (if-let [x (filter #(= (count (stg/split k #"/"))
                           (count (stg/split % #"/"))) candidates)]
      (first x)
      nil)))

(defn- service-path? [codices proj k]
  (or (get-in codices [proj :paths k])
      (get-in codices [proj :paths (wild-path? k
    (filter #(.contains % "*") (keys (get-in codices [proj :paths]))))])))

(defn req-> [{:keys [request-method headers query-params form-params body]}]
  {:method request-method :hdrs headers :q-params query-params
   :form-params form-params :body (slurp body)})

(defn- mod-1st-hdr
  "If a based on a probability defined in the codex optionally mutate the first
   response header."
  [{:keys [rsp] :as proj-payload} errs prob]
  (let [hdrs (:headers rsp)
        estatus (or (get-in rsp [:errors :status]) errs)
        eprob (or (get-in rsp [:errors :probability]) prob)]
    (if (and estatus (percentage? eprob))
      (let [k (first (keys hdrs))]
        (if k (st/rename-keys hdrs {k (str k "mutated")}) hdrs))
      hdrs)))

(defn- proj-2-status [{:keys [rsp] :as proj-payload} payload]
  (if-let [status (:status rsp)]
    (assoc payload :status status)
    payload))

(defn- err-2-status [{:keys [rsp] :as proj-payload} proj-errs prob payload]
  (let [estatus (or (get-in rsp [:errors :status]) proj-errs)
        eprob (or (get-in rsp [:errors :probability]) prob)]
    (if (and (and estatus (percentage? eprob)) (not (req-err-status? payload)))
      (assoc payload :status (rand-nth estatus))
      payload)))

(defn- verify-headers [req proj-payload payload]
  (if-let [hdrs (:headers (:req proj-payload))]
    (if (every? (set (keys (:hdrs req))) (map stg/lower-case (keys hdrs)))
      payload
      (assoc payload :status 400))
    payload))

(defn- verify-query-params [req proj-payload payload]
  (if-let [rpms (get-in proj-payload [:req :query-params :required])]
    (if (every? (set (keys (:q-params req))) (keys rpms))
      payload
      (assoc payload :status 400))
    payload))

(defn- verify-form [req proj-payload payload]
  (if-let [f-keys (:form-params (:req proj-payload))]
    (let [req-form-ks (set (keys (:form-params req)))]
      (if (= req-form-ks (set (keys f-keys)))
        payload
        (assoc payload :status 400)))
    payload))

(defn- verify-body [req proj-payload payload]
  (if-let [b-keys (:body (:req proj-payload))]
    (let [req-body-ks (set (keys (jsn/parse-string (:body req))))]
      (if (= req-body-ks (set (keys b-keys)))
        payload
        (assoc payload :status 400)))
    payload))

(defn- verify-2-status [req proj-payload payload]
  (->> (verify-headers req proj-payload payload)
       (verify-query-params req proj-payload)
       (verify-form req proj-payload)
       (verify-body req proj-payload)))

(defn- status [req proj-payload proj-errs prob]
  (->> (h/status (:method req))
       (proj-2-status proj-payload)
       (verify-2-status req proj-payload)
       (err-2-status proj-payload proj-errs prob)))

(defn- headers [proj-payload proj-errs prob payload]
  (if-let [hdrs (mod-1st-hdr proj-payload proj-errs prob)]
    (if (err-status? payload) payload (assoc payload :headers hdrs))
    payload))

(defn- rsp [payload header body]
  (assoc payload :headers {"Content-Type" header} :body body))

;; TODO: this is nasty, needs refactoring, no time right now
(defn- body [proj-payload payload]
  (when (:time (:rsp proj-payload))
    (Thread/sleep (* (:time (:rsp proj-payload)) 1000)))
  (if (err-status? payload)
    payload
    (if-let [body (:body (:rsp proj-payload))]
      (if-let [ctype (get-in proj-payload [:rsp :headers "Content-Type"])]
        (cond
          (= ctype h/xml) (rsp payload ctype (txco/pretty-xml-> body))
          (= ctype h/txt) (rsp payload ctype body)
          :else (rsp payload h/jsn (txco/pretty-js-> body)))
        (rsp payload h/jsn (txco/pretty-js-> body)))
      payload)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn sim-rsp-> [{:keys [uri] :as req} codices]
  (let [proj (keyword (second (stg/split uri #"/")))
        k (second (stg/split uri (re-pattern (str "/" (name proj) "/"))))
        proj-errors (get (get-in codices [proj :errors]) :status)
        prob (or (get (get-in codices [proj :errors]) :probability) 0)
        req (req-> req)]
    (if-let [proj-payload (service-path? codices proj k)]
      (if ((:method req) proj-payload)
        (->> (status req ((:method req) proj-payload) proj-errors prob)
             (headers ((:method req) proj-payload) proj-errors prob)
             (body ((:method req) proj-payload)))
        {:status 405})
      {:status 404})))
