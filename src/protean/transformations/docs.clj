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

(defn- a [h c] (l/node :a :attrs {:href h} :content c))
(defn- ul-unstyled [d] (l/node :ul :attrs {:class "list-unstyled"} :content d))
(defn- li [d] (l/node :li :content d))
(defn- tr [d] (l/node :tr :content d))
(defn- td [d] (l/node :td :content d))
(defn- small [d] (l/node :small :content d))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn build-projects-tr [payload]
  (map #(li (a (str "/documentation/projects/" (name %)) (name %))) payload))

(l/defdocument projects-template (file "public/html/projects.html")
  [payload]
  (l/id="project-version") (l/content (get-version))
  (l/id="projects-list") (l/content (build-projects-tr payload)))

(defn build-headers-list [payload]
  (map #(li (str (key %) ": " (val %))) payload))

(defn build-req-params-list [payload]
  (map #(li (str (key %) " = " (val %))) payload))

(defn build-form-keys-list [payload]
  (map #(li (str (key %) ": " (val %))) (:form-keys payload)))

(defn build-json-keys-list [payload]
  (map #(li (str (key %) ": " (val %))) (:body-keys payload)))

(defn build-project-td [payload]
  (vec [(td (upper-case (name (:method payload))))
        (l/node :td :attrs {:width "500px"} :content
          (ul-unstyled
              (vec [(li (:uri payload))
                    (l/node :div
                      :attrs {:class "panel panel-info"}
                      :content (li (small (txc/curly-> payload))))])))
        (td (ul-unstyled (build-headers-list (:headers payload))))
        (td (ul-unstyled (build-req-params-list (:req-params payload))))
        (td (ul-unstyled (build-form-keys-list payload)))
        (td (ul-unstyled (build-json-keys-list payload)))]))

(defn build-project-tr [payload] (map #(tr (build-project-td %)) payload))

(l/defdocument project-template (file "public/html/project.html")
  [id payload]
  (l/id="project-version") (l/content (get-version))
  (l/id="project-name") (l/content (name id))
  (l/element= :tbody) (l/content (build-project-tr payload)))