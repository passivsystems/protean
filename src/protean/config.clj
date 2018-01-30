(ns protean.config
  (:require [clojure.string :as s]
            [environ.core :refer [env]]
            [me.rossputin.diskops :as d]))

;; =============================================================================
;; Configuration
;; =============================================================================

(defn os [] (System/getProperty "os.name"))

(defonce host (or (env :hostname) "localhost"))

(defn sim-port [] (or (env :protean-sim-port) "3000"))

(defn sim-max-threads [] (or (env :protean-sim-max-threads) "1000"))

(defn admin-port [] (or (env :protean-admin-port) "3001"))

(defn admin-max-threads [] (or (env :protean-admin-max-threads) "50"))

(defn codex-dir [] (or (env :protean-codex-dir) (d/pwd)))

(defn public-dir [] (or (env :protean-asset-dir) "public"))

(defn protean-home [] (or (env :protean-home) (d/pwd)))

(defn log-dir [] (or (env :protean-log-dir) (d/pwd)))

(defn log-level [] (keyword (or (env :protean-log-level) "info")))

(defn target-dir [] (or (env :protean-target) "target"))

(defn curl-option [] (or (env :protean-curl-option) "-i"))

(defn- to-bool [str] (some #{(s/lower-case str)} ["on" "true" "yes" "y"]))

(defn curl-flatten? [] (to-bool (or (env :protean-curl-flatten) "on")))

(defn admin-server? [] (to-bool (or (env :protean-admin-server) "true")))

(defn sim-server? [] (to-bool (or (env :protean-sim-server) "true")))
