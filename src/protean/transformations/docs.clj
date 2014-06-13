(ns protean.transformations.docs
	"Uses output from the analysis transformations to generate usage docs."
  (:require [clojure.string :as stg]
            [clojure.edn :as edn]
            [clojure.core.incubator :as ib]
            [clojure.java.io :refer [delete-file]]
            [ring.util.codec :as cod]
            [me.raynes.laser :as l]
            [protean.transformations.api :as txapi]
            [protean.transformations.analysis :as txan]
            [protean.transformations.curly :as txc])
  (:use [clojure.java.io :refer [file]]
        [me.rossputin.pew])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(defmacro get-version [] (System/getProperty "protean.version"))

(defn- doc-li [c s] (for [[k v] c] (li (str k s (stg/replace v "psv+" "XYZ")))))

(defn- cell [c s] (td (ul-unstyled (doc-li c s))))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn services-tr [payload]
  (map #(li (a (str "/documentation/services/" (name %)) (name %))) payload))

(l/defdocument services-template (file "public/html/projects.html")
  [payload]
  (l/id="project-version") (<- (get-version))
  (l/id="projects-list") (<- (services-tr payload)))

(defn service-td
  [{:keys [method doc headers req-params body-keys form-keys uri] :as payload}]
  (vec [(td
          (ul-unstyled
           (vec [(li (strong (stg/upper-case (name method))))
                  (li (->> (span) (clazz glyph-info) (title (or doc ""))))])))
        (->> (td
               (ul-unstyled
                (vec [(li (strong uri))
                      (li (->> (div (small (txc/curly-> payload)))
                               (clazz pnl-info)))])))
             (width "500px"))
        (cell headers ":")
        (cell req-params "=")
        (cell form-keys "=")
        (cell body-keys ":")]))

(defn service-tr [payload] (map #(tr (service-td %)) payload))

(l/defdocument service-template (file "public/html/project.html")
  [id payload]
  (l/id="project-version") (<- (get-version))
  (l/id="project-name") (<- (name id))
  (l/element= :tbody) (<- (service-tr payload)))
