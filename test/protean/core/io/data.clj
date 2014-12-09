(ns protean.core.io.data
  (require [protean.core.codex.reader :as r]))

;; =============================================================================
;; Sample data helper functions
;; =============================================================================

(defn read-edn [f] (r/read-codex (str "test-data/" f)))
