(ns protean.core.codex.test-reader
  (:require [protean.core.io.data :as d]
            [expectations :refer :all]))

(let [cdx (d/read-edn "sim.edn")] (expect 8 (count (:ordered-resources cdx))))
