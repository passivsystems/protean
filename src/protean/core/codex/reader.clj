(ns protean.core.codex.reader
  (:require [clojure.edn :as edn]
            [protean.config :as conf]
            [protean.core.codex.document :as d])
  (:import java.io.File))

(defn- read-codex-part
  "will read the codex eden file, merging with any referenced files"
  [codex-dir file]
  (defn- merge-includes [[k v]]
    (cond
      (= :includes k) (reduce merge-with merge (map (partial read-codex-part codex-dir) v))
      (map? v) {k (apply merge-with merge (map merge-includes v))}
      :else {k v}))
  (let [afile (if (string? file) (d/to-path-dir file codex-dir) file)
        read (edn/read-string (slurp afile))]
    (apply merge-with merge (map merge-includes read))))

(defn read-codex
  "will read the codex eden file, merging with any referenced files"
  [file]
  (let [codex-dir (.getParent (.getAbsoluteFile file))]
    ;; TODO review this - alternatives are setting a binding, updating system
    ;; property (env caches values at startup)?
    (merge {:codex-dir codex-dir} (read-codex-part file codex-dir))))
