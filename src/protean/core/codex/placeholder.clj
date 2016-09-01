(ns protean.core.codex.placeholder
  "Placeholder functionality, swapping codex examples, generating."
  (:refer-clojure :exclude [long int])
  (:require [clojure.string :as s]
            [clojure.set :refer [map-invert]]
            [clojure.pprint]
            [protean.core.generation.generate :as gn]
            [protean.core.codex.document :as d]
            [protean.core.transformation.coerce :as c]))

;; =============================================================================
;; Helper functions
;; =============================================================================

; place holder has form: ${xxx}
(def ph #"\$\{([^\}]*)\}")

(defn- g-val [type structured tree]
  (if-let [regex (d/get-in-tree tree [:types type])]
    (gn/generate regex)
    (case type
      :Int (str (gn/rnd-int))
      :Long (str (gn/rnd-long))
      :Double (str (gn/rnd-double))
      :Boolean (str (gn/rnd-bool))
      :Uuid (str (gn/rnd-uuid))
      :Json (c/js structured)
      :MatrixParams (c/->matrix-params structured)
      (gn/generate type))))

;; =============================================================================
;; Truthiness functions
;; =============================================================================

(defn holder?
  "Does a value contain a placeholder?"
  [v]
  (cond
    (string? v) (re-seq ph v)
    (map? v)(seq (mapcat holder? (vals v)))
    (seq? v)(mapcat holder? v)
    (vector? v)(mapcat holder? v)
    :else nil))

(defn map-holders [s]
  (map-invert (into {} (holder? s))))

;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn- replace-loop [s func matches]
  (if (empty? matches)
    s
    (let [match (first matches)
          to-be-replaced (first match)
          term (second match)
          applied (func term)]
      (recur
        (if applied (s/replace-first s to-be-replaced applied) s)
        func
        (rest matches)))))

(defn replace-all-with
  "replace all occurrences in string of placeholder with result of applying func to the placeholder name"
  [s func]
  (replace-loop s func (holder? s)))

(defn- holder-swap-exp [tree v]
  (if-let [x (d/get-in-tree tree [:vars v :examples])]
    (first x)))

(declare swap)

; TODO we will probably want to control if we generate optional elements
(defn gen-struct
  "generate a structed value, where the sub-values may be recursively generated"
  [struct tree bag gen-all]
  (let [struct-with-ph (->> struct
                         (map (fn [[k v]] [k (first v)]))
                         (into {}))]
    (swap struct-with-ph tree bag :gen-all gen-all)))


(defn- holder-swap-gen [gen-all bag tree v]
  (let [{:keys [gen type struct]} (d/get-in-tree tree [:vars v])
        structured (gen-struct struct tree bag gen-all)]
    (if (or gen-all (not (= false gen)))
      (if type
        (g-val type structured tree)))))

(defn- holder-swap-bag [bag v]
  (if-let [x (get-in bag [v])]
    x))

(defn holder-swap
  "Swap generative values in m of placeholders."
  [m swap-fn tree]
  (cond
    (string? m)(replace-all-with m (partial swap-fn tree))
    (map? m)(into {} (for [[k v] m]
      {k (holder-swap v swap-fn tree)}))
    (seq? m)(map #(holder-swap % swap-fn tree) m)
    (vector? m)(map #(holder-swap % swap-fn tree) m)
    :else m))

(defn swap
  "swaps all occurances of placeholders in m with values in bag
   or generated/examples from tree.
   Note vars marked as :gen false in tree will not be generated (unless optional parameter :gen-all is true)"
  [m tree bag & {:keys [gen-all]}]
  (-> m
     (holder-swap holder-swap-bag bag)
     (holder-swap holder-swap-exp tree)
     (holder-swap (partial holder-swap-gen gen-all bag) tree)))


;; =============================================================================
;; Extraction functions
;; =============================================================================

(defn- query-param-value
  [tree request placeholder]
  (let [qps    (d/qps tree true)
        ph-qps (into (sorted-map) (map #(hash-map (ffirst(holder? %)) (second %)) (map-invert qps)))
        param  (get-in ph-qps [placeholder])
        value  (get-in request [:params (keyword param)])]
    (when-let [qps-v (when (some? value) (get-in qps [param]))]
      (second (re-find (re-pattern (s/replace qps-v ph "(.*)")) value)))))

(defn response-bag
  "Creates a bag of placeholder values for the response from the request"
  [tree tree-response request]
  (into
    (sorted-map)
    (map
      #(hash-map
        (second %)
        (query-param-value tree request (first %)))
      (holder? tree-response))))

(defn- diff [s1 s2]
  (cond
    (and (nil? (first s1)) (nil? (first s2))) []
    (= (first s1) (first s2)) (diff (rest s1) (rest s2))
    :else [s1 s2]))

(defn- diff-str [s1 s2]
  (into [] (map s/join (diff (char-array (str s1)) (char-array (str s2))))))

(defn read-from [template a-ph s]
  (let [[left right] (diff-str template s)
        diff-match (if left (re-matches ph left))]
        ; note currently only works until first mismatch.
        ; Which only works if our placeholder is the only placeholder, and is at the end of the string.
        ; e.g. abc${def} - ok
        ;      abc${def}ghi - not ok
    (println "left" left)
    (println "right" right)
    (println "diff-match" diff-match)
    (if (= (second diff-match) a-ph)
      right)))
