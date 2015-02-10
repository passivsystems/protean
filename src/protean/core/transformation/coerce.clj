(ns protean.core.transformation.coerce
  "General purpose coercion."
  (:refer-clojure :exclude [int long])
  (:require [clojure.set :as st]
            [clojure.data.xml :as xml]
            [cheshire.core :as jsn]
            [yaclot.core :refer [convert to-type]])
  (:use [clojure.pprint]))

;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn int [s] (convert s (to-type Integer)))

(defn long [s] (convert s (to-type Long)))

(defn js [d] (if d (jsn/generate-string d) d))

(defn pretty-js [d] (if d (jsn/generate-string d {:pretty true}) d))

(defn clj [d] (if (map? d) d (jsn/parse-string d)))

(defn pretty-clj [d] (pprint (clj d)))

(defn xml [d] (xml/sexp-as-element d))

(defn str-xml [d] (xml/emit-str (xml d)))

(defn pretty-xml [d] (xml/indent-str (xml d)))
