(ns protean.core.codex.placeholder
  "Placeholder functionality, swapping codex examples, generating."
  (:refer-clojure :exclude [long int])
  (:require [clojure.string :as stg]
            [clojure.data.generators :as gen]
            [protean.core.codex.document :as d]
            [protean.core.transformation.coerce :as c])
  (:import java.lang.Math java.util.Random))

;; =============================================================================
;; Helper functions
;; =============================================================================

(def psv "psv+")
(def ns-psv "/psv+")

(def rnd (Random.))

(defn- int
  "Generate a random int.
   For some reason generators int does not return an int."
  [] (.intValue (gen/uniform Integer/MIN_VALUE (inc Integer/MAX_VALUE))))

(defn- int+ [] (Math/abs (.nextInt rnd)))

(defn- long+ [] (Math/abs (.nextLong rnd)))

(defn- g-val [v]
  (case v
    "Int" (int+)
    "Long" (long+)
    "String" (gen/string)))

(defn- qp? [type] (= type :query-params))


;; =============================================================================
;; Truthiness functions
;; =============================================================================

(defn holder?
  "Does a simple value contain a placeholder ?"
  [v] (if (string? v) (.contains v psv) false))

(defn uri-ns-holder?
  "Does a uri contain a ns prefixed wildcard placeholder ?"
  [v] (.contains v ns-psv))

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
  (or (uri-ns-holder? uri)
      (authzn-holder? mp)
      (params-holder? mp :query-params)
      (params-holder? mp :body)
      (params-holder? mp :form-params)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn uri-ns-holder
  "Get ns prefixed wildcard portion of uri, E.G. things/psv+."
  [uri] (-> uri (.split "/psv\\+") first (.split "/") last (str "/" psv)))

(defn encode-value
  "Encode body items as clojure, they are Json initially."
  [k x] (if (= k :body) (c/clj x) x))

(defn holder-swap-uri [v [method uri mp :as payload]]
  (if-let [sv (get-in mp [:gen v :type])]
    (let [gv (g-val sv)
          raw-map (update-in mp [:codex :ph-swaps] conj "dyn")
          ph-map (update-in raw-map [:codex :ph-swaps] vec)]
      (list method (stg/replace uri psv (str gv)) ph-map))
    payload))

(defn holder-swap-exp
  "Swap codex example values in for placeholders."
  [k v m]
  (if (holder? v)
    (if-let [x (get-in m [:gen k :examples])] [(first x) "exp"] [v "idn"])
    [v "idn"]))

(defn holder-swap-gen
  "Swap generative values in for placeholders."
  [k v mp]
  (if (holder? v)
    (if-let [x (get-in mp [:gen k :type])] [(g-val x) "gen"] [v "idn"])
    [v "idn"]))

(defn- json-qp? [m p]
  (if (empty? p) false (and (d/qp-json? m) (map? (first (vals p))))))

(defn- swap-qp [swp-fn m p]
  (let [c (if (json-qp? m p) (first (vals p)) p)]
    (for [[k v] c] [k (swp-fn k v m)])))

(defn- swap-body [swp-fn m p] (for [[k v] p] [k (swp-fn k v m)]))

(defn- mapify-swapped [raw m p type ph-op]
  (let [mapified (into {} (for [[k [sval stype :as v]] raw] [k sval]))
        v-res (if (and (qp? type) (= ph-op :gen) (json-qp? m p)) (c/js mapified) mapified)]
    (if (json-qp? m p)
      {(first (keys p)) v-res}
      v-res)))

(defn holders-swap
  "Swap all placeholders with available seed, example or generated substitutes."
  [ph swp-fn m type ph-op]
  (let [p (if (vector? ph) (first ph) ph)
        raw (if (qp? type) (swap-qp swp-fn m p) (swap-body swp-fn m p))
        swapped (mapify-swapped raw m p type ph-op)
        sts (for [[k [sval stype :as v]] raw] stype)
        swap-type (cond
                   (some #{"gen" "exp"} sts) "dyn"
                   (some #{"seed"} sts) "seed"
                   :else "idn")]
    [swapped swap-type]))
