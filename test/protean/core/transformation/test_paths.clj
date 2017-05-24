(ns protean.core.transformation.test-paths
  (:require [clojure.pprint]
            [clojure.java.io :refer [file]]
            [protean.api.codex.reader :as r]
            [protean.core.transformation.paths :refer [paths]]
            [expectations :refer :all]))

(defn read-edn [f] (r/read-codex (file (str "test-data/" f))))

;; =============================================================================
;; Testing computation of endpoints from codex
;; =============================================================================

(let [paths (paths (read-edn "get-codex-root.edn") ["sample"])]
  (expect ["sample" "sample"] (map #(:svc %) paths))
  (expect ["/" "test"] (map #(:path %) paths)))

(let [paths (paths (read-edn "get-codex-paths.edn") ["sample simple test"])]
  (expect ["sample" "sample"] (map #(:svc %) paths))
  (expect ["simple" "test"] (map #(:path %) paths)))

(let [paths (paths (read-edn "get-codex-paths.edn") ["sample"])]
  (expect ["sample" "sample"] (map #(:svc %) paths))
  (expect #{"simple" "test"} (set (map #(:path %) paths))))

(let [paths (paths (read-edn "get-codex.edn") ["sample simple test"])
      p (first paths)]
  (expect "sample" (:svc p))
  (expect "simple" (:path p)))

(let [paths (paths (read-edn "get-codex.edn") ["sample"])
      p (first paths)]
  (expect "sample" (:svc p))
  (expect "simple" (:path p)))

(let [paths (paths (read-edn "multi-method.edn") ["sample homes/1"])]
  (expect ["sample" "sample" "sample"] (map #(:svc %) paths))
  (expect #{"homes/1"} (set (map #(:path %) paths)))
  (expect #{:get :put :delete} (set (map #(:method %) paths))))
