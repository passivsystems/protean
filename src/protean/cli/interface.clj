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
(def svc-errs "service-errors")
(def add-svc-err "add-service-error")
(def set-svc-err-prob "set-service-error-prob")
(def del-svc "del-service")
(def del-svc-errs "del-service-errors")
(def doc "doc")
(def visit "visit")

;; =============================================================================
;; Interface args verification functions
;; =============================================================================

(defn add-svc-err? [{:keys [name status-err]}] (or (not name) (not status-err)))

(defn set-svc-err-prob? [{:keys [name level]}] (or (not name) (not level)))

(defn doc? [{:keys [name file directory]}]
  (or (not name) (not file) (not directory)))

(defn visit? [{:keys [file body]}] (or (not file) (not body)))
