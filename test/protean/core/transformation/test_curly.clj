(ns protean.core.transformation.test-curly
  (:require [protean.core.io.data :as d]
            [protean.core.transformation.curly :refer [curly-analysis->]]
            [clojure.test :refer :all]))

(deftest simple-curly
  (let [codices (d/read-edn "get-codex.edn")
        curly (curly-analysis-> "localhost" 8080 codices "sample")
        c (first curly)]
    (is (= true (.contains c "curl")))))
