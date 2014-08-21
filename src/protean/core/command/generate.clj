(ns protean.core.command.generate
  "Generate values for placeholders, only when integration testing."
  (:refer-clojure :exclude [long int])
  (:require [clojure.string :as stg]
            [clojure.data.generators :as gen]
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
    "Long" (long+)))

(defn- holder-swap [v [p1 p2 p3 :as payload]]
  (let [m p3]
    (if-let [sv (get-in m [:gen v :type])]
      (let [gv (g-val sv)]
        (list p1 (stg/replace p2 "psv+" (str gv)) p3))
      payload)))

(defn- uri [codices payload]
  (let [uri (second payload)]
    (if (p/uri-ns-holder? uri)
      (let [v (p/uri-ns-holder uri)]
        (holder-swap v payload))
      payload)))

(defn- generate [test codices]
  (->> test
       (uri codices)))

(defn generations [codices tests] (map #(generate % codices) tests))
