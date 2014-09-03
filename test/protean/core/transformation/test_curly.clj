(ns protean.core.transformation.test-curly
  (:require [clojure.string :refer [split]]
            [protean.core.io.data :as d]
            [protean.core.transformation.curly :refer [curly-analysis->]]
            [clojure.test :refer :all]))

(deftest simple-curly
  (let [codices (d/read-edn "get-codex.edn")
        curly (curly-analysis-> "localhost" 8080 codices "sample")
        [cmd verbosity uri] (split (first curly) #" ")]
    (is (= cmd "curl"))
    (is (= verbosity "-v"))
    (is (= uri "'http://localhost:8080/sample/simple'"))))

(deftest query-param-curly
  (let [codices (d/read-edn "curly.edn")
        curly (curly-analysis-> "localhost" 8080 codices "curly")
        [cmd verbosity uri] (split (first curly) #" ")]
    (is (= uri "'http://localhost:8080/curly/query-params?blurb=flibble'"))))
