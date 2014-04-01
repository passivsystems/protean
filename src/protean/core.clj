(ns protean.core
  "Entry point into the app.  Config, server and routes."
  (:require [clojure.edn :as edn]
            [clojure.java.io :refer [file]]
            [compojure.core :refer [defroutes ANY DELETE GET POST PUT]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [me.rossputin.diskops :as do]
            [protean.pipeline :as pipe]
            [protean.transformations.coerce :as txco])
  (:use ring.adapter.jetty
        [ring.middleware.multipart-params]
        [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.File)
  (:gen-class))

;; =============================================================================
;; Helper functions
;; =============================================================================

(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename] "protean.log")

(def port (atom 3000))

(defonce pwd (. (file ".") getCanonicalPath))

(defonce fs (File/separator))

(defn proj-files []
  (-> (remove #(.isDirectory %) (file-seq (file pwd)))
      (do/filter-exts ["edn"])))

(defn- build-projects []
  "Load projects from disk."
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
  (GET    "/documentation/projects/:id" [id] (pipe/project-docs id @port))
  (GET    "/documentation/projects" [] (pipe/projects-docs))
  (GET    "/documentation" [] (pipe/project-documentation))
  (GET    "/roadmap" [] (pipe/project-road))
  (DELETE "/projects/:id/errors" [id] (pipe/delete-proj-errors id))
  (PUT    "/projects/:id/errors/status/:err" [id err]
    (pipe/put-proj-error id err))
  (PUT    "/projects/:id/errors/probability/:prob" [id prob]
    (pipe/put-proj-error-prob id prob))
  (GET    "/projects" [] (pipe/projects))
  (GET    "/projects/:id" [id] (pipe/project id))
  (GET    "/projects/:id/usage" [id] (pipe/project-usage id @port))
  (wrap-multipart-params (PUT    "/projects" req (pipe/put-projects req)))
  (DELETE "/projects/:id" [id] (pipe/del-proj-handled id))
  (GET    "/status" [] (pipe/status)))

(defroutes api-routes
  (ANY "*" req (pipe/api req)))


;; =============================================================================
;; Server setup
;; =============================================================================

(defn server [api-port admin-port]
  (run-jetty admin-routes {:port admin-port :join? false})
  (run-jetty (-> api-routes handler/api) {:port api-port :join? false}))


;; =============================================================================
;; Application entry point
;; =============================================================================

(defn -main [& args]
  (let [api-port (or (first args) "3000")
        admin-port (or (second args) "3001")
        projects (build-projects)]
    (info "Starting protean")
    (reset! port api-port)
    (info (str "Projects loaded : " projects))
    (server (txco/int-> api-port) (txco/int-> admin-port))
    (info (str "Protean has started"))))
