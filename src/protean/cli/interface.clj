(ns protean.cli.interface
  "Utilities and functions for the command line interface.")

;; =============================================================================
;; Constants
;; =============================================================================

;; commands

(def svc "service")
(def svc-usg "service-usage")
(def svcs "services")
(def add-svcs "add-services")
(def del-svc "del-service")
(def sims "sims")
(def add-sims "add-sims")
(def del-sim "del-sim")
(def doc "doc")
(def visit "visit")

;; =============================================================================
;; Interface args verification functions
;; =============================================================================

(defn doc? [{:keys [name file directory]}]
  (or (not name) (not file) (not directory)))

(defn visit? [{:keys [file body]}] (or (not file) (not body)))
