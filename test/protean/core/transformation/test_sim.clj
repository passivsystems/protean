(ns protean.core.transformation.test-sim
  (:require [protean.api.protocol.http :as h]
            [protean.core.io.data :as d]
            [protean.api.transformation.coerce :as c]
            [protean.api.transformation.sim :as s]
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
  (expect 200 (:status rsp-1))
  (expect 200 (:status rsp-2))
  (expect 2 (count (:headers rsp-2))) ;; account for CORS headers
  (expect 204 (:status rsp-3))
  (expect 201 (:status rsp-4))
  (expect 2 (count (:headers rsp-4))) ;; account for CORS headers
  (expect 204 (:status rsp-5))
  (expect 204 (:status rsp-6))
  (expect 404 (:status rsp-7))
  (expect true (contains? (:headers rsp-7) "Protean-error"))
  (expect 405 (:status rsp-8))
  (expect true (contains? (:headers rsp-8) "Allow")))


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
  (expect 200 (:status rsp-1))
  (expect 404 (:status rsp-2))
  (expect true (contains? (:headers rsp-2) "Protean-error")))


;; =============================================================================
;; Validation
;; =============================================================================

(def cdx-3 {
  "sample" {
    "simple" {
      :get [{
        :validating true
        :vars {"rp1" {:type :String :doc "A test request param"}}
        :req {:query-params {"rp1" ["${rp1}" :required]}}
        :rsp {:200 {}}
      }]
    }
  }
  })

(let [rsp-1 (s/sim-rsp (req :get "/sample/simple" h/txt body nil) cdx-3 {})]
  (expect 400 (:status rsp-1)))
