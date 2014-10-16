(ns protean.core.transformation.test-payload
  (:require [protean.core.io.data :as d])
  (:use [clojure.test]
        [protean.core.transformation.payload :only (build-payload)]))

(defn- payload-item [locs filename]
  (let [corpus {:locs [locs]}]
    (first (build-payload "localhost" 8080 (d/read-edn filename) corpus))))

(deftest sim-payload
  (let [p (payload-item "sim path1" "sim.edn")]
    (is (= (:uri p) "http://localhost:8080/sim/path1"))))

(deftest sim-wildcard-payload
  (let [p (payload-item "sim wild/*" "sim.edn")]
    (is (= (:uri p) "http://localhost:8080/sim/wild/psv+"))))

(deftest sim-full-reqparams-payload
  (let [p (payload-item "sim full/query-params" "sim.edn")]
    (is (= (count (get-in p [:options :headers])) 1))
    (is (= (count (get-in p [:options :query-params])) 1))))

(deftest sim-full-formparams-payload
  (let [p (payload-item "sim full/form-params" "sim.edn")]
    ;; we expect two headers as we enforce request headers in the sim
    ;; post/put etc therefore calculate a request header
    (is (= (count (get-in p [:options :headers])) 2))
    (is (= (count (get-in p [:options :form-params])) 1))))
