(ns protean.core.transformation.paths
  "Accepts codices and a vector of locs (paths) to get out of codices.
   Creates a datastructure which contains each required path and its
   associated spec.

   Codices here may be the entire body of codices Protean includes.

   If the locs vector is empty every resource under every service Protean knows
   about will be included in the path extraction transformation.

   If the corpus includes a 'locs' vector 'sample simple' a
   path extraction datastructure will be generated for one 'sample' service like:

    {
      :svc :sample
      :path simple
      :method :get
      :spec {:doc Simplest example of a resource - doc is optional}
    }
  "
  (:require [clojure.string :as stg]
            [protean.core.codex.document :as d]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- encode [svc path md sp codex]
  (let [merged (update-in sp [:req :headers] merge (:headers codex))]
    {:svc svc :path path :method md :spec merged}))

(defn- methods-range [svc paths codex]
  (map #(encode svc (first (keys paths)) (key %) (val %) codex) (first (vals paths))))

(defn- combi-paths [codices combi]
  (let [svc (first combi)
        paths-loc (rest combi)
        paths (map #(hash-map % (get-in codices [svc %])) paths-loc)
        codex (get-in codices [svc :req])]
    (map #(methods-range svc % codex) paths)))

(defn- svc-paths [codices svc]
  (let [paths-raw (d/custom-entries (get-in codices [svc]))
        paths (map #(hash-map (first %) (last %)) paths-raw)
        codex (get-in codices [svc :req])] ; TODO review this - codex is always nil?
    (map #(methods-range svc % codex) paths)))

(defn- proc-group [codices group path-fn coll]
  (if (seq group)
    (flatten (reduce conj (map #(path-fn codices %) coll)))
    '()))

(defn- locs-range [codices locs]
  (let [groups ((juxt filter remove) #(= (count (stg/split % #" ")) 1) locs)
        combi (map #(stg/split (apply str %) #" ") (second groups))
        combi-paths (proc-group codices (second groups) combi-paths combi)
        svc-paths (proc-group codices (first groups) svc-paths (first groups))]
    (concat combi-paths svc-paths)))

;; =============================================================================
;; Path calculation functions
;; =============================================================================

(defn paths->
  "Get all service paths or specified combinations of service/path | service."
  [codices locs]
  (def res (locs-range codices locs))
  (if (empty? res) (println "WARNING locs" locs "did not resolve to any path"))
  res)
