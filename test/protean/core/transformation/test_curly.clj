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
   :uri "http://laton.lan:3000/sample/simple"
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


;; (let [codices (d/read-edn "curly.edn")
;;       curly (curly-analysis-> "localhost" 8080 codices "curly")
;;       [cmd verbosity uri] (split (first curly) #" ")]
;;   (expect uri "'http://localhost:8080/curly/query-params?blurb=flibble'"))
