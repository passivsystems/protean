(ns protean.core.transformation.test-curly
  (:require [clojure.string :refer [split]]
            [protean.core.transformation.curly :refer [curly-analysis->]]
            [expectations :refer :all]))

;; =============================================================================
;; Testing transformation from analysis structure to curl command
;; =============================================================================

(let [analysed '(
  {
   :method :get
   :uri "http://localhost:3000/sample/simple"
   :tree [nil
          {:get nil}
          {"simple" {:get nil}} {:rsp {:200 {:doc "OK"}}}
          {"sample" {"simple" {:get nil}} :get {:rsp {:200 {:doc "OK"}}}}]
  }
  )
       [cmd verbosity uri] (split (first (curly-analysis-> analysed)) #" ")]
  (expect cmd "curl")
  (expect verbosity "-v")
  (expect (.endsWith uri "/sample/simple'") true))

(let [analysed '(
  {
  :method :get
  :uri "http://localhost:3000/curly/query-params"
  :tree [
    {
      :rsp {
        :200 {
          :body-example "test-data/content/doc/responses/simple/200-ref.json"
        }
      }
      :req {
        :query-params {:required {"blurb" "${blurb}"}}
      }
      :vars {"blurb" {:doc "A sample request param", :type :String}}
    }
    {
      :get {
        :rsp {
          :200 {
            :body-example "test-data/content/doc/responses/simple/200-ref.json"
          }
        }
        :req {:query-params {:required {"blurb" "${blurb}"}}}
        :vars {"blurb" {:doc "A sample request param", :type :String}}
      }
    }
    {
      "query-params" {
        :get {
          :rsp {
            :200 {
              :body-example "test-data/content/doc/responses/simple/200-ref.json"
            }
          }
          :req {:query-params {:required {"blurb" "${blurb}"}}}
          :vars {"blurb" {:doc "A sample request param", :type :String}}
        }
      }
    }
    {
      :rsp {:200 {:doc OK}}
    }
    {
      "curly" {
        "query-params" {
          :get {
            :rsp {
              :200 {
                :body-example "test-data/content/doc/responses/simple/200-ref.json"
              }
            }
            :req {:query-params {:required {"blurb" "${blurb}"}}}
            :vars {"blurb" {:doc "A sample request param", :type :String}}
          }
        }
      }
      :types {:String "[a-zA-Z0-9]+"}
      :get {:rsp {:200 {:doc "OK"}}}
    }
  ]
   })
      [cmd verbosity uri] (split (first (curly-analysis-> analysed)) #" ")]
  (expect cmd "curl")
  (expect verbosity "-v")
  (expect (.contains uri "?blurb=")))
