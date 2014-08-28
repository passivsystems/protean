(ns protean.core.command.generate
  "Generate values for placeholders, only when integration testing."
  (:refer-clojure :exclude [long int])
  (:require [clojure.string :as stg]
            [clojure.data.generators :as gen]
            [protean.core.transformation.coerce :as c]
            [protean.core.codex.placeholder :as p])
  (:import java.lang.Math))

;; =============================================================================
;; Helper functions
;; =============================================================================

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

(defn- holder-swap [k v mp]
  (if (p/holder? v)
    (if-let [x (get-in mp [:gen k :type])] (g-val x) v)
    v))

(defn- holders-swap [ph m] (into {} (for [[k v] ph] [k (holder-swap k v m)])))

(defn- uri-holder-swap [v [p1 p2 p3 :as payload]]
  (if-let [sv (get-in p3 [:gen v :type])]
    (let [gv (g-val sv)]
      (list p1 (stg/replace p2 "psv+" (str gv)) p3))
    payload))

(defn- swap-placeholders [k [method uri mp :as payload]]
  (if-let [ph (p/encode-swapped-value k (k mp))]
    (list method uri (assoc mp k (c/js-> (holders-swap ph mp))))
    payload))

(defn- uri [codices payload]
  (let [uri (second payload)]
    (if (p/uri-ns-holder? uri)
      (let [v (p/uri-ns-holder uri)]
        (uri-holder-swap v payload))
      payload)))

(defn- generate [test codices]
  (->> test
       (swap-placeholders :body)
       (uri codices)))

(defn generations [codices tests] (map #(generate % codices) tests))
