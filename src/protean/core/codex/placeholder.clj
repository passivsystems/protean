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

(defn holder-swap-uri [v [method uri mp :as payload] tree]
  (if-let [sv (d/get-in-tree tree [:req :vars v :type])]
    (let [gv (g-val sv tree)
          raw-map (update-in mp [:codex :ph-swaps] conj "dyn")
          ph-map (update-in raw-map [:codex :ph-swaps] vec)]
      (list method (stg/replace uri ph (str gv)) ph-map)) ; TODO this will replace all in URI - do we know we only have 1?
    payload))

(defn holder-swap-exp
  "Swap codex example values in for placeholders."
  [k v tree]
  (if-let [match (nth (first (holder? v)) 1)] ; just pulling out first match (could there be more?)
    (if-let [x (d/get-in-tree tree [:req :vars match :examples])]
      [(first x) "exp"]
      [v "idn"])
    [v "idn"]))

(defn holder-swap-gen
  "Swap generative values in for placeholders."
  [k v tree]
  (if-let [match (nth (first (holder? v)) 1)]; just pulling out first match (could there be more?)
    (if-let [x (d/get-in-tree tree [:req :vars match :type])]
      [(g-val x tree) "format"]
      [v "idn"])
    [v "idn"]))

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
