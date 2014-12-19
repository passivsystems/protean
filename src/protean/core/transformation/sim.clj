(ns protean.core.transformation.sim
  "Machinery provided for running sims and powering sim extensions."
  (:require [clojure.string :as s]
            [clojure.set :as st]
            [clojure.pprint]
            [clojure.main :as m]
            [clojure.java.io :refer [file]]
            [protean.core.protocol.http :as h]
            [protean.core.protocol.protean :as p]
            [protean.core.codex.document :as d]
            [protean.core.transformation.coerce :as c]
            [protean.core.codex.placeholder :as ph]
            [protean.core.transformation.validation :as v]
            [clj-http.client :as clt]
            [overtone.at-at :as at]
            [environ.core :as ec]
            [io.aviso.ansi :as aa])
  (:use [taoensso.timbre :as timbre
     :only (trace debug info warn error)
     :rename {trace log-trace debug log-debug info log-info warn log-warn error log-error}]))

(defn- pretty-str [s ctype]
  (cond
    (h/xml? ctype) (c/pretty-xml s)
    (h/txt? ctype) s
    :else (c/pretty-js s)))

(defn- to-endpoint [requested-endpoint paths svc]
  (let [endpoints (keys (get-in paths [svc]))
        any (fn [s] "[^/]+")
        to-tuple (fn [endpoint] [(ph/replace-all-with endpoint any) endpoint])
        regexs (map to-tuple endpoints)
        is-match (fn [[regex original]] (if (re-matches (re-pattern regex) requested-endpoint) original))]
    (some is-match regexs)))

(defn- print-error [e] (println (aa/red (str "caught exception: " (.getMessage e)))))

(defn- fnfirst [x] (first (nfirst x)))

