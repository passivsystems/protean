(ns protean.config
  (:require [environ.core :refer [env]])
  (:require [me.rossputin.diskops :as d]))

;; =============================================================================
;; Configuration
;; =============================================================================

(defonce host (or (env :hostname) (.getCanonicalHostName (java.net.InetAddress/getLocalHost))))

(defn sim-port [] (or (env :sim-port) "3000"))

(defn admin-port [] (or (env :admin-port) "3001"))

(defn codex-dir [] (or (env :codex-dir) (d/pwd)))

(defn asset-dir [] (or (env :asset-dir) "public"))

(defn res-dir [] (str (asset-dir) "/resource"))

(defn html-dir [] (str (asset-dir) "/html"))
