(ns protean.core.transformation.sim
  "Machinery provided for running sims and powering sim extensions."
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
            [protean.core.codex.placeholder :as ph]
            [protean.core.transformation.jsonvalidation :as jv]
            [protean.core.transformation.xmlvalidation :as xv]
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
  (if-let [body-schema (d/get-in-tree tree [:req :body-schema])]
    (let [validation (xv/validate body-schema (:body request))]
      (if (:success validation)
        true
        (println "Request did not conform to json validation" body-schema ":" (:message validation))))
    ; TODO deprecate old body definition
    (if-let [codex-body (d/body-req tree)]
      (let [tags-in-str (fn [s] (map-vals (zip-str s) :tag))
            expected-tags (tags-in-str (c/pretty-xml codex-body))
            received-tags (tags-in-str (:body request))]
        (if (= received-tags expected-tags)
          true
          (println "Xml body not valid - expected" expected-tags "but received" received-tags)))
      true)))

(defn- valid-jsn-body? [request tree]
  (if-let [body-schema (d/get-in-tree tree [:req :body-schema])]
    (let [validation (jv/validate body-schema (:body request))]
      (if (:success validation)
        true
        (println "Request did not conform to json validation" body-schema ":" (:message validation))))
    ; TODO deprecate old body definition
    (if-let [codex-body (d/body-req tree)]
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
        any (fn [s] "[^/]+")
        to-tuple (fn [endpoint] [(ph/replace-all-with endpoint any) endpoint])
        regexs (map to-tuple endpoints)
        is-match (fn [[regex original]] (if (re-matches (re-pattern regex) requested-endpoint) original))]
    (some is-match regexs)))

(defn- print-error [e] (println (aa/red (str "caught exception: " (.getMessage e)))))

(def ^:dynamic *tree*)
(def ^:dynamic *request*)
(def ^:dynamic *corpus*)


;; =============================================================================
;; Entry
;; =============================================================================

(declare success)
(defn sim-rsp-> [{:keys [uri] :as req} paths sims]
  (let [svc (second (s/split uri #"/"))
        requested-endpoint (second (s/split uri (re-pattern (str "/" (name svc) "/"))))
        endpoint (to-endpoint requested-endpoint sims svc)
        method (:request-method req)
        rules (get-in sims [svc endpoint method])
        tree (get-in paths [svc endpoint method])
          ; (d/to-seq codices svc endpoint method)
        body-in (:body req)
        request (assoc req
          ; make endpoint available in request
          :endpoint endpoint
          ; also convert body from input stream to content, since we may need to access it more than once
          :body (if body-in (slurp body-in) ""))
        corpus {}
        execute (fn [rule]
          (if (not tree) (println "Warning - no endpoint found for" [svc endpoint method]))
          (try
            (binding [*tree* tree
                      *request* request
                      *corpus* corpus]
               (apply rule nil))
            (catch Exception e (print-error e))))
        rules-response (some identity (map execute rules))
        default-success (binding [*tree* tree *request* request *corpus* corpus](success))
        ; we return the first non-nil response, else a success response. (TODO should be imported from a default sim.edn file)
        response (if rules-response rules-response default-success)]
    (println "executed" (count rules) "rules for uri:" uri "(svc:" svc "endpoint:" endpoint "method:" method ")")
    (println "responding with" response)
    response))


;; =============================================================================
;; DSL for sims
;; =============================================================================

; TODO provide schema in codex for xml/json, and validate against that.
(defn valid-inputs? []
  (and
    (valid-headers? *request* *tree*)
    (valid-query-params? *request* *tree*)
    (valid-form? *request* *tree*)
    (valid-body? *request* *tree*)))


;; =============================================================================
;; Scheduling
;; =============================================================================

(def ^:private schedule-pool (at/mk-pool))

(defn- job
  "Creates a job to be scheduled from provided delay - will ensure dynamic bindings are preserved"
  [delayed]
  (let [captured_tree *tree*
        captured_request *request*
        captured_corpus *corpus*]
    (fn []
      (try
        (do
          (println "timeout - executing job")
          (binding [*tree* captured_tree
                    *request* captured_request
                    *corpus* captured_corpus]
            @delayed))
      (catch Exception e (print-error e))))))

(defn at-delayed [ms-time delayed]
  (at/at ms-time (job delayed) schedule-pool)
;  (at/show-schedule schedule-pool)
  nil)

(defmacro at
  [ms-time then]
  `(at-delayed ~ms-time (delay ~then)))

(defn after-delayed
  [delay-ms delayed]
  (at/after delay-ms (job delayed) schedule-pool)
;  (at/show-schedule schedule-pool)
  nil)

(defmacro after
  [delay-ms then]
  `(after-delayed ~delay-ms (delay ~then)))


;; =============================================================================
;; Responses
;; =============================================================================

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
  "Returns a randomly selected success response as defined for endpoint"
  []
  (format-rsp (rand-nth (d/success-status *tree*))))

(defn error
  "Returns a randomly selected error response as defined for endpoint"
  []
  (format-rsp (rand-nth (d/error-status *tree*))))

(defn respond
  "Creates a response with given status-code.
   If body-url is provided, will include the content and inferred content-type."
  [status-code & {:keys [body-url]}]
  (if body-url
    {:status status-code
      :body (slurp body-url)
      :headers {"Content-Type" (mime body-url)}}
    {:status status-code}))

(defmacro prob
  "Will evaluate the provided function with specified probability"
  [n then]
  `(if (< (rand) ~n) ~then))

(defn log [what where]
  (let [to-log [(str (java.util.Date.)) what]]
    (spit where (with-out-str (clojure.pprint/pprint to-log)) :append true)))

(defn make-request
  "Makes an API request"
  ([method url content]
    (let [the-request (assoc content
          :url url
          :method method
          :throw-exceptions false)
          res (clt/request the-request)]
      (println "res" res)
      (if-let [log-file (:log content)]
        (log [(str "Response from " (:url content)) res] log-file))))

  ([method url request body]
    (let [the-request
            {:url url
             :method method
             :body body
             :content-type (get-in request [:headers "content-type"])
             :throw-exceptions false}
          res (clt/request the-request)]
      (println "res" res))))

(defn post [url body] (make-request :post url *request* body))

(defn put [url body] (make-request :put url *request* body))

(defn env
  "Accesses environment variables"
  [name]
  (ec/env name))
