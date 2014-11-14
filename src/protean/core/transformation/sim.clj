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
            [protean.core.transformation.coerce :as c]
            [clj-http.client :as clt]
            [overtone.at-at :as at]
            [environ.core :as ec])
  (:import java.io.ByteArrayInputStream))

;; =============================================================================
;; Verify request functions
;; =============================================================================

(defn- valid-headers? [request tree]
  (let [hdrs (d/hdrs-req tree)]
    (if hdrs
      (every? (set (keys (:hdrs request))) (map s/lower-case (keys hdrs)))
      true)))

(defn- valid-query-params? [request tree]
  (let [rpms (d/qp tree)]
    (if rpms
      (every? (set (keys (:q-params request))) (keys rpms))
      true)))

(defn- valid-form? [request tree]
  (let [f-keys (d/fp tree)]
    (if f-keys
      (= (set (keys (:form-params request))) (set (keys f-keys)))
      true)))

(defn- valid-xml-body? [request tree]
  (let [codex-body (d/body-req tree)
        zip-str (fn [s] (z/xml-zip (x/parse (ByteArrayInputStream. (.getBytes s)))))
        map-vals (fn [m k] (set (keep k (tree-seq #(or (map? %) (vector? %)) identity m))))]
    (if codex-body
      (let [req-xml (zip-str (:body request))
            codex-xml (zip-str (c/pretty-xml codex-body))]
        (= (map-vals req-xml :tag) (map-vals codex-xml :tag)))
       true)))

(defn- valid-jsn-body? [request tree]
  (let [codex-body (d/body-req tree)]
    (if codex-body
      (let [body-jsn (jsn/parse-string (:body request))]
        (if (map? codex-body)
          (= (set (keys body-jsn)) (set (keys codex-body)))
          (contains? codex-body body-jsn)))
      true)))

(defn- valid-body? [request tree]
  (if (h/xml? (p/ctype request))
    (valid-xml-body? request tree)
    (valid-jsn-body? request tree)))



; TODO use (c/pretty-xml body) and (c/pretty-js body) to format response as appropriate
; (cond
;          (h/xml? ctype) (h/rsp payload ctype (c/pretty-xml body))
;          (h/txt? ctype) (h/rsp payload ctype body)
;          :else (h/rsp payload h/jsn (c/pretty-js body)))
;        (h/rsp payload h/jsn (c/pretty-js body)))
  

;; =============================================================================
;; NEW
;; =============================================================================




(defn- to-endpoint [requested-endpoint sim-rules svc]
  (let [endpoints (keys (get-in sim-rules [svc]))
        to-tuple (fn [endpoint] [(s/replace endpoint #"\*" ".+") endpoint])
        regexs (map to-tuple endpoints)
        is-match (fn [[regex original]] (if (re-matches (re-pattern regex) requested-endpoint) original))]
    (some is-match regexs)))


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
        body-in (:body req)
        request (assoc req
          ; make endpoint available in request
          :endpoint endpoint
          ; also convert body from input stream to content, since we may need to access it more than once
          :body (if body-in (slurp body-in) ""))
        corpus {}
        execute (fn [rule]
          (try
            (binding [tree tree
                      request request
                      corpus corpus]
               (apply rule nil))
            (catch Exception e (println "caught exception: " (.getMessage e)))))
        ; we return the first non-nil response. If there are none - will return nil (resolves to 404)
        response (some identity (map execute rules))]
    (println "executed" (count rules) "rules for uri:" uri "(svc:" svc "endpoint:" endpoint "method:" method ")")
    (println "responding with" response)
    response))



(defn valid-inputs? []
  (println "verifying inputs:")
;  (clojure.pprint/pprint tree)

  (and
    (valid-headers? request tree)
    (valid-query-params? request tree)
    (valid-form? request tree)
    (valid-body? request tree)))

;; =============================================================================
;; Scheduling
;; =============================================================================

(def ^:private schedule-pool (at/mk-pool))

(defn- job
  "Creates a job to be scheduled from provided delay - will ensure dynamic bindings are preserved"
  [delayed]
  (let [captured_tree tree
        captured_request request
        captured_corpus corpus]
    (fn []
      (try
        (do
          (println "timeout - executing job")
          (binding [tree captured_tree
                    request captured_request
                    corpus captured_corpus]
            @delayed))
      (catch Exception e (println "caught exception: " (.getMessage e)))))))

(defn at [ms-time delayed] 
  (at/at ms-time (job delayed) schedule-pool)
;  (at/show-schedule schedule-pool)
  nil)

; TODO use macro with lazy evaluation instead of delay?...
(defn after [delay-ms delayed]
  (at/after delay-ms (job delayed) schedule-pool)
;  (at/show-schedule schedule-pool)
  nil)


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
                            (assoc headers "Content-Type" (mime body-url))
                            headers)
          body (if body-url (slurp body-url))
          response {:status status-code :headers headers_w_ctype :body body}]
      (println "formatting rsp:" rsp)
      (println "returning :" response)
      response)
    (println "no response found to handle request")))

(defn success
  "Returns a (randomly selected) success response as defined for endpoint"
  []
  (format-rsp (rand-nth (d/success-status tree))))

(defn error
  "Returns a (randomly selected) error response as defined for endpoint"
  []
  (format-rsp (rand-nth (d/error-status tree))))

(defn respond
  "Creates a response with given status-code.
   If body-url is provided, will include the content and inferred content-type."
  [status-code & body-url]
  (if body-url
    {:status status-code :body (slurp body-url) :headers {"Content-Type" (mime body-url)}}
    {:status status-code}))

; TODO use macro with lazy evaluation instead of delay...
(defn prob
  "Will evaluate the provided function with specified probability"
  [n delayed]
  (if (< (rand) n) @delayed))

(defn log [what where]
  (let [to-log [(str (java.util.Date.)) what]]
    (spit where (with-out-str (clojure.pprint/pprint to-log)) :append true)))

(defn make-request
  "Makes an API request"
  [method url content]
  (let [the-request (assoc content
          :url url
          :method method
          :throw-exceptions false)
        res (clt/request the-request)]
    (println "res" res)
    (if-let [log-file (:log content)]
      (log [(str "Response from " (:url content)) res] log-file))))

(defn env
  "Accesses environment variables"
  [name]
  (ec/env name))
