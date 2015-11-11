(ns protean.server.main
  "Entry point into the server component.  Config, server and routes."
  (:require [clojure.edn :as edn]
            [clojure.main :as m]
            [clojure.java.io :refer [file]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.multipart-params :as mp]
            [compojure.core :refer [defroutes ANY DELETE GET POST PUT]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [cemerick.pomegranate :as pom]
            [me.rossputin.diskops :as do]
            [protean.config :as c]
            [protean.server.pipeline :as pipe]
            [protean.core.transformation.coerce :as co]
            [protean.core.codex.reader :as r]
            [protean.core.codex.document :as d]
            [clojure.pprint]
            [taoensso.timbre.appenders.core :as appenders])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.File)
  (:gen-class))

;; =============================================================================
;; Helper functions
;; =============================================================================

(timbre/merge-config! {:timestamp-opts {:pattern "yyyy-MM-dd HH:mm:ss.SSS"}})
(timbre/merge-config! {:appenders {:spit (appenders/spit-appender {:fname
  (str (c/log-dir) "/protean2.log")})}})
(timbre/set-level! (c/log-level))

(defn- files [c-dir ext]
  (-> (remove #(.isDirectory %) (.listFiles (file c-dir)))
      (do/filter-exts [ext])))

(defn- build-services
  "Load services from disk."
  [c-dir]
  (let [codex-fs (files c-dir "cod.edn")]
    (doseq [f codex-fs]
      (pipe/load-codex f)))
  (d/custom-keys @pipe/state))

(defn- build-sims
  "Load sims from disk."
  [c-dir]
  (let [sim-fs (files c-dir "sim.edn")]
    (doseq [f sim-fs]
      (pipe/load-sim f)))
  (d/custom-keys @pipe/sims))


;; =============================================================================
;; Routes
;; =============================================================================

(defroutes admin-routes
  (GET    "/services" [] (pipe/services))
  (GET    "/services/:id" [id] (pipe/service id))
  (GET    "/services/:id/usage" [id] (pipe/service-usage id c/host))
  (PUT    "/services" req (pipe/put-services req))
  (DELETE "/services/:id" [id] (pipe/del-service-handled id))
  (GET    "/sims" [] (pipe/sims-names))
  (PUT    "/sims" req (pipe/put-sims req))
  (DELETE "/sims/:id" [id] (pipe/del-sim-handled id))
  (GET    "/status" [] (pipe/status)))

(defroutes api-routes
  (ANY "*" req (pipe/api req)))


;; =============================================================================
;; Server setup
;; =============================================================================

(defn- server [sim-port sim-max-threads admin-port admin-max-threads]
  (jetty/run-jetty
    (-> admin-routes mp/wrap-multipart-params)
    { :port (co/int admin-port)
      :join? false
      :max-threads (co/int admin-max-threads)})
  (jetty/run-jetty
    (-> api-routes handler/api mp/wrap-multipart-params)
    { :port (co/int sim-port)
      :join? false
      :max-threads (co/int sim-max-threads)}))


;; =============================================================================
;; Application entry point
;; =============================================================================

(defmacro version [] (System/getProperty "protean.version"))

(defn start [codex-dir]
  (timbre/log-and-rethrow-errors
    (let [sim-port (c/sim-port)
          sim-max-threads (c/sim-max-threads)
          admin-port (c/admin-port)
          admin-max-threads (c/admin-max-threads)
          c-dir (or codex-dir (c/codex-dir))]
      (info "Starting protean - v" (version))
      (info "Codex directory : " c-dir)

      ;; configure classpath for this instance of protean
      ;; we currently support local clj artefacts and remote coords (e.g. clojars)
      ;; TODO: support local jar files in a directory
      (pom/add-classpath c-dir)
      (pom/add-classpath (str (file c-dir "clj")))

      (info (str "Codices loaded : " (build-services c-dir)))
      (info (str "Sim extensions loaded : " (build-sims c-dir)))
      (server sim-port sim-max-threads admin-port admin-max-threads)
      (info (str "Protean has started \n"
                 "Sim Port:   " sim-port   " Max threads: " sim-max-threads "\n"
                 "Admin Port: " admin-port " Max threads: " admin-max-threads)))))

(defn -main [& args] (start nil))
