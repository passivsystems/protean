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
            [me.rossputin.diskops :as do]
            [protean.config :as c]
            [protean.server.pipeline :as pipe]
            [protean.core.transformation.coerce :as co]
            [protean.server.docs :as pdoc]
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
(timbre/set-config! [:shared-appender-config :spit-filename] "protean.log")
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
  (let [fs (files c-dir "sim.edn")
        f (first (filter #(= (.getName %) "protean-utils.sim.edn") fs))
        sim-fs (conj (remove #(= % f) fs) f)]
    (doseq [f sim-fs]
      (pipe/load-sim f)))
  (d/custom-keys @pipe/sims))


;; =============================================================================
;; Routes
;; =============================================================================

(defroutes admin-routes
  (route/files "/resource" {:root (c/res-dir)})

  (GET    "/" [] (pipe/service-index))
  (GET    "/documentation/api" [] (pipe/service-api))
  (GET    "/documentation/services" [] (pipe/services-docs))
  (GET    "/documentation" [] (pipe/service-documentation))
  (GET    "/roadmap" [] (pipe/service-road))
  (GET    "/community" [] (pipe/service-community))
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

(defn -main [& args]
  (let [api-port (c/sim-port)
        c-dir (c/codex-dir)]
    (info "Starting protean - v" (pdoc/version))
    (info "Codex directory : " c-dir)
    (info "Asset directory : " (c/asset-dir))
    (info (str "Services loaded : " (build-services c-dir)))
    (info (str "Sims loaded : " (build-sims c-dir)))
    (server (co/int api-port) (co/int (c/admin-port)))
    (info (str "Protean has started"
      " : api-port " api-port ", admin-port " (c/admin-port)))))
