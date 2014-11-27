(ns protean.core.codex.placeholder
  "Placeholder functionality, swapping codex examples, generating."
  (:refer-clojure :exclude [long int])
  (:require [clojure.string :as s]
            [clojure.set :refer [map-invert]]
            [clojure.pprint]
            [protean.core.codex.document :as d]
            [protean.core.transformation.coerce :as c])
  (:import java.lang.Math
           java.util.Random
           java.util.UUID
           org.databene.benerator.primitive.RegexStringGenerator
           org.databene.benerator.engine.DefaultBeneratorContext))

;; =============================================================================
;; Helper functions
;; =============================================================================

; place holder has form: ${xxx}
(def ph #"\$\{([^\}]*)\}")

(def rnd (Random.))

(defn- generate [regex]
  (let [generator (RegexStringGenerator. regex)]
    (.init generator (DefaultBeneratorContext.))
    (.generate generator)))

(defn- g-val [v tree]
  (if-let [regex (d/get-in-tree tree [:types v])]
    (generate regex)
    (case v
      :Int (str (Math/abs (.nextInt rnd)))
      :Long (str (Math/abs (.nextLong rnd)))
      :Double (str (.nextDouble rnd))
      :Boolean (str (.nextBoolean rnd))
      :Uuid (.toString (UUID/randomUUID))
    (generate v))))

(defn replace-placeholders [s r]
  (s/replace s ph r))

;; =============================================================================
;; Truthiness functions
;; =============================================================================

(defn holder?
  "Does a value contain a placeholder?"
  [v]
  (cond
    (string? v) (re-seq ph v)
    (map? v)(seq (mapcat holder? (vals v)))
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

(defn- holder-swap-gen [tree v]
  (if-let [x (d/get-in-tree tree [:vars v :type])]
    (g-val x tree)))

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
    (seq? m)(map holder-swap m)
    (vector? m)(map holder-swap m)
    :else m))

(defn swap [ph tree bag]
  (-> ph
     (holder-swap holder-swap-bag bag)
     (holder-swap holder-swap-gen tree)
     (holder-swap holder-swap-exp tree)))
