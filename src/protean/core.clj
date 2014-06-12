(ns protean.core
  "Entry point into the app.  Config, server and routes."
  (:require [clojure.edn :as edn]
            [clojure.java.io :refer [file]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.multipart-params :as mp]
            [compojure.core :refer [defroutes ANY DELETE GET POST PUT]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [me.rossputin.diskops :as do]
            [protean.pipeline :as pipe]
            [protean.transformations.coerce :as txco])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.File java.net.InetAddress)
  (:gen-class))

;; =============================================================================
;; Helper functions
;; =============================================================================

(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename] "protean.log")

(defonce host (.getCanonicalHostName (InetAddress/getLocalHost)))

(def port (atom 3000))

(defn proj-files []
  (-> (remove #(.isDirectory %) (file-seq (file (do/pwd))))
      (do/filter-exts ["edn"])))

(defn- build-projects
  "Load services from disk."
  []
  (let [files (proj-files)]
    (doseq [f files]
      (reset! pipe/state (merge @pipe/state (edn/read-string (slurp f))))))
  (keys @pipe/state))


;; =============================================================================
;; Routes
;; =============================================================================

(defroutes admin-routes
  (route/files "/resource" {:root "public/resource"})

  (GET    "/" [] (pipe/project-index))
  (GET    "/documentation/api" [] (pipe/project-api))
  (GET    "/documentation/services/:id" [id] (pipe/project-docs id host @port))
  (GET    "/documentation/services" [] (pipe/projects-docs))
  (GET    "/documentation" [] (pipe/project-documentation))
  (GET    "/roadmap" [] (pipe/project-road))
  (DELETE "/services/:id/errors" [id] (pipe/delete-proj-errors id))
  (PUT    "/services/:id/errors/status/:err" [id err]
    (pipe/put-proj-error id err))
  (PUT    "/services/:id/errors/probability/:prob" [id prob]
    (pipe/put-proj-error-prob id prob))
  (GET    "/services" [] (pipe/projects))
  (GET    "/services/:id" [id] (pipe/project id))
  (GET    "/services/:id/usage" [id] (pipe/project-usage id host @port))
  (mp/wrap-multipart-params (PUT    "/services" req (pipe/put-projects req)))
  (DELETE "/services/:id" [id] (pipe/del-proj-handled id))
  (POST   "/test" req (pipe/test! req host @port))
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
  (let [api-port (or (first args) "3000")
        admin-port (or (second args) "3001")
        projects (build-projects)]
    (info "Starting protean")
    (reset! port api-port)
    (info (str "Services loaded : " projects))
    (server (txco/int-> api-port) (txco/int-> admin-port))
    (info (str "Protean has started"))))
