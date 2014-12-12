(ns protean.core.io.data
  (require [protean.core.codex.reader :as r]
           [clojure.java.io :refer [file]]))

;; =============================================================================
;; Sample data helper functions
;; =============================================================================

(defn read-edn [f] (r/read-codex (file (str "test-data/" f))))
