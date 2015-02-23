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
            [environ.core :refer [env]]
            [cemerick.pomegranate :as pom]
            [me.rossputin.diskops :as do]
            [protean.config :as c]
            [protean.server.pipeline :as pipe]
            [protean.core.transformation.coerce :as co]
            [protean.core.codex.reader :as r]
            [protean.core.codex.document :as d]
            [clojure.pprint])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.File)
  (:gen-class))

;; =============================================================================
;; Helper functions
;; =============================================================================

(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename]
  (str (c/log-dir) "/protean.log"))
(timbre/set-level! (c/log-level))

;; configure classpath for this instance of protean
;; we currently support local clj artefacts and remote coords (e.g. clojars)
;; TODO: support local jar files in a directory
(pom/add-classpath (c/codex-dir))
(pom/add-classpath (str (file (c/codex-dir) "clj")))

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
  (mp/wrap-multipart-params (PUT    "/services" req (pipe/put-services req)))
  (DELETE "/services/:id" [id] (pipe/del-service-handled id))
  (GET    "/sims" [] (pipe/sims-names))
  (mp/wrap-multipart-params (PUT    "/sims" req (pipe/put-sims req))) ; TODO only first mp/wrap-multipart-params wrapped route works with multi-part...
  (DELETE "/sims/:id" [id] (pipe/del-sim-handled id))
  (GET    "/status" [] (pipe/status)))

(defroutes api-routes
  (mp/wrap-multipart-params (ANY "*" req (pipe/api req))))


;; =============================================================================
;; Server setup
;; =============================================================================

(defn- server [api-port admin-port]
  (jetty/run-jetty admin-routes {:port admin-port :join? false})
  (jetty/run-jetty (-> api-routes handler/api) {:port api-port :join? false}))


;; =============================================================================
;; Application entry point
;; =============================================================================

(defmacro version [] (System/getProperty "protean.version"))

(defn -main [& args]
  (let [api-port (c/sim-port)
        c-dir (c/codex-dir)]
    (info "Starting protean - v" (version))
    (info "Codex directory : " c-dir)
    (info (str "Services loaded : " (build-services c-dir)))
    (info (str "Sims loaded : " (build-sims c-dir)))
    (server (co/int api-port) (co/int (c/admin-port)))
    (info (str "Protean has started"
      " : api-port " api-port ", admin-port " (c/admin-port)))))