(defn- aug-path-params [req-endpoint cod-endpoint request]
  (let [p-ks (s/split cod-endpoint #"/")
        p-vs (s/split req-endpoint #"/")
        raw-params (into {} (filter #(re-seq ph/ph (key %)) (zipmap p-ks p-vs)))
        params (into {} (for [[k v] raw-params] [(fnfirst (re-seq ph/ph k)) v]))]
    (assoc request :path-params params)))

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
        endpoint (to-endpoint requested-endpoint paths svc)
        method (:request-method req)
        rules (get-in sims [svc endpoint method])
        tree (get-in paths [svc endpoint method])
          ; (d/to-seq codices svc endpoint method)
        body-in (:body req)
        request (assoc req
          ; make endpoint available in request
          :endpoint endpoint
          :svc svc
          ; also convert body from input stream to content, since we may need to access it more than once
          :body (if body-in (slurp body-in) ""))
        corpus {}
        execute (fn [rule]
          (if (not tree) nil)
          (try
            (binding [*tree* tree
                      *request* (aug-path-params requested-endpoint endpoint request)
                      *corpus* corpus]
               (apply rule nil))
            (catch Exception e (print-error e))))
        rules-response (some identity (map execute rules))
        default-success (binding [*tree* tree *request* request *corpus* corpus](success))
        ; we return the first non-nil response, else a success response.
        response (if rules-response rules-response default-success)]
    (if (not tree)
      (do
        (log-warn "Warning - no endpoint found for" [svc endpoint method])
        (if-let [supported-methods (keys (get-in paths [svc endpoint]))]
          {:status 405 :headers {"Allow" (s/join ", " (map #(.toUpperCase (name %)) supported-methods))}}))
      (do
        (log-debug "executed" (count rules) "rules for uri:" uri "(svc:" svc "endpoint:" endpoint "method:" method ")")
        (log-debug "responding with" response)
        response))))


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
          (log-debug "timeout - executing job")
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

(defmacro in
  [delay-ms then]
  `(after-delayed ~delay-ms (delay ~then)))


;; =============================================================================
;; Requests
;; =============================================================================

(defn query-param [p] (get-in *request* [:query-params p]))

(defn path-param [p] (get-in *request* [:path-params p]))

(defn param [p] (get-in *request* [:params p]))

(defn route-param
  "Simplisticly grabs the last part of a uri"
  [route-params]
  (last (s/split (:* route-params) #"/")))

(defn form-param [p] (get-in *request* [:form-params p]))

(defn body-param [p] ((c/clj (:body *request*)) p))

(defn header [h] (get-in *request* [:headers h]))


;; =============================================================================
;; Responses
;; =============================================================================

(defn- format-rsp [rsp-entry]
  (if rsp-entry
    (let [status-code (Integer/parseInt (name (key rsp-entry)))
          rsp (val rsp-entry)
          body-url (:body-example rsp)
          headers (:headers rsp)
          headers_w_ctype (if (and body-url (not (get-in headers [h/ctype])))
                            (assoc headers h/ctype (h/mime body-url))
                            headers)
          body (if body-url (slurp body-url))
          response {:status status-code :headers headers_w_ctype :body body}]
      (log-debug "formatting rsp:" rsp)
      (log-debug "returning :" response)
      response)
    (log-info "no response found to handle request")))

(defn success
  "Returns a randomly selected success response as defined for endpoint"
  []
  (let [successes (d/success-status *tree*)
        {:keys [svc request-method uri]} *request*
        success (format-rsp (rand-nth successes))]
    (if (empty? successes)
      (log-warn "warning - no successes found for endpoint" [svc uri request-method])
      (ph/swap success *tree* {} :gen-all true))))

(defn error
  "Returns a specific or randomly selected error response for an endpoint"
  ([x]
    (let [errors (d/error-status *tree*)
          {:keys [svc request-method uri]} *request*
          error (format-rsp
                  (first (filter #(= (first %) (keyword (str x))) errors)))]
      (if (empty? errors)
        (log-warn "warning - no errors found for endpoint" [svc uri request-method])
        (ph/swap error *tree* {} :gen-all true))))
  ([] (error (Long. (name (first (rand-nth (d/error-status *tree*))))))))

(defn respond
  ([status] {:status status})
  ([status & {:keys [body-url]}]
    {:status status
     :body (slurp body-url)
     :headers {h/ctype (h/mime body-url)}}))

(defn encode
  "Encode d using header content type information in request"
  [d]
  (println "accept : " (get-in *request* [:headers "accept"]))
  (let [accept (p/accept *request*)]
  (cond
    (= accept h/xml) (c/xml d)
    (= accept h/txt) (str d)
    :else (c/js d))))

;; TODO: polymorphic slurp of body based on understanding if it is path or data
;; TODO: construct rsp (ctype etc) headers based on request accept header etc
(defn rsp
  "Creates rsp inferring status and content type from request.
   Defaults to org or personal prefs in includes."
   [body]
   (let [status 200
         headers {h/ctype h/jsn}]
     {:status status :headers headers :body body}))


;; TODO: rename, this is not forming a response, merely grabbing a body
(defn rsp-body-file
  "Look in a directory structure 'data-path' for a file 'f-name' with given ext"
  [data-path f-name ext]
  (let [fs (file-seq (file data-path))]
    (first (filter #(= (.getName %) (str f-name ext)) fs))))

;; TODO: rename, this is not forming a response, merely grabbing a body
(defn rsp-body-state
  "Look in a piece of state s for a key k"
  [s k] (first (filter #(= (:id %) k) s)))

(defmacro prob
  "First arity evaluates the provided fn with specified probability.
   Second arity evaulates first fn with provided prob else second fn."
  ([n then] `(if (< (rand) ~n) ~then))
  ([n then else] `(if (< (rand) ~n) ~then ~else)))

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
    (log-debug "res" res)
    (if-let [log-file (:log content)]
      (log [(str "Response from " (:url content)) res] log-file))))

(defn simple-request
  [method url body]
  (make-request method url
                {:content-type (header "content-type")
                 :headers (dissoc (:headers *request*) "content-length")
                 :body body}))

(defn env
  "Accesses environment variables"
  [name]
  (ec/env name))


;; =============================================================================
;; Validation of Request by Codex Specification
;; =============================================================================

(defn- validate-body [request tree errors]
  (let [expected-ctype (d/req-ctype tree)
        schema (d/get-in-tree tree [:req :body-schema])
        codex-body (d/body-req tree)]
    (v/validate-body request expected-ctype schema codex-body errors)))

(defn valid-inputs?
  "Validate request against codex specification"
  []
  (let [errors
    (->> []
      (v/validate-headers (d/req-hdrs *tree*) *request*)
      (v/validate-query-params *request* *tree*)
      (v/validate-form-params *request* *tree*)
      (validate-body *request* *tree*))]
      (if (empty? errors)
      true
      (log-info (s/join "," errors)))))

(defmacro validate [then] `(if (valid-inputs?) ~then (respond 400)))
