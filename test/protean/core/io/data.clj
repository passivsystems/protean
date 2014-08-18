(ns protean.core.io.data
  (require [clojure.edn :as edn]))

;; =============================================================================
;; Sample data helper functions
;; =============================================================================

(defn read-edn [f] (edn/read-string (slurp (str "data/" f))))
