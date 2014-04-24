(ns protean.transformations.coerce
  "General purpose coercion."
  (:require [clojure.set :as st]
            [clojure.data.xml :as xml]
            [cheshire.core :as jsn]))

;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn int-> [s] (. Integer parseInt s))

(defn js-> [d] (jsn/generate-string d))

(defn clj-> [d] (jsn/parse-string d))

(defn xml-> [d] (xml/sexp-as-element d))

(defn indent-> [d] (xml/indent-str d))
