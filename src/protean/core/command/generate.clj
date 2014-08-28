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

(defn- uri-holder-swap [v [method uri mp :as payload]]
  (if-let [sv (get-in mp [:gen v :type])]
    (let [gv (g-val sv)]
      (list method (stg/replace uri "psv+" (str gv)) mp))
    payload))

(defn- swap-placeholders [k [method uri mp :as payload]]
  (if-let [phs (p/encode-value k (k mp))]
    (list method uri (assoc mp k (c/js-> (p/holders-swap phs holder-swap mp))))
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
