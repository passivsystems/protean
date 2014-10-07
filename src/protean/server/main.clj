(ns protean.server.main
  "Entry point into the server component.  Config, server and routes."
  (:require [clojure.edn :as edn]
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
            [protean.server.docs :as pdoc])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.File java.net.InetAddress)
  (:gen-class))

;; =============================================================================
;; Helper functions
;; =============================================================================

(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename] "protean.log")
(timbre/set-level! :info)

(defonce host (.getCanonicalHostName (InetAddress/getLocalHost)))

(defn files [c-dir]
  (-> (remove #(.isDirectory %) (file-seq (file c-dir)))
      (do/filter-exts ["edn"])))

(defn- build-services
  "Load services from disk."
  [c-dir]
  (let [fs (remove #(= (.getName (.getParentFile %)) "data") (files c-dir))]
    (doseq [f fs]
      (reset! pipe/state (merge @pipe/state (edn/read-string (slurp f))))))
  (keys @pipe/state))


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
  (GET    "/services/:id/errors" [id] (pipe/service-errors id))
  (DELETE "/services/:id/errors" [id] (pipe/delete-proj-errors id))
  (PUT    "/services/:id/errors/status/:err" [id err]
    (pipe/put-proj-error id err))
  (PUT    "/services/:id/errors/probability/:prob" [id prob]
    (pipe/put-proj-error-prob id prob))
  (GET    "/services" [] (pipe/services))
  (GET    "/services/:id" [id] (pipe/service id))
  (GET    "/services/:id/usage" [id] (pipe/service-usage id host))
  (mp/wrap-multipart-params (PUT    "/services" req (pipe/put-services req)))
  (DELETE "/services/:id" [id] (pipe/del-proj-handled id))
  (GET    "/status" [] (pipe/status)))

(defroutes api-routes
  (ANY "*" req (pipe/api req)))


;; =============================================================================
;; Server setup
;; =============================================================================

(defn server [api-port admin-port]
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
    (server (co/int api-port) (co/int (c/admin-port)))
    (info (str "Protean has started"))))
