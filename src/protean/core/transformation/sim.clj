(ns protean.core.transformation.sim
  "Machinery provided for running sims and powering sim extensions."
  (:require [clojure.string :as s]
            [clojure.set :as st]
            [clojure.pprint]
            [clojure.main :as m]
            [clojure.java.io :refer [file]]
            [me.rossputin.diskops :as dk]
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

(defn- parse-endpoint [requested-endpoint cod-endpoint]
  ; TODO instead of requiring namedparameter to start with ';' when a matrix parameter
  ;      could look up [:var s :type] to see if is a Matrix parameter
  ;      however currently require parse-endpoint before we can get tree
  (let [any (fn [s] (if (.startsWith s ";")
              "(;[^/^?]*)?" ; matrixParam regex
              "([^/^?]+)")) ; pathParam regex
        regex (ph/replace-all-with cod-endpoint any)]
    (re-matches (re-pattern regex) requested-endpoint)))

(defn- to-endpoint [requested-endpoint paths svc]
  (let [endpoints (keys (get-in paths [svc]))
        filtered-ep (filter #(parse-endpoint requested-endpoint %) endpoints)]
    (if (next filtered-ep)
      (or (some #{requested-endpoint} filtered-ep) requested-endpoint nil)
        (first (filter #(parse-endpoint requested-endpoint %) endpoints)))))

(defn- stacktrace [e]
  (let [sw (java.io.StringWriter.)]
    (.printStackTrace e (java.io.PrintWriter. sw))
    (.toString sw)))

(defn- print-error [e] (log-error (aa/red (str "caught exception: " (stacktrace e)))))

(defn- aug-path-params [req-endpoint cod-endpoint request]
  (let [ph-ks (map second (re-seq ph/ph cod-endpoint))
        ph-vs (drop 1 (parse-endpoint req-endpoint cod-endpoint))
        params (into {} (map vector ph-ks ph-vs))]
    (assoc request :path-params params)))

(def ^:dynamic *tree*)
(def ^:dynamic *request*)
(def ^:dynamic *corpus*)


;; =============================================================================
;; Sim Machinery Access
;; =============================================================================

(defn qslurp
  "Quantum slurp, used to look for sim extension referenced resources in
   multiple places.
   p is a resource path (probably relative)."
  [p] (slurp (d/to-path p *tree*)))


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

(defn body [] (:body *request*))

(defn body-clj
  ([] (c/clj (body)))
  ([k] (c/clj (body) (or k false))))

(defn query-param [p] (get-in *request* [:query-params p]))

(defn path-param [p] (get-in *request* [:path-params p]))

(defn flip [f]
  (fn [x y] (f y x)))

(defn matrix-params [mp-name]
  (if-let [pp (path-param (str ";" mp-name))]
    (->> pp
        ((flip s/split) #";")
        (filter seq)
        (map #(s/split % #"="))
        (into {}))))

(defn param [p] (get-in *request* [:params p]))

(defn route-param
  "Simplisticly grabs the last part of a uri"
  [route-params]
  (last (s/split (:* route-params) #"/")))

(defn form-param [p] (get-in *request* [:form-params p]))

(defn body-param
  ([p] ((body-clj) p))
  ([p k] (p (body-clj k))))

(defn header [h] (get-in *request* [:headers h]))


;; =============================================================================
;; Responses
;; =============================================================================

(defn- format-rsp [rsp-entry]
  (if rsp-entry
    (let [status-code (Integer/parseInt (name (key rsp-entry)))
          rsp (val rsp-entry)
          body-url (first (:body-examples rsp))
          headers (:headers rsp)
          headers_w_ctype (if (and body-url (not (get-in headers [h/ctype])))
                            (assoc headers h/ctype (h/mime body-url))
                            headers)
          raw-body (if body-url (slurp (d/to-path body-url *tree*)))
          body (if (h/txt? (get headers_w_ctype h/ctype))
                  (s/trim-newline raw-body)
                  raw-body)
          response {:status status-code :headers headers_w_ctype :body body}]
      (log-debug "rsp headers including inferred content type : " headers_w_ctype)
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
          error (filter #(= (first %) (keyword (str x))) errors)
          error-rsp (format-rsp (first error))]
      (when (empty? errors) (log-warn "warning - no errors found for endpoint" [svc uri request-method]))
      (when (empty? error) (log-warn "warning - sim extension error not described in codex" [svc uri request-method]))
      (if (seq error) (ph/swap error-rsp *tree* {} :gen-all true) {:status x})))
  ([] (error (Long. (name (first (rand-nth (d/error-status *tree*))))))))

(defn respond
  ([status] {:status status})
  ([status & {:keys [body-url]}]
    {:status status
     :body (qslurp body-url)
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
      (log [(str "Response from " (:url content)) res] log-file))
    res))

(defn simple-request
  [method url hdrs body]
  (make-request method url
                {:content-type (header "content-type")
                 :headers (merge
                            (dissoc (:headers *request*) "content-length")
                            hdrs)
                 :body body}))

(defn env
  "Accesses environment variables"
  [name] (ec/env name))


;; =============================================================================
;; Validation of Request by Codex Specification
;; =============================================================================

(defn- validate-body [request tree errors]
  (let [expected-ctype (d/req-ctype tree)
        schema (d/to-path (d/get-in-tree tree [:req :body-schema]) tree)
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


;; =============================================================================
;; Sim Execution
;; =============================================================================

(defn- sim-req
  "Prepare request for sim binding - augment with necessary information.
   Handles converting body from input stream to content for multi access."
  [req ep svc]
  (assoc req :endpoint ep :svc svc :body (or (dk/slurp-pun (:body req)) "")))

(defn- protean-error-405 [supported-methods]
  {:status 405
   :headers {
     "Protean-error" "Method Not Allowed"
     "Allow" (s/join ", " (map #(s/upper-case (name %)) supported-methods))
   }
  })

(defn- protean-error-404 []
  {:status 404 :headers {"Protean-error" "Not Found"}})

(defn- protean-error-500 []
  {:status 500 :headers {"Protean-error" "Error in sim"}})

(defn- execute-fn
  "Prepare bindings for use through out sim execution context.
   Handle rules processing.

   rep is the requested endpoint
   ep is the endpoint
   req is the request"
  [tree corpus rep ep req]
  (fn [rule]
    (if (not tree) nil)
    (try
      (binding [*tree* tree
                *request* (aug-path-params rep ep req)
                *corpus* corpus]
        (apply rule nil))
    (catch Exception e
      (print-error e)
      (protean-error-500)))))

(declare success)

(defn- http-options [paths svc endpoint sim-cfg]
  (let [e (get-in paths [svc endpoint])
        m (map #(s/upper-case (name %)) (keys e))
        h (keys (into {} (for [[k v] e] (d/req-hdrs v))))
        hdrs {"Content-Type" "text/html"
              "Access-Control-Allow-Methods" (s/join ", "  m)
              "Access-Control-Allow-Headers" (s/join ", "  h)}
        cors-hdrs (if (false? (:cors sim-cfg)) hdrs (assoc hdrs "Access-Control-Allow-Origin" "*"))]
    [{:rsp {:200 {:headers cors-hdrs}}}]))

(defn sim-rsp [{:keys [uri] :as req} paths sims]
  (let [svc (second (s/split uri #"/"))
        requested-endpoint (second (s/split uri (re-pattern (str "/" (name svc) "/"))))
        endpoint (to-endpoint requested-endpoint paths svc)
        method (:request-method req)
        rules (get-in sims [svc endpoint method])
        sim-cfg (:sim-cfg sims)
        tree (if-let [x (get-in paths [svc endpoint method])]
               x
               (when (= method :options) (http-options paths svc endpoint sim-cfg)))
        request (sim-req req endpoint svc)
        corpus {}
        execute (execute-fn tree corpus requested-endpoint endpoint request)
        default-success (binding [*tree* tree *request* request *corpus* corpus]
                          (if (false? (:validating sim-cfg))
                            (success)
                            (validate (success))))
        response (or (some identity (map execute rules)) default-success)]
    (log-info "sim cfg : " sim-cfg)
    (if (not tree)
      (do
        (log-warn "warning - no endpoint found for" [uri method])
        (if-let [supported-methods (keys (get-in paths [svc endpoint]))]
          (protean-error-405 supported-methods)
          (protean-error-404)))
      (do
        (log-debug "executed" (count rules) "rules for uri:" uri "(svc:" svc "endpoint:" endpoint "method:" method ")")
        (log-debug "responding with" response)
        (cond
           (false? (:cors sim-cfg)) response
           (map? response)          (merge-with merge {:headers {"Access-Control-Allow-Origin" "*"}} response)
           :else                    {:headers {"Access-Control-Allow-Origin" "*"} :body response})))))
