(ns protean.core.transformation.test-analysis
  (:require [protean.core.io.data :as d])
  (:use [clojure.test]
        [protean.core.transformation.analysis :only (analysis->)]))

(deftest simple-analysis
  (let [corpus {:locs ["sample simple"]}
        aSis (analysis-> "localhost" 8080 (d/read-edn "get-codex.edn") corpus)
        an (first aSis)]
    (is (= :get (:method an)))
    (is (= "http://localhost:8080/sample/simple" (:uri an)))
    (is (= nil (get-in an [:codex :body-res])))
    (is (= nil (get-in an [:codex :success-code])))
    (is (= nil (get-in an [:codex :content-type])))))

(deftest simulation-analysis
  (let [corpus {:locs ["sim path1"]}
        aSis (analysis-> "localhost" 8080 (d/read-edn "sim.edn") corpus)
        an (first aSis)]
    (is (= :get (:method an)))
    (is (= "http://localhost:8080/sim/path1" (:uri an)))
    (is (= (count (get-in an [:codex :body])) 2))
    (is (= nil (get-in an [:codex :body-res])))
    (is (= nil (get-in an [:codex :success-code])))
    (is (= nil (get-in an [:codex :content-type])))))
