(ns protean.core.transformation.test-paths
  (:require [protean.core.io.data :as d] [clojure.pprint]
            [protean.core.transformation.paths :refer [paths]]
            [expectations :refer :all]))

;; =============================================================================
;; Testing computation of endpoints from codex
;; =============================================================================

(let [paths (paths (d/read-edn "get-codex-paths.edn") ["sample simple test"])]
  (expect 2 (count paths))
  (expect ["sample" "sample"] (map #(:svc %) paths))
  (expect ["simple" "test"] (map #(:path %) paths)))

(let [paths (paths (d/read-edn "get-codex-paths.edn") ["sample"])]
  (expect 2 (count paths))
  (expect ["sample" "sample"] (map #(:svc %) paths))
  (expect #{"simple" "test"} (set (map #(:path %) paths))))

(let [paths (paths (d/read-edn "get-codex.edn") ["sample simple test"])
      p (first paths)]
  (expect 1 (count paths))
  (expect "sample" (:svc p))
  (expect "simple" (:path p)))

(let [paths (paths (d/read-edn "get-codex.edn") ["sample"])
      p (first paths)]
  (expect 1 (count paths))
  (expect "sample" (:svc p))
  (expect "simple" (:path p)))

(let [paths (paths (d/read-edn "multi-method.edn") ["sample homes/1"])]
  (expect 3 (count paths))
  (expect ["sample" "sample" "sample"] (map #(:svc %) paths))
  (expect #{"homes/1"} (set (map #(:path %) paths)))
  (expect #{:get :put :delete} (set (map #(:method %) paths))))
