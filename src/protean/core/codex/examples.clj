(ns protean.core.codex.examples
  "Codex type examples functionality.
   Generally second in the ranks after seed values."
  (:require [protean.core.codex.placeholder :as p]))

(defn holder-swap [k v m]
  (if (p/holder? v) (if-let [x (get-in m [:gen k :examples])] (first x) v) v))
