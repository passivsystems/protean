(ns protean.transformations.docs
	"Uses output from the analysis transformations to generate usage docs."
  (:require [clojure.edn :as edn]
            [clojure.core.incubator :as ib]
            [clojure.java.io :refer [delete-file]]
            [ring.util.codec :as cod]
            [me.raynes.laser :as l]
            [protean.transformations.api :as txapi]
            [protean.transformations.analysis :as txan]
            [protean.transformations.curly :as txc])
  (:use [clojure.string :only [join split upper-case]]
        [clojure.set :only [intersection]]
        [clojure.java.io :refer [file]]
        [taoensso.timbre :as timbre :only (trace debug info warn error)]
        [me.rossputin.pew])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(defmacro get-version [] (System/getProperty "protean.version"))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn projects-tr [payload]
  (map #(li (a (str "/documentation/projects/" (name %)) (name %))) payload))

(l/defdocument projects-template (file "public/html/projects.html")
  [payload]
  (l/id="project-version") (<- (get-version))
  (l/id="projects-list") (<- (projects-tr payload)))

(defn project-td
  [{:keys [method doc headers req-params body-keys form-keys uri] :as payload}]
  (vec [(td
          (ul-unstyled
           (vec [(li (strong (upper-case (name method))))
                  (li (->> (span) (clazz glyph-info) (title (or doc ""))))])))
        (->> (td
               (ul-unstyled
                (vec [(li (strong uri))
                      (li (->> (div (small (txc/curly-> payload)))
                               (clazz pnl-info)))])))
             (width "500px"))
        (td (ul-unstyled (for [[k v] headers] (li (str k ":" v)))))
        (td (ul-unstyled (for [[k v] req-params] (li (str k "=" v)))))
        (td (ul-unstyled (for [[k v] form-keys] (li (str k "=" v)))))
        (td (ul-unstyled (for [[k v] body-keys] (li (str k ":" v)))))]))

(defn project-tr [payload] (map #(tr (project-td %)) payload))

(l/defdocument project-template (file "public/html/project.html")
  [id payload]
  (l/id="project-version") (<- (get-version))
  (l/id="project-name") (<- (name id))
  (l/element= :tbody) (<- (project-tr payload)))
