(ns protean.server.docs
	"Uses output from the analysis transformations to generate usage docs."
  (:require [clojure.string :as stg]
            [clojure.edn :as edn]
            [clojure.core.incubator :as ib]
            [clojure.java.io :refer [delete-file]]
            [ring.util.codec :as cod]
            [me.raynes.laser :as l]
            [protean.config :as c]
            [protean.core.transformation.curly :as txc])
  (:use [clojure.java.io :refer [file]]
        [me.rossputin.pew])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(defmacro version [] (System/getProperty "protean.version"))

(defn- doc-li [c s] (for [[k v] c] (li (str k s (stg/replace v "psv+" "XYZ")))))

(defn- cell [c s] (td (ul-unstyled (doc-li c s))))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn services-tr [payload]
  (map #(li (name %)) payload))

(l/defdocument services-template (file (str (c/html-dir) "/projects.html"))
  [payload]
  (l/id="project-version") (<- (version))
  (l/id="projects-list") (<- (services-tr payload)))
