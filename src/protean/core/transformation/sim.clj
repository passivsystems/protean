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
            [environ.core :as ec]
            [io.aviso.ansi :as aa])
  (:import java.io.ByteArrayInputStream))

;; =============================================================================
;; Verify request functions
;; =============================================================================

(defn- valid-headers? [request tree]
  (let [hdrs (d/hdrs-req tree)]
    (if hdrs
      (let [expected-headers (map s/lower-case (keys hdrs))
            received-headers (keys (:hdrs request))]
        (if (every? (set received-headers) expected-headers)
          true
          (println "Headers not valid - expected" expected-headers "but received" received-headers)))
      true)))

(defn- valid-query-params? [request tree]
  (let [rpms (d/qp tree)]
    (if rpms
      (let [expected-qps (keys rpms)
            received-qps (map name (keys (:params request)))]
        (if (every? (set received-qps) expected-qps)
          true
          (println "Query params not valid - expected" expected-qps "but received" received-qps)))
      true)))

(defn- valid-form? [request tree]
  (let [f-keys (d/fp tree)]
    (if f-keys
      (let [expected-form (keys f-keys)
            received-form (keys (:form-params request))]
        (if (= (set received-form) (set (keys f-keys)))
          true
          (println "Form params not valid - expected" expected-form "but received" received-form)))
      true)))

(defn- zip-str [s] (z/xml-zip (x/parse (ByteArrayInputStream. (.getBytes s)))))
(defn- map-vals [m k] (set (keep k (tree-seq #(or (map? %) (vector? %)) identity m))))
(defn- valid-xml-body? [request tree]
  (let [codex-body (d/body-req tree)
        tags-in-str (fn [s] (map-vals (zip-str s) :tag))]
    (if codex-body
      (let [expected-tags (tags-in-str (c/pretty-xml codex-body))
            received-tags (tags-in-str (:body request))]
        (if (= received-tags expected-tags)
          true
          (println "Xml body not valid - expected" expected-tags "but received" received-tags)))
      true)))

(defn- valid-jsn-body? [request tree]
  (let [codex-body (d/body-req tree)]
    (if codex-body
      (let [body-jsn (jsn/parse-string (:body request))]
        (if (map? codex-body)
          (let [expected-keys (set (keys codex-body))
                received-keys (set (keys body-jsn))]
            (if (= received-keys expected-keys)
              true
              (println "Json body not valid - expected" expected-keys "but received" received-keys)))
          (contains? codex-body body-jsn)))
      true)))

(defn- valid-body? [request tree]
  (if (h/xml? (p/ctype request))
    (valid-xml-body? request tree)
    (valid-jsn-body? request tree)))



(defn- pretty-str [s ctype]
  (cond
    (h/xml? ctype) (c/pretty-xml s)
    (h/txt? ctype) s
    :else (c/pretty-js s)))

(defn- to-endpoint [requested-endpoint sim-rules svc]
  (let [endpoints (keys (get-in sim-rules [svc]))
        to-tuple (fn [endpoint] [(s/replace endpoint #"\*" ".+") endpoint])
        regexs (map to-tuple endpoints)
        is-match (fn [[regex original]] (if (re-matches (re-pattern regex) requested-endpoint) original))]
    (some is-match regexs)))

(defn- print-error [e] (println (aa/red (str "caught exception: " (.getMessage e)))))

(def ^:dynamic tree)
(def ^:dynamic request)
(def ^:dynamic corpus)


;; =============================================================================
;; Entry
;; =============================================================================

(defn sim-rsp-> [{:keys [uri] :as req} codices]
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
            (catch Exception e (print-error e))))
        ; we return the first non-nil response. If there are none - will return nil (resolves to 404)
        response (some identity (map execute rules))]
    (println "executed" (count rules) "rules for uri:" uri "(svc:" svc "endpoint:" endpoint "method:" method ")")
    (println "responding with" response)
    response))


;; =============================================================================
;; DSL for sims
;; =============================================================================


; TODO provide schema in codex for xml/json, and validate against that.
(defn valid-inputs? []
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
      (catch Exception e (print-error e))))))

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
  [status-code & {:keys [body-url]}]
  (if body-url
    {:status status-code
      :body (slurp body-url)
      :headers {"Content-Type" (mime body-url)}}
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
