(ns protean.core.codex.test-reader
  (:require [protean.core.io.data :as d]
            [expectations :refer :all]))

(let [cdx (d/read-edn "reader.edn")
      odr (:ordered-resources cdx)]
  (expect 12 (count odr))
  (expect "cpath-1" (first odr))
  (expect "bpath_2" (second odr)))
