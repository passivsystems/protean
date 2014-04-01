(ns protean.transformations.api
  "Consumes simulated requests and transforms into simulated responses."
  (:require [clojure.string :as stg]
            [clojure.set :as st]
            [cheshire.core :as jsn]
            [protean.transformations.coerce :as txco]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(def ERRS #{400 405 500 502 503 504})

(def REQ_ERRS #{400 405})

(defn- err-status? [payload] (some #{(:status payload)} ERRS))

(defn- percentage? [x] (if (< (rand-int 100) x) true false))

(defn- mod-1st-hdr [proj-payload errs prob]
  (let [hdrs (:headers (:rsp proj-payload))
        estatus (or (get-in proj-payload [:rsp :errors :status]) errs)
        eprob (or (get-in proj-payload [:rsp :errors :probability]) prob)]
    (if (and estatus (percentage? eprob))
      (let [k (first (keys hdrs))]
        (if k (st/rename-keys hdrs {k (str k "mutated")}) hdrs))
      hdrs)))

(defn- method-2-status-> [method]
  (cond
    (= method :get)  {:status 200}
    (= method :post) {:status 201}
    (= method :put)  {:status 204}
    :else {:status 500}))

(defn- proj-2-status-> [proj-payload payload]
  (if-let [status (:status (:rsp proj-payload))]
    (assoc payload :status status)
    payload))

(defn- err-2-status-> [proj-payload errs prob payload]
  (let [estatus (or (get-in proj-payload [:rsp :errors :status]) errs)
        eprob (or (get-in proj-payload [:rsp :errors :probability]) prob)]
    (if (and (and estatus (percentage? eprob))
           (not (contains? REQ_ERRS (:status payload))))
    (assoc payload :status (rand-nth estatus))
    payload)))

(defn- verify-headers-> [req proj-payload payload]
  (if-let [hdrs (:headers (:req proj-payload))]
    (if (every? (set (keys (:hdrs req))) (map stg/lower-case (keys hdrs)))
      payload
      (assoc payload :status 400))
    payload))

(defn- verify-req-params-> [req proj-payload payload]
  (if-let [rpms (:req-params (:req proj-payload))]
    (if (every? (set (keys (:q-params req))) (keys rpms))
      payload
      (assoc payload :status 400))
    payload))

(defn- verify-form-> [req proj-payload payload]
  (if-let [f-keys (:form (:req proj-payload))]
    (let [req-form-ks (set (keys (:form-params req)))]
      (if (= req-form-ks (set (keys f-keys)))
        payload
        (assoc payload :status 400)))
    payload))

(defn- verify-body-> [req proj-payload payload]
  (if-let [b-keys (:body (:req proj-payload))]
    (let [req-body-ks (set (keys (jsn/parse-string (:body req))))]
      (if (= req-body-ks (set (keys b-keys)))
        payload
        (assoc payload :status 400)))
    payload))

(defn- verify-method-> [req proj-payload payload]
  (if-let [method (:method (:req proj-payload))]
    (if (= (:method req) method)
      payload
      (assoc payload :status 405))
    payload))

(defn- verify-2-status-> [req proj-payload payload]
  (->> (verify-headers-> req proj-payload payload)
       (verify-req-params-> req proj-payload)
       (verify-form-> req proj-payload)
       (verify-body-> req proj-payload)
       (verify-method-> req proj-payload)))

(defn- status-> [req proj-payload errs prob]
  (->> (method-2-status-> (:method req))
       (proj-2-status-> proj-payload)
       (verify-2-status-> req proj-payload)
       (err-2-status-> proj-payload errs prob)))

(defn- headers-> [proj-payload errs prob payload]
  (if-let [hdrs (mod-1st-hdr proj-payload errs prob)]
    (if (err-status? payload) payload (assoc payload :headers hdrs))
    payload))


;; =============================================================================
;; Transformation functions
;; =============================================================================

;; TODO: this is nasty, needs refactoring, no time right now
(defn- body-> [proj-payload payload]
  (when (:time (:rsp proj-payload))
    (Thread/sleep (* (:time (:rsp proj-payload)) 1000)))
  (if (err-status? payload)
    payload
    (if-let [body (:body (:rsp proj-payload))]
      (if-let [ctype (:content-type (:rsp proj-payload))]
        (cond
          (= ctype "text/xml") (assoc payload :content-type "text/xml"
                                      :body (txco/indent-> (txco/xml-> body)))
          (= ctype "text/plain") (assoc payload :content-type "text/plain"
                                      :body body)
          :else (assoc payload :body (txco/js-> body)))
        (assoc payload :body (txco/js-> body)))
      payload)))

(defn api-resp-> [req proj-payload errs prob]
	(->> (status-> req proj-payload errs prob)
       (headers-> proj-payload errs prob)
       (body-> proj-payload)))
