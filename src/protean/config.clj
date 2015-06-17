(ns protean.config
  (:require [environ.core :refer [env]]
            [me.rossputin.diskops :as d]))

;; =============================================================================
;; Configuration
;; =============================================================================

(defn os [] (System/getProperty "os.name"))

(defonce host (or (env :hostname) "localhost"))

(defn sim-port [] (or (env :protean-sim-port) "3000"))

(defn admin-port [] (or (env :protean-admin-port) "3001"))

(defn codex-dir [] (or (env :protean-codex-dir) (d/pwd)))

(defn log-dir [] (or (env :protean-log-dir) (d/pwd)))

(defn log-level [] (keyword (or (env :protean-log-level) "info")))

(defn target-dir [] (or (env :protean-target) "target"))

(defn curl-option[] (or (env :protean-curl-option) "-i"))
