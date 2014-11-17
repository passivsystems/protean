(ns protean.core.transformation.test-paths
  (:require [protean.core.io.data :as d] [clojure.pprint])
  (:use [clojure.test]
        [protean.core.transformation.paths :only (paths->)]))

(deftest combi-paths
  (let [paths (paths-> (d/read-edn "get-codex-paths.edn") ["sample simple test"])]
    (is (= 2 (count paths)))
    (is (= ["sample" "sample"] (map #(:svc %) paths)))
    (is (= ["simple" "test"] (map #(:path %) paths)))))

(deftest svc-paths
  (let [paths (paths-> (d/read-edn "get-codex-paths.edn") ["sample"])]
    (is (= 2 (count paths)))
    (is (= ["sample" "sample"] (map #(:svc %) paths)))
    (is (= #{"simple" "test"} (set (map #(:path %) paths))))))

(deftest simple-combi-path
  (let [paths (paths-> (d/read-edn "get-codex.edn") ["sample simple test"])
        p (first paths)]
    (is (= 1 (count paths)))
    (is (= "sample" (:svc p)))
    (is (= "simple" (:path p)))))

(deftest simple-svc-path
  (let [paths (paths-> (d/read-edn "get-codex.edn") ["sample"])
        p (first paths)]
    (is (= 1 (count paths)))
    (is (= "sample" (:svc p)))
    (is (= "simple" (:path p)))))

(deftest multimethod
  (let [paths (paths-> (d/read-edn "multi-method.edn") ["sample homes/1"])]
    (is (= 3 (count paths)))
    (is (= ["sample" "sample" "sample"] (map #(:svc %) paths)))
    (is (= #{"homes/1"} (set (map #(:path %) paths))))
    (is (= #{:get :put :delete} (set (map #(:method %) paths))))))

