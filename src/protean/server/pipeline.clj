(ns protean.server.pipeline
  (:require [clojure.edn :as edn]
            [clojure.core.incubator :as ib]
            [clojure.main :as m]
            [clojure.java.io :refer [delete-file]]
            [ring.util.codec :as cod]
            [me.raynes.laser :as l]
            [protean.config :as c]
            [protean.core.codex.document :as d]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.sim :as txsim]
            [protean.core.transformation.coerce :as co]
            [protean.core.transformation.analysis :as txan]
            [protean.core.transformation.curly :as txc]
            [protean.server.docs :as txdocs]
            [protean.core.codex.reader :as r])
  (:use [clojure.string :only [join split upper-case]]
        [clojure.set :only [intersection]]
        [clojure.java.io :refer [file]]
        [taoensso.timbre :as timbre :only (trace debug info warn error)]
        [me.rossputin.pew])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(def json {:headers {h/ctype h/jsn}})

(def state (atom {}))
(def sims (atom {}))

(defn- log-request [{:keys [request-method uri query-params] :as req}]
  (debug "request is : " req)
  (info "method: " request-method ", uri: " uri ", query-params: " query-params))

(defn handler
  [f & handlers]
  (reduce (fn [handled h] (partial h handled)) f (reverse handlers)))

(defn handle-error
  [f & args]
  (try
    (apply f args)
    (catch IOException ioex
      (error (.getMessage ioex))
      {:status 500})))

(defn- body [req-body]
  (let [rbody (slurp req-body)] (if (not-empty rbody) (co/clj rbody) nil)))


;; =============================================================================
;; Service pipelines
;; =============================================================================

(defn api [req]
  (log-request req)
  (txsim/sim-rsp-> req @state @sims))


;; =============================================================================
;; Admin pipelines
;; =============================================================================

;; services
;;;;;;;;;;;

(defn services [] (assoc json :body (co/js (sort (d/custom-keys @state)))))

(defn service [id] (assoc json :body (co/js (get-in @state [id]))))

(defn service-usage [id host]
  (assoc json :body (co/js (txc/curly-analysis-> host (c/sim-port) @state id))))

(defn del-service [svc]
  (reset! state (ib/dissoc-in @state [svc]))
  {:status 204})

(def del-service-handled (handler del-service handle-error))

(defn put-services [req]
  (let [file ((:params req) "file")
        data (r/read-codex (:tempfile file))]
    (reset! state (merge @state data)))
  (services))

;; sims
;;;;;;;;;;;

(defn sims-names [] (assoc json :body (co/js (sort (d/custom-keys @sims)))))

(defn del-sim [svc]
  (reset! state (ib/dissoc-in @sims [svc]))
  {:status 204})

(def del-sim-handled (handler del-sim handle-error))

(defn load-sim [f]
  (let [file-content (m/load-script (.getPath f))]
    (reset! sims (merge @sims file-content))))

(defn put-sims [req]
  (let [file ((:params req) "file")]
    (load-sim (:tempfile file)))
  (sims-names))


;; services documentation
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn services-docs []
  (txdocs/services-template (sort (d/custom-keys @state))))

(defn- html [f] (str (c/html-dir) f))

(l/defdocument service-index
  (file (html "/index.html")) []
  (l/id="project-version") (<- (txdocs/version)))

(l/defdocument service-api
  (file (html "/api.html")) []
  (l/id="project-version") (<- (txdocs/version))
  (l/id="hostname") (<- c/host)
  (l/id="admin-port") (<- (c/admin-port)))

(l/defdocument service-documentation
  (file (html "/documentation.html")) []
  (l/id="project-version") (<- (txdocs/version)))

(l/defdocument service-road
  (file (html "/roadmap.html")) []
  (l/id="project-version") (<- (txdocs/version)))

(l/defdocument service-community
  (file (html "/community.html")) []
  (l/id="project-version") (<- (txdocs/version)))


;; service status
;;;;;;;;;;;;;;;;;

(defn status [] (assoc json :body (co/js {"status" "ok"})))
