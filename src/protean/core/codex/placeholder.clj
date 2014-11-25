(ns protean.core.codex.placeholder
  "Placeholder functionality, swapping codex examples, generating."
  (:refer-clojure :exclude [long int])
  (:require [clojure.string :as stg]
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

(defn- qp? [type] (= type :query-params))


(defn replace-placeholders [s r]
  (stg/replace s ph r)) 

;; =============================================================================
;; Truthiness functions
;; =============================================================================

(defn holder?
  "Does a simple value contain a placeholder ?"
  [v]
  (if (string? v) (re-seq ph v) nil))


;(defn authzn-holder?
;  "Does the authzn header contain a placeholder ?"
;  [v] (if-let [auth (d/azn v)] (holder? auth) false))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn- replace-loop [func s matches]
  (if (empty? matches)
    s
    (let [match (first matches)
          to-be-replaced (first match)
          term (second match)
          applied (func term)]
      (recur func (if applied
           (stg/replace-first s to-be-replaced applied)
           s) (rest matches)))))

(defn replace-all-with
  "replace all occurrences in s of placeholder with result of applying func to the placeholder name"
  [s func]
  (replace-loop func s (holder? s)))

(defn holder-swap-exp [tree v]
  (if-let [x (d/get-in-tree tree [:vars v :examples])]
    (first x)))

(defn holder-swap-gen [tree v]
  (if-let [x (d/get-in-tree tree [:vars v :type])]
    (g-val x tree)))

(defn holder-swap-bag [bag v]
  (if-let [x (get-in bag [v])]
    x))

(defn holder-swap
  "Swap generative values in for placeholders."
  [m swap-fn tree]
  (into {} (for [[k v] m]
    {k (cond
      (string? v)(replace-all-with v (partial swap-fn tree))
      (map? v)(holder-swap v swap-fn tree)
      :else v
    )})))

