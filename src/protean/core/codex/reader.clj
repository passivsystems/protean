(ns protean.core.codex.reader
  (:require [clojure.edn :as edn]))

(defn read-codex
  "will read the codex eden file, merging with any referenced files"
  [file]
  (defn- merge-includes [[k v]]
    (cond
      (= :includes k) (reduce merge (map read-codex v))
      (map? v) {k (apply merge (map merge-includes v))}
      :else {k v}))
  (let [read (edn/read-string (slurp file))]
    (apply merge (map merge-includes read))))


