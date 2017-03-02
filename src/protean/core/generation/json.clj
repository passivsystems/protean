(ns protean.core.generation.json
  "Generate json from json schema."
  (:require [clojure.string :as str]
            [cheshire.core :as cc]
            [protean.core.generation.generate :as g]
            [clj-time.core :as tc]
            [clj-time.format :as tf]))

(declare gen2)

(defn gen-obj [{:keys [properties required enum]}]
  (cond
    enum (rand-nth enum)
    :else (let [f (fn [[n o]] [n (gen2 o)])]
            (into {} (map f properties)))))

(defn- gen-int-between [min max]
  (+ min (rand-int (- max min))))

(defn gen-str [{:keys [minLength maxLength format enum pattern]}]
  (cond
    enum (rand-nth enum)
    (= "date-time" format) (tf/unparse (tf/formatters :date-time-no-ms) (tc/now)) ; TODO move generators from placeholder into generate, and generate a date-time
    (= "email" format) (str (g/generate "[a-zA-Z\\d][a-zA-Z\\d-]{1,13}[a-zA-Z\\d]")
                            "@"
                            ; this is hostname repeated:
                            (g/generate "[a-zA-Z]{1,33}\\.[a-z]{2,4}"))
    (= "hostname" format) (g/generate "[a-zA-Z]{1,33}\\.[a-z]{2,4}")
    (= "ipv6" format) (g/generate "[a-f\\d]{4}(:[a-f\\d]{4}){7}")
    (= "ipv4" format) (str/join "." (take 4 (repeatedly #(rand-int 256))))
    (= "uri" format) "http://www.ietf.org/rfc/rfc2396.txt"
    (= "uriref" format) "/rfc/rfc2396.txt"
    ;(= "uri" format) (g/generate "[a-zA-Z][a-zA-Z0-9+-.]*")
    ; (= "uriref" format) (g/generate "[a-zA-Z][a-zA-Z0-9+-.]*")
    pattern (g/generate pattern)
    :else (let [len (gen-int-between (or minLength 0) (or maxLength 20))]
             (g/rnd-str len "abcdefghijklmnopqrstuvwxyz"))))

(defn gen-int [{:keys [minimum maximum exclusiveMinimum exclusiveMaximum]}]
  (let [min (+ (or minimum 0) (if exclusiveMinimum 1 0))
        max (- (or maximum 10) (if exclusiveMaximum 1 0))]
    (gen-int-between min max)))

(defn gen-number [{:keys [minimum maximum exclusiveMinimum exclusiveMaximum]}]
  (let [min (+ (or minimum 0) (if exclusiveMinimum 1 0))
        max (- (or maximum 10) (if exclusiveMaximum 1 0))]
    (+ min (* (rand) (- max min)))))

(defn gen-array [{:keys [items uniqueItems minItems maxItems]}]
  (let [min (or minItems 0)
        max (or maxItems 10)
        size (gen-int-between min max)]
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
