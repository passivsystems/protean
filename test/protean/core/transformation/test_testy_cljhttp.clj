; (ns protean.core.transformation.test-testy-cljhttp
;   (:require [protean.core.io.data :as d])
;   (:use [clojure.test]
;         [protean.core.transformation.testy-cljhttp :only (clj-httpify)]))
;
; (defn- cljhttp-item [locs filename]
;   (let [corpus {:locs [locs]}]
;     (first (clj-httpify "localhost" 8080 (d/read-edn filename) corpus))))
;
; (deftest sim-payload
;   (let [p (cljhttp-item "sim path1" "sim.edn")]
;     (is (= (first p) 'client/get))))
;
; (deftest sim-wildcard-payload
;   (let [p (cljhttp-item "sim wild/*" "sim.edn")]
;     (is (= (first p) 'client/get))))
;
; (deftest sim-full-queryparams-payload
;   "We always pass through a codex item even if its contents are nil values."
;   (let [p (cljhttp-item "sim full/query-params" "sim.edn")]
;     (is (= (count (last p)) 5))))
;
; (deftest sim-full-formparams-payload
;   "We always pass through a codex item even if its contents are nil values."
;   (let [p (cljhttp-item "sim full/form-params" "sim.edn")]
;     (is (= (count (last p)) 5))))
