(ns protean.core.codex.reader
  (:require [clojure.edn :as edn]))

(defn read-codex
  "will read the codex eden file, merging with any referenced files"
  [file]
  (let [read (edn/read-string (slurp file))
        include-edn (map read-codex (:includes read))]
    (merge (reduce merge include-edn) read)))
