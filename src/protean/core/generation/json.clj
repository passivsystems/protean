(ns protean.core.generation.json
  "Generate json from json schema."
  (:require [cheshire.core :as cc]
            [protean.core.generation.generate :as g]
            [clj-time.core :as tc]
            [clj-time.format :as tf]))

(declare gen2)

(defn gen-obj [{:keys [properties required enum]}]
  (cond
    enum (rand-nth enum)
    :else (let [f (fn [[n o]] [n (gen2 o)])]
            (into {} (map f properties)))))

(defn gen-str [{:keys [maxLength format enum pattern]}]
  (cond
    enum (rand-nth enum)
    (= "date-time" format) (tf/unparse (tf/formatters :date-time-no-ms) (tc/now)) ; TODO move generators from placeholder into generate, and generate a date-time
    (= "email" format) "a@b.com"
    (= "hostname" format) "xx.lcs.mit.edu"
    (= "ipv6" format) "0:0:0:0:0:0:0:1"
    (= "uri" format) "http://www.ietf.org/rfc/rfc2396.txt"
    pattern (g/generate pattern)
    :else (g/rnd-str (or maxLength 20) "abcdefghijklmnopqrstuvwxyz")))

(defn gen-int [{:keys []}]
  (let [min 0
        max 10]
    (+ min (rand-int (- max min)))))

(defn gen-number [{:keys []}]
  (rand))

(defn gen-array [{:keys [uniqueItems minItems items]}]
  (let [min (or minItems 0)
        max 10
        size (+ min (rand-int (- max min)))]
     (into [] (repeat size (gen2 items)))))

(defn- gen2 [{:keys [type anyOf oneOf] :as o}]
  (cond
    anyOf (gen2 (merge {:type type} (rand-nth anyOf))) ; type may be defined within anyOf or in toplevel
    oneOf (gen2 (merge {:type type} (rand-nth oneOf)))
    :else (do
      (case type
        "array"   (gen-array o)
        "boolean" (rand-nth [true false])
        "integer" (gen-int o)
        "number"  (gen-number o)
        "null"    nil
        "string"  (gen-str o)
        (gen-obj o)))))

(defn gen
  "generate a json which is valid according to provided schema"
  [schema-path]
  (-> (slurp schema-path)
      (cc/parse-string true)
      gen2
      cc/generate-string))
