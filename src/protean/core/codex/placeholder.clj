(ns protean.core.codex.placeholder
  "Placeholder functionality, swapping codex examples, generating."
  (:refer-clojure :exclude [long int])
  (:require [clojure.string :as stg]
            [clojure.data.generators :as gen]
            [protean.core.transformation.coerce :as c])
  (:import java.lang.Math))

;; =============================================================================
;; Helper functions
;; =============================================================================

(def psv "psv+")

(defn- int
  "Generate a random int.
   For some reason generators int does not return an int."
  []
  (.intValue (gen/uniform Integer/MIN_VALUE (inc Integer/MAX_VALUE))))

(defn- int+ [] (Math/abs (clojure.core/int (gen/int))))

(defn- long+ [] (Math/abs (gen/long)))

(defn- g-val [v]
  (case v
    "Int" (int+)
    "Long" (long+)
    "String" (gen/string)))


;; =============================================================================
;; Truthiness functions
;; =============================================================================

(defn holder?
  "Does a simple value contain a placeholder ?"
  [v]
  (.contains v psv))

(defn uri-ns-holder?
  "Does a uri contain a ns prefixed wildcard placeholder ?"
  [v]
  (.contains v (str "/" psv)))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn uri-ns-holder
  "Get ns prefixed wildcard portion of uri, E.G. things/psv+."
  [uri]
  (-> uri (.split "/psv\\+") first (.split "/") last (str "/psv+")))

(defn encode-value
  "Encode body items as clojure, they are Json initially."
  [k x]
  (if (= k :body) (c/clj-> x) x))

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

(defn holders-swap
  "Swap all placeholders with available seed, example or generated substitutes."
  [ph swp-fn m]
  (let [raw (for [[k v] ph] [k (swp-fn k v m)])
        swapped (into {} (for [[k [sval stype :as v]] raw] [k sval]))
        sts (for [[k [sval stype :as v]] raw] stype)
        swap-type (if (some #{"gen" "exp"} sts) "dyn" "idn")]
    [swapped swap-type]))
