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
            [protean.core.transformation.sim :as sim]
            [protean.core.transformation.coerce :as co]
            [protean.core.transformation.curly :as txc]
            [protean.core.codex.reader :as r]
            [protean.core.transformation.paths :as p]
            [clojure.pprint])
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
(def paths (atom {}))
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
  (sim/sim-rsp req @paths @sims))


;; =============================================================================
;; Admin pipelines
;; =============================================================================

;; services
;;;;;;;;;;;

(defn services [] (assoc json :body (co/js (sort (keys @paths)))))

; TODO the result of this function is currently affected by collisions between codices - should use @paths instead
(defn service [svc] (assoc json :body (co/js (get-in @state [svc]))))

(defn service-usage [svc host]
  (let [port (c/sim-port)
        to-map (fn [path]
            (let [endpoint (key path)
                  methods (keys (val path))]
             (for [method methods]
               {:method method
                 :uri (p/uri host port svc endpoint)
                 :tree (get-in @paths [svc endpoint method])})))
        analysed (mapcat to-map (get-in @paths [svc]))]
    (assoc json :body (co/js (txc/curly-analysis-> analysed)))))

(defn del-service [svc]
  (reset! paths (ib/dissoc-in @paths [svc]))
  {:status 204})

(def del-service-handled (handler del-service handle-error))

(defn load-codex [f]
  (let [codex (r/read-codex f)
        locs (d/custom-keys codex)
        tpaths (p/paths codex locs)]
    (doseq [path tpaths]
      (swap! paths assoc-in [(:svc path) (:path path) (:method path)] (:tree path)))
    (reset! state (merge @state codex))))

(defn put-services [req]
  (let [file ((:params req) "file")
        data (r/read-codex (:tempfile file))]
    (load-codex (:tempfile file)))
  (services))

;; sims
;;;;;;;;;;;

(defn sims-names [] (assoc json :body (co/js (sort (d/custom-keys @sims)))))

(defn del-sim [svc]
  (reset! sims (ib/dissoc-in @sims [svc]))
  {:status 204})

(def del-sim-handled (handler del-sim handle-error))

(defn load-sim [f]
  (let [file-content (m/load-script (.getPath f))]
    (reset! sims (merge @sims file-content))))

(defn put-sims [req]
  (let [file ((:params req) "file")]
    (load-sim (:tempfile file)))
  (sims-names))


;; service status
;;;;;;;;;;;;;;;;;

(defn status [] (assoc json :body (co/js {"status" "ok"})))
