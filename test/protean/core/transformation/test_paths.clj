(ns protean.core.transformation.test-paths
  (:require [protean.core.io.data :as d])
  (:use [clojure.test]
        [protean.core.transformation.paths :only (paths->)]))

(deftest combi-paths
  (let [paths (paths-> (d/read-edn "get-codex-paths.edn") ["sample simple test"])
        p (first paths)]
    (is (= 2 (count paths)))
    (is (= :sample (:svc p)))
    (is (= "simple" (:path p)))))

(deftest svc-paths
  (let [paths (paths-> (d/read-edn "get-codex-paths.edn") ["sample"])
        p (first paths)]
    (is (= 2 (count paths)))
    (is (= :sample (:svc p)))
    (is (= "simple" (:path p)))))

(deftest simple-combi-path
  (let [paths (paths-> (d/read-edn "get-codex.edn") ["sample simple test"])
        p (first paths)]
    (is (= 1 (count paths)))
    (is (= :sample (:svc p)))
    (is (= "simple" (:path p)))))

(deftest simple-svc-path
  (let [paths (paths-> (d/read-edn "get-codex.edn") ["sample"])
        p (first paths)]
    (is (= 1 (count paths)))
    (is (= :sample (:svc p)))
    (is (= "simple" (:path p)))))

(deftest multimethod
  (let [paths (paths-> (d/read-edn "multi-method.edn") ["sample homes/1"])
        p (first paths)]
    (is (= 3 (count paths)))
    (is (= :sample (:svc p)))
    (is (= "homes/1" (:path p)))))
