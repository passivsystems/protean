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
      :Int (Math/abs (.nextInt rnd))
      :Long (Math/abs (.nextLong rnd))
      :Double (.nextDouble rnd)
      :Boolean (.nextBoolean rnd)
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


(defn authzn-holder?
  "Does the authzn header contain a placeholder ?"
  [v] (if-let [auth (d/azn v)] (holder? auth) false))

(defn params-holder?
  "Do test params contain a placeholder ?
   body is the test request body
   k may be query-params|form-params|body"
  [body k]
  (if-let [params (if (= k :body) (c/clj (k body)) (k body))]
    (if (first (filter #(holder? %) (vals params)))
      true
      false)
    false))

(defn test-holder?
  "Does a test contain placeholders of any kind ?"
  [[method uri mp :as test]]
  (or (holder? uri)
      (authzn-holder? mp)
      (params-holder? mp :query-params)
      (params-holder? mp :body)
      (params-holder? mp :form-params)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn uri-ns-holder
  "Get ns prefixed wildcard portion of uri, E.G. things/psv+."
  [uri]
  (if-let [match (holder? uri)]
    (first (map #(nth % 1) match)))) ; Just returning first match for now - TODO

(defn encode-value
  "Encode body items as clojure, they are Json initially."
  [k x] (if (= k :body) (c/clj x) x))



;        extract-ph-names (fn [input]
;            (map #(nth % 1) (ph/holder? input)))
;        ph-names (filter identity (reduce concat (map extract-ph-names inputs)))


;       (fn [s]
;            (if-let [match (ph/holder? s)]
;              (recur (stg/replace-first s ph/ph (str "_" (nth (first match) 1) "_")))
;             s))

(defn replace-all-with
  "replace all occurrences in s of placeholder with result of applying func to the placeholder name"
  [s func]
  (if-let [match (holder? s)]
    (recur (stg/replace-first s ph (func (nth (first match) 1))) func)
  s))

(defn holder-swap-uri [v [method uri mp :as payload] tree]
  (if-let [sv (d/get-in-tree tree [:vars v :type])]
    (let [gv (g-val sv tree)
          raw-map (update-in mp [:codex :ph-swaps] conj "dyn")
          ph-map (update-in raw-map [:codex :ph-swaps] vec)]
      (list method (stg/replace uri ph (str gv)) ph-map)) ; TODO this will replace all in URI - do we know we only have 1?
    payload))

(defn holder-swap-exp [tree v]
  (if-let [x (d/get-in-tree tree [:vars v :examples])]
    (first x)
    v))

(defn holder-swap-gen [tree v]
  (if-let [x (d/get-in-tree tree [:vars v :type])]
    (g-val x tree)
    v))

(defn holder-swap2
  "Swap generative values in for placeholders."
  [m swap-fn tree]
  (println "holder-swap2 m:" m (type m))
  (def z (for [e m]
    (do
      (println "holder-swap2 e:" e (type e))
    (let [k (key e)
          v (val e)]
      (do (println "holder-swap2 kv" k ":" v "(" (type v) ")")
      {k (cond
        (string? v)(do (def x (replace-all-with v (partial swap-fn tree))) (println "x:" x) x)
        (vector? v)(for [x v] (replace-all-with x (partial swap-fn tree))) ; TODO recur on each v may be a map..
        :else (do (println "recuring on " (type v)) (holder-swap2 swap-fn v tree)) ; recur?
      )})
    ))))
  (println "z:" z)
z)

(defn- json-qp? [t p]
  (if (empty? p) false (and (d/qp-json? t) (map? (first (vals p))))))

(defn- swap-qp [swp-fn p is-json-qp t]
  (let [c (if is-json-qp (first (vals p)) p)]
    (for [[k v] c] [k (swp-fn k v t)])))

(defn- swap-body [swp-fn p t]
  (for [[k v] p] [k (swp-fn k v t)]))

(defn- mapify-swapped [raw p is-qp is-json-qp ph-op]
  (let [mapified (into {} (for [[k [sval stype :as v]] raw] [k sval]))
        v-res (if (and is-qp (= ph-op :vars) is-json-qp) (c/js mapified) mapified)]
    (if is-json-qp
      {(first (keys p)) v-res}
      v-res)))

(defn holders-swap
  "Swap all placeholders with available seed, example or generated substitutes."
  [ph swp-fn type ph-op t]
  (let [p (if (vector? ph) (first ph) ph)
        is-json-qp (json-qp? t p)
        is-qp (qp? type)
        raw (if is-qp (swap-qp swp-fn p is-json-qp t) (swap-body swp-fn p t))
        swapped (mapify-swapped raw p is-qp is-json-qp ph-op)
        sts (for [[k [sval stype :as v]] raw] stype)
        swap-type (cond
                   (some #{"gen" "exp"} sts) "dyn"
                   (some #{"seed"} sts) "seed"
                   :else "idn")]
    [swapped swap-type]))
