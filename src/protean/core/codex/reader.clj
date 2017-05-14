(ns protean.core.codex.reader
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [protean.config :as conf]
            [protean.core.codex.document :as d])
  (:import java.io.File))

(defn- resource-order-sequence [tree svc]
  (let [matched-path-ks (re-seq #"\"[A-Za-z0-9-\$\{\}/]+\" \{[\s]+" tree)
        raw-paths (last (s/split (s/join "," matched-path-ks) (re-pattern svc)))]
    (re-seq #"\"[A-Za-z0-9-\$\{\}/]+\"" raw-paths)))

(defn- read-codex-part
  "will read the codex eden file, merging with any referenced files"
  [codex-dir file]
  (defn- merge-includes [[k v]]
    (cond
      (= :includes k) (reduce merge-with merge (map (partial read-codex-part codex-dir) v))
      (map? v) {k (apply merge-with merge (map merge-includes v))}
      :else {k v}))
  (let [afile (if (string? file) (d/to-path-dir file codex-dir) file)
        file-content (slurp afile)
        read (edn/read-string file-content)
        tree (apply merge-with merge (map merge-includes read))]
    (if-let [svc (d/service tree)]
      (let [xs-raw (resource-order-sequence file-content svc)
            xs (map #(s/replace % (re-pattern "\"") "") xs-raw)]
        (merge {:ordered-resources xs} tree))
      tree)))

(defn read-codex
  "will read the codex eden file, merging with any referenced files"
  [file]
  (let [codex-dir (.getParent (.getAbsoluteFile file))]
    ;; TODO review this - alternatives are setting a binding, updating system
    ;; property (env caches values at startup)?
    (merge {:codex-dir codex-dir} (read-codex-part codex-dir file))))
