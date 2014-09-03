(ns protean.core.transformation.test-sim
  (:require [protean.core.protocol.http :as h]
            [protean.core.io.data :as d])
  (:use [clojure.test]
        [protean.core.transformation.sim :only (sim-rsp->)]))

(defn request [m u c b f]
  {
    :ssl-client-cert nil
    :remote-addr "127.0.0.1"
    :params {:* u}
    :route-params {:* u}
    :headers {"user-agent" "curl/7.29.0"
              "content-type" c
              "accept" "*/*"
              "host" "localhost:3000"}
    :server-port 3000
    :content-length nil
    :form-params (or f {})
    :query-params {}
    :content-type nil
    :character-encoding nil
    :uri u
    :server-name "localhost"
    :query-string nil
    :body b
    :scheme :http
    :request-method m
  })

(def body (.getBytes "" "UTF-8"))
(defn- get-req [p] (request :get p h/txt body nil))

(deftest get-rsp-200
  (let [req (get-req "/sample/simple")
        cdx (d/read-edn "get-codex.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 200))))

(deftest get-rsp-500
  (let [req (get-req "/sample/simple")
        cdx (d/read-edn "get-codex-500.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 500))))

(deftest get-wildcard-simple-200
  (let [req (get-req "/sample/simple/1")
        cdx (d/read-edn "get-wildcard.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 200))))

(deftest put-wildcard-complex-200
  (let [req (request :put "/sample/v/1/users/1/items/2/status" h/txt body nil)
        cdx (d/read-edn "get-wildcard-complex.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 200))))

(deftest get-wildcard-complex-200
  (let [req (get-req "/sample/v/1/users/1/items/2")
        cdx (d/read-edn "get-wildcard-complex.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 200))))

(deftest post-wildcard-complex-201
  (let [req (request :post "/sample/v/1/users/1/items" h/txt body nil)
        cdx (d/read-edn "get-wildcard-complex.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 201))))

(deftest post-wildcard-crazy-201
  (let [req (request :post "/sample/v/1/users/1/items/1/assets" h/txt body nil)
        cdx (d/read-edn "wildcard-crazy.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 201))))

; should fail verification (yield 400) on json req body
(deftest post-rsp-jsn-body-400
  (let [req (request :post "/sample/simple" h/jsn (.getBytes "{\"k1\":\"v1\"}" "UTF-8") nil)
        cdx (d/read-edn "post-codex-400.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 400))))

(deftest multimethod-get
  (let [req (get-req "/sample/homes/1")
        cdx (d/read-edn "multi-method.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 200))))

(deftest multimethod-put
  (let [req (request :put "/sample/homes/1" h/frm body {})
        cdx (d/read-edn "multi-method.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 204))))

(deftest multimethod-head
  (let [req (request :head "/sample/homes/1" h/txt body nil)
        cdx (d/read-edn "multi-method.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 405))))

(deftest status-override
  (let [req (get-req "/sim/status-override")
        cdx (d/read-edn "sim.edn")
        rsp (sim-rsp-> req cdx)]
    (is (= (:status rsp) 204))))
