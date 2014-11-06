(ns protean.core.codex.reader
  (:require [clojure.edn :as edn]))

;      (reset! pipe/state (merge @pipe/state (r/read f)))))
(defn read-codex [file] (edn/read-string (slurp file)))
