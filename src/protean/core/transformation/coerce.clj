(ns protean.core.transformation.coerce
  "General purpose coercion."
  (:require [clojure.set :as st]
            [clojure.data.xml :as xml]
            [cheshire.core :as jsn])
  (:use [clojure.pprint]))

;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn int-> [s] (. Integer parseInt s))

(defn js-> [d] (if d (jsn/generate-string d) d))

(defn pretty-js-> [d] (if d (jsn/generate-string d {:pretty true}) d))

(defn clj-> [d] (if (map? d) d (jsn/parse-string d)))

(defn pretty-clj-> [d] (pprint (clj-> d)))

(defn xml-> [d] (xml/sexp-as-element d))

(defn pretty-xml-> [d] (xml/indent-str (xml-> d)))
