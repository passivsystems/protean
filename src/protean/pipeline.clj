(ns protean.pipeline
  (:require [clojure.edn :as edn]
            [clojure.core.incubator :as ib]
            [clojure.java.io :refer [delete-file]]
            [ring.util.codec :as cod]
            [me.raynes.laser :as l]
            [protean.transformation.sim :as txsim]
            [protean.transformation.coerce :as txco]
            [protean.transformation.analysis :as txan]
            [protean.transformation.curly :as txc]
            [protean.transformations.docs :as txdocs]
            [protean.transformations.testsim :as txts]
            [protean.transformations.testapi :as txta])
  (:use [clojure.string :only [join split upper-case]]
        [clojure.set :only [intersection]]
        [clojure.java.io :refer [file]]
        [taoensso.timbre :as timbre :only (trace debug info warn error)]
        [me.rossputin.pew])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(def json {:headers {"Content-Type" "application/json; charset=utf-8"}})

(def state (atom {}))

(defn- log-request [req]
  (info "request is : " req)
  (info "request method : " (:request-method req))
  (info "request uri : " (:uri req))
  (info "request query params : " (:query-params req)))

(defn handler
  [f & handlers]
  (reduce (fn [handled h] (partial h handled)) f (reverse handlers)))

(defn handle-proj-del-error
  [f & args]
  (try
    (apply f args)
    (catch IOException ioex
      (error (.getMessage ioex))
      {:status 500})))

(defn- body [req-body]
  (let [rbody (slurp req-body)] (if (not-empty rbody) (txco/clj-> rbody) nil)))


;; =============================================================================
;; Service pipelines
;; =============================================================================

(defn api [req]
  (log-request req)
  (txsim/sim-rsp-> req @state))

(defn test! [{:keys [params] :as req} host port]
  (let [body (body (:body req)) h (get body "host") p (get body "port")]
    (let [host (or h host) port (or p port)
          res (if (or h p)
                (txta/testapi-analysis-> host port @state body)
                (txts/testsim-analysis-> host port @state body))]
      (assoc json :body (txco/js-> res)))))


;; =============================================================================
;; Admin pipelines
;; =============================================================================

;; services
;;;;;;;;;;;

(defn services []  (assoc json :body (txco/js-> (sort (keys @state)))))

(defn service [id] (assoc json :body (txco/js-> ((keyword id) @state))))

(defn service-usage [id host port]
  (assoc json :body (txco/js-> (txc/curly-analysis-> host port @state id))))

(defn del-proj [id]
  (reset! state (dissoc @state (keyword id)))
  (delete-file (str id ".edn"))
  {:status 204})

(def del-proj-handled (handler del-proj handle-proj-del-error))

(defn put-services [req]
  (let [file ((:params req) "file")
        data (edn/read-string (slurp (:tempfile file)))]
    (reset! state (merge @state data))
    (doseq [d data]
      (spit (str (name (key d)) ".edn") (pr-str {(key d) (val d)})))
    (services)))

(defn delete-proj-errors [service]
  (reset! state (ib/dissoc-in @state [(keyword service) :errors :status]))
  {:status 204})

(defn put-proj-error [proj err]
  (reset! state
    (update-in @state [(keyword proj) :errors :status] conj (txco/int-> err)))
  {:status 204})

(defn put-proj-error-prob [proj prob]
  (reset! state
    (assoc-in @state [(keyword proj) :errors :probability] (txco/int-> prob)))
  {:status 204})


;; services documentation
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn services-docs [] (txdocs/services-template (sort (keys @state))))

(defn service-docs [id host port]
  (txdocs/service-template id
    (txan/analysis-> host port @state {"locs" [id]})))

(l/defdocument service-index (file "public/html/index.html") []
  (l/id="project-version") (<- (txdocs/version)))

(l/defdocument service-api (file "public/html/api.html") []
  (l/id="project-version") (<- (txdocs/version)))

(l/defdocument service-documentation (file "public/html/documentation.html") []
  (l/id="project-version") (<- (txdocs/version)))

(l/defdocument service-road (file "public/html/roadmap.html") []
  (l/id="project-version") (<- (txdocs/version)))


;; service status
;;;;;;;;;;;;;;;;;

(defn status [] (assoc json :body (txco/js-> {"status" "ok"})))
