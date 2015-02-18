(ns protean.core.codex.reader
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [protean.config :as conf])
  (:import java.io.File))

(defn- read-codex-part
  "will read the codex eden file, merging with any referenced files"
  [file]
  (defn- merge-includes [[k v]]
    (cond
      (= :includes k) (reduce merge-with merge (map read-codex-part v))
      (map? v) {k (apply merge-with merge (map merge-includes v))}
      :else {k v}))
  (let [afile (if (string? file) (io/file (conf/codex-dir) file) file)
        read (edn/read-string (slurp afile))]
    (apply merge-with merge (map merge-includes read))))

(defn read-codex
  "will read the codex eden file, merging with any referenced files"
  [file]
  (let [codex-dir (.getParent (.getAbsoluteFile file))]
    ;; TODO review this - alternatives are setting a binding, updating system
    ;; property (env caches values at startup)?
    (merge {:codex-dir codex-dir} (read-codex-part file))))
