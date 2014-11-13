(ns protean.core.transformation.sim
  (:require [clojure.string :as s]
            [clojure.set :as st]
            [clojure.pprint]
            [clojure.main :as m]
            [clojure.xml :as x]
            [clojure.zip :as z]
            [cheshire.core :as jsn]
            [protean.core.protocol.http :as h]
            [protean.core.protocol.protean :as p]
            [protean.core.codex.document :as d]
            [protean.core.transformation.coerce :as c])
  (:import java.io.ByteArrayInputStream))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- err-status?
  "Is payload status a codex error status code ?"
  [{:keys [status]}] (not (h/success? status)))

(defn- client-err-status?
  "Is payload status a codex client error status code ? "
  [{:keys [status]}] (h/client-err? status))

(defn- percentage? [x] (if (< (rand-int 100) x) true false))

(defn- partial-path? [key path]
  (let [split-path (set (s/split path #"/"))]
    (>= (count (st/intersection (set (s/split key #"/")) split-path))
       (dec (count split-path)))))

(defn- wild-path? [k paths]
  (let [candidates (filter #(partial-path? k %) paths)]
    (if-let [x (filter #(= (count (s/split k #"/"))
                           (count (s/split % #"/"))) candidates)]
      (first x)
      nil)))

(defn- service-path? [codices svc k]
  (or (get-in codices [svc k])
      (get-in codices [svc (wild-path? k
    (filter #(.contains % "*") (d/custom-keys (get-in codices [svc]))))])))

(defn req-> [{:keys [request-method headers query-params form-params body]}]
  {:method request-method :hdrs headers :q-params query-params
   :form-params form-params :body (slurp body)})

(defn- mod-1st-hdr
  "If a based on a probability defined in the codex optionally mutate the first
   response header."
  [codex errs {:keys [headers]} prob]
  (let [path-hdrs (d/hdrs-rsp codex)
        hdrs (if-let [r-hdrs headers] (merge path-hdrs r-hdrs) path-hdrs)
        estatus (or (d/err-status codex) errs)]
    (if (and estatus (percentage? (or (d/err-prob codex) prob)))
      (let [k (first (keys hdrs))]
        (if k (st/rename-keys hdrs {k (str k "mutated")}) hdrs))
      hdrs)))

(defn- svc-2-status [{:keys [rsp]} payload]
  (if-let [status (:status rsp)]
    (assoc payload :status status)
    payload))

(defn- err-2-status [codex svc-errs prob payload]
  (let [estatus (or (d/err-status codex) svc-errs)
        eprob (or (d/err-prob codex) prob)]
    (if (and
          (and estatus (percentage? eprob))
          (not (client-err-status? payload)))
      (assoc payload :status (rand-nth estatus))
      payload)))

(defn- verify-headers [req codex payload]
  (if-let [hdrs (d/hdrs-req codex)]
    (if (every? (set (keys (:hdrs req))) (map s/lower-case (keys hdrs)))
      payload
      (assoc payload :status 400))
    payload))

(defn- verify-query-params [req codex payload]
  (if-let [rpms (d/qp codex)]
    (if (every? (set (keys (:q-params req))) (keys rpms))
      payload
      (assoc payload :status 400))
    payload))

(defn- verify-form [req codex payload]
  (if-let [f-keys (d/fp codex)]
    (let [req-form-ks (set (keys (:form-params req)))]
      (if (= req-form-ks (set (keys f-keys)))
        payload
        (assoc payload :status 400)))
    payload))

(defn zip-str [s]
  (z/xml-zip (x/parse (ByteArrayInputStream. (.getBytes s)))))

(defn- map-vals [m k]
  (set (keep k (tree-seq #(or (map? %) (vector? %)) identity m))))

(defn- xml-body [req codex payload]
  (if-let [codex-body (d/body-req codex)]
    (let [req-xml (zip-str (:body req))
          codex-xml (zip-str (c/pretty-xml (d/body-req codex)))
          rb-vals (map-vals req-xml :tag)
          cb-vals (map-vals codex-xml :tag)]
      (if (= rb-vals cb-vals) payload (assoc payload :status 400)))
    payload))

(defn- jsn-body [req codex payload]
  (if-let [codex-body (d/body-req codex)]
    (let [body-jsn (jsn/parse-string (:body req))]
      (if (map? codex-body)
        (let [req-body-ks (set (keys body-jsn))]
          (if (= req-body-ks (set (keys codex-body)))
            payload
            (assoc payload :status 400)))
        (if (contains? codex-body body-jsn)
          payload
          (assoc payload :status 400))))
    payload))

(defn- verify-body [req codex payload]
  (if (h/xml? (p/ctype req))
    (xml-body req codex payload)
    (jsn-body req codex payload)))

(defn- verify-2-status [req codex payload]
  (->> (verify-headers req codex payload)
       (verify-query-params req codex)
       (verify-form req codex)
       (verify-body req codex)))

(defn- default-status [method] {:status (or (method {:get 200 :post 201 :put 204 :delete 204 :head 200}) 500)})

(defn- status [req codex svc-errs prob]
  (->> (default-status (:method req))
       (svc-2-status codex)
       (verify-2-status req codex)
       (err-2-status codex svc-errs prob)))

(defn- headers [codex svc-errs svc-rsp prob payload]
  (if-let [hdrs (mod-1st-hdr codex svc-errs svc-rsp prob)]
    (if (err-status? payload) payload (assoc payload :headers hdrs))
    payload))

;; TODO: this is nasty, needs refactoring, no time right now
(defn- body [codex payload]
  (when (:time (:rsp codex))
    (Thread/sleep (* (:time (:rsp codex)) 1000)))
  (if (err-status? payload)
    payload
    (if-let [body (:body (:rsp codex))]
      (if-let [ctype (d/rsp-type codex)]
        (cond
          (h/xml? ctype) (h/rsp payload ctype (c/pretty-xml body))
          (h/txt? ctype) (h/rsp payload ctype body)
          :else (h/rsp payload h/jsn (c/pretty-js body)))
        (h/rsp payload h/jsn (c/pretty-js body)))
      payload)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn sim-rsp-old-> [{:keys [uri] :as req} codices]
  (let [svc (second (s/split uri #"/"))
        k (second (s/split uri (re-pattern (str "/" (name svc) "/"))))
        svc-errors (get (get-in codices [svc :errors]) :status)
        svc-rsp (get-in codices [svc :rsp])
        prob (or (get (get-in codices [svc :errors]) :probability) 0)
        req (req-> req)]
    (if-let [codex (service-path? codices svc k)]
      (if ((:method req) codex)
        (->> (status req ((:method req) codex) svc-errors prob)
             (headers ((:method req) codex) svc-errors svc-rsp prob)
             (body ((:method req) codex)))
        {:status 405})
      {:status 404})))




;; =============================================================================
;; NEW
;; =============================================================================




(defn- to-endpoint [requested-endpoint sim-rules svc]
  (let [endpoints (keys (get-in sim-rules [svc]))
        to-tuple (fn [endpoint] [(s/replace endpoint #"\*" ".+") endpoint])
        regexs (map to-tuple endpoints)
        is-match (fn [[regex original]] (if (re-matches (re-pattern regex) requested-endpoint) original))]
    (some is-match regexs)
  )
)


(def ^:dynamic tree)
(def ^:dynamic request)
(def ^:dynamic corpus)


(defn sim-rsp-> [{:keys [uri] :as req} codices]
  (println "\nsim-rsp-> req:" req)
  (let [svc (second (s/split uri #"/"))
        sim-rules (m/load-script (str svc ".edn.sim"))
        requested-endpoint (second (s/split uri (re-pattern (str "/" (name svc) "/"))))
        endpoint (to-endpoint requested-endpoint sim-rules svc)
        method (:request-method req)
        rules (get-in sim-rules [svc endpoint method])
        tree (d/to-seq codices svc endpoint method)
        request (assoc-in req [:endpoint] endpoint) ; TODO review this - adding resolved endpoint to request
        corpus {}
        execute (fn [rule]
          (binding [tree tree
                    request request
                    corpus corpus]
             (apply rule nil)))
        response (some identity (map execute rules))]
    (println "uri:" uri)
    (println (count rules) "rules for svc:" svc "endpoint:" endpoint "method:" method)
    (println "responding with" response)
    response)) ; TODO what if there is more than one?

;
; DSL for sim..
;



(defn transport [what where]
  (println "transporting" what "to" where)
)

; TODO use macro with lazy evaluation instead of delay?...
(defn schedule [cron func]
  
  (println "scheduling" func "in" cron)
)

(defn- mime [url]
  (cond
    (.endsWith url ".json") h/jsn
    (.endsWith url ".xml") h/xml
    (.endsWith url ".txt") h/txt
    :else h/bin))

(defn- format-rsp [rsp-entry]
  (if rsp-entry
    (let [status-code (Integer/parseInt (name (key rsp-entry)))
          rsp (val rsp-entry)
          body-url (:body rsp)
          headers (:headers rsp)
          headers_w_ctype (if (and body-url (not (get-in headers ["Content-Type"])))
                            (assoc-in headers ["Content-Type"] (mime body-url))
                            headers)
          body (if body-url (slurp body-url))
          response {:status status-code :headers headers_w_ctype :body body}]
      (println "formatting rsp:" rsp)
      (println "returning :" response)
      response)
    (println "no response found to handle request")))

(defn success []
  (format-rsp (rand-nth (d/success-status tree))))

(defn error []
  (format-rsp (rand-nth (d/error-status tree))))

; TODO use macro with lazy evaluation instead of delay...
(defn prob [n delayed]
  (println "prob" n)
  (if (< (rand) n) @delayed))

