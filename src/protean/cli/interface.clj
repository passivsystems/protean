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
(def int-test "test")
(def sim "sim")
(def build "build")
(def visit "visit")

;; =============================================================================
;; Interface args verification functions
;; =============================================================================

(defn doc? [{:keys [file]}] (not file))

(defn int-test? [{:keys [file]}] (not file))

(defn sim? [{:keys [directory]}] (not directory))

(defn build? [{:keys [directory]}] (not directory))

(defn visit? [{:keys [file body]}] (or (not file) (not body)))
