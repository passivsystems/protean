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
        [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(defmacro get-version []
  (System/getProperty "protean.version"))

(defn a [h c] (l/node :a :attrs {:href h} :content c))
(defn ul-unstyled [d] (l/node :ul :attrs {:class "list-unstyled"} :content d))
(defn li [d] (l/node :li :content d))
(defn tr [d] (l/node :tr :content d))
(defn td [d] (l/node :td :content d))
(defn small [d] (l/node :small :content d))
(defn <- [d] (l/content d))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn build-projects-tr [payload]
  (map #(li (a (str "/documentation/projects/" (name %)) (name %))) payload))

(l/defdocument projects-template (file "public/html/projects.html")
  [payload]
  (l/id="project-version") (<- (get-version))
  (l/id="projects-list") (<- (build-projects-tr payload)))

(defn project-td
  [{:keys [method doc headers req-params body-keys form-keys uri] :as payload}]
  (vec [(td
         (ul-unstyled
          (vec [(li (upper-case (name method)))
                (li
                  (l/node :span
                          :attrs {:class "glyphicon glyphicon-info-sign"
                                  :title (or doc "")}))])))
        (l/node :td :attrs {:width "500px"} :content
          (ul-unstyled
              (vec [(li uri)
                    (l/node :div
                      :attrs {:class "panel panel-info"}
                      :content (li (small (txc/curly-> payload))))])))
        (td (ul-unstyled (for [[k v] headers] (li (str k ":" v)))))
        (td (ul-unstyled (for [[k v] req-params] (li (str k "=" v)))))
        (td (ul-unstyled (for [[k v] form-keys] (li (str k "=" v)))))
        (td (ul-unstyled (for [[k v] body-keys] (li (str k ":" v)))))]))

(defn build-project-tr [payload] (map #(tr (project-td %)) payload))

(l/defdocument project-template (file "public/html/project.html")
  [id payload]
  (l/id="project-version") (<- (get-version))
  (l/id="project-name") (<- (name id))
  (l/element= :tbody) (<- (build-project-tr payload)))
