(ns protean.core.transformation.test-sim
  (:require [protean.core.protocol.http :as h]
            [protean.core.io.data :as d]
            [protean.core.transformation.coerce :as c]
            [protean.core.transformation.sim :as s]
            [expectations :refer :all]
            [taoensso.timbre :as l]))

(l/set-level! :warn)

(defn- req [m u c b f]
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

;; =============================================================================
;; Simple methods statuses and headers
;; =============================================================================

(def cdx-1 {
  "sample" {
    "simple" {
      :get [{:rsp {:200 {}}}]
      :head [{:rsp {:200 {:headers {"token" "aGVsbG8gc2FpbG9y"}}}}]
      :put [{:rsp {:204 {}}}]
      :post [{:rsp {:201 {:headers {"Location" "over here"}}}}]
      :delete [{:rsp {:204 {}}}]
      :patch [{:rsp {:204 {}}}]
    }
  }
})

(let [rsp-1 (s/sim-rsp (req :get "/sample/simple" h/txt body nil) cdx-1 {})
      rsp-2 (s/sim-rsp (req :head "/sample/simple" nil body nil) cdx-1 {})
      rsp-3 (s/sim-rsp (req :put "/sample/simple" nil body nil) cdx-1 {})
      rsp-4 (s/sim-rsp (req :post "/sample/simple" nil body nil) cdx-1 {})
      rsp-5 (s/sim-rsp (req :delete "/sample/simple" nil body nil) cdx-1 {})
      rsp-6 (s/sim-rsp (req :patch "/sample/simple" nil body nil) cdx-1 {})
      rsp-7 (s/sim-rsp (req :get "/sample/404" nil body nil) cdx-1 {})
      rsp-8 (s/sim-rsp (req :muppet "/sample/simple" nil body nil) cdx-1 {})]
  (expect (:status rsp-1) 200)
  (expect (:status rsp-2) 200)
  (expect (count (:headers rsp-2)) 1)
  (expect (:status rsp-3) 204)
  (expect (:status rsp-4) 201)
  (expect (count (:headers rsp-4)) 1)
  (expect (:status rsp-5) 204)
  (expect (:status rsp-6) 204)
  (expect (:status rsp-7) 404)
  (expect (contains? (:headers rsp-7) "Protean-error") true)
  (expect (:status rsp-8) 405)
  (expect (contains? (:headers rsp-8) "Allow") true))


;; =============================================================================
;; Parameters
;; =============================================================================

(def cdx-2 {
  "sample" {
    "simple/${thingId}" {
      :get [
        {:rsp {:200 {}}}
        {:get {:rsp {:200 {}}}}
        {
          "simple/${thingId}" {:get {:rsp {:200 {}}}},
          :vars {"thingId" {:doc "Id of thing", :type :Int}}
        }
        {:rsp {:200 {:doc "OK"}}}
        {:get {:rsp {:200 {:doc "OK"}}},
         :default-content-type "application/json; charset=utf-8",
         "sample" {
           "simple/${thingId}" {:get {:rsp {:200 {}}}},
           :vars {"thingId" {:doc "Id of thing", :type :Int}}
         }
        }
      ]
    }
  }
})

(let [rsp-1 (s/sim-rsp (req :get "/sample/simple/1" h/txt body nil) cdx-2 {})
      rsp-2 (s/sim-rsp (req :get "/sample/simple" h/txt body nil) cdx-2 {})]
  (expect (:status rsp-1) 200)
  (expect (:status rsp-2) 404)
  (expect (contains? (:headers rsp-2) "Protean-error") true))

;
; (def sims (m/load-script "test-data/default.sim.edn"))
;
; (deftest post-wildcard-crazy-201
;   (let [req (request :post "/sample/v/1/users/1/items/1/assets" h/txt body nil)
;         cdx (d/read-edn "wildcard-crazy.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (:status rsp) 201))))
;
; ; should fail verification (yield 400) on json req body
; (deftest post-rsp-jsn-body-400
;   (let [req (request :post "/sample/simple" h/jsn (.getBytes "{\"k1\":\"v1\"}" "UTF-8") nil)
;         cdx (d/read-edn "post-codex-400.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (:status rsp) 400))))
;
; (deftest multimethod-get
;   (let [req (get-req "/sample/homes/1")
;         cdx (d/read-edn "multi-method.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (:status rsp) 200))))
;
; (deftest multimethod-put
;   (let [req (request :put "/sample/homes/1" h/frm body {})
;         cdx (d/read-edn "multi-method.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (:status rsp) 204))))
;
; (deftest multimethod-head
;   (let [req (request :head "/sample/homes/1" h/txt body nil)
;         cdx (d/read-edn "multi-method.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (:status rsp) 405))))
;
; (deftest status-override
;   (let [req (get-req "/sim/status-override")
;         cdx (d/read-edn "sim.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (:status rsp) 204))))
;
; (deftest json-ctype
;   (let [req (get-req "/sim/path1")
;         cdx (d/read-edn "sim.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (.contains (get-in rsp [:headers h/ctype]) h/jsn) true))))
;
; (deftest txt-ctype
;   (let [req (get-req "/sim/path-txt")
;         cdx (d/read-edn "sim.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (.contains (get-in rsp [:headers h/ctype]) h/txt) true))))
;
; (deftest xml-ctype
;   (let [req (get-req "/sim/path-xml")
;         cdx (d/read-edn "sim.edn")
;         rsp (sim-rsp-> req cdx sims)]
;     (is (= (.contains (get-in rsp [:headers h/ctype]) h/xml) true))))
