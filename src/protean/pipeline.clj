(ns protean.pipeline
  (:require [clojure.edn :as edn]
            [clojure.core.incubator :as ib]
            [clojure.java.io :refer [delete-file]]
            [ring.util.codec :as cod]
            [me.raynes.laser :as l]
            [protean.transformations.coerce :as txco]
            [protean.transformations.api :as txapi]
            [protean.transformations.analysis :as txan]
            [protean.transformations.curly :as txc]
            [protean.transformations.docs :as txdocs])
  (:use [clojure.string :only [join split upper-case]]
        [clojure.set :only [intersection]]
        [clojure.java.io :refer [file]]
        [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(def json {:headers {"Content-Type" "application/json; charset=utf-8"}})

(def state (atom {:sample
                   {:paths {"get/test/*" {:rsp {:body {"t1key" "t1val"}
                                                :errors {:status [504]
                                                :probability 50}}}
                            "random/test2" {:req {:headers {"X-Auth" "XYZ"}
                                                  :req-params
                                                    {"blurb" "flibble"}}
                                            :rsp {:body {"t2key" "t2val"}}}
                            "get/xml" {:rsp {:content-type "text/xml"
                                             :body [:parent
                                                     [:child {:type "xml"}]]}}
                            "post/test" {:req {:method :post
                                               :form {"k1" "v1"}}
                                         :rsp {:headers {"Location" "7"}}}
                            "put/test1" {:req {:body {"k1" "v1" "k2" "v2"}}
                                         :rsp {:status 200}}
                            "put/test2" {}
                            "get/slow" {:rsp {:time 10}}}
                    :errors {:status [500 503] :probability 25}}}))

(def teststate (atom {
  :depapi {
    :paths {
      ;get a token
      "token" {
        :req {
          :headers {
            "Authorization" "Basic token"
            "Content-type" "application/x-www-form-urlencoded"
          }
          :req-params {
            "grant_type" "password"
            "username" "ops_user"
            "password" "apassword"
          }
        }
        :rsp {
          :body {
            "access_token" "c710eb92-9a1a-4fae-8ac3-2943b0c11fe8"
            "token_type" "bearer"
            "expires_in" "27848010"
          }
        }
      }

      ;already activated ?
      "v/1/hubs/*/homes" {
        :req { :headers {"Authorization" "Bearer token"} }
        :rsp { :status 404 }
      }
    }
  }
}))

(defn substring? [sub st] (not= (.indexOf st sub) -1))

(defn- partial-path? [key path]
  (let [split-path (set (split path #"/"))]
    (>= (count (intersection (set (split key #"/")) split-path))
       (dec (count split-path)))))

(defn- wild-path? [k paths]
  (if-let [x (first (filter #(partial-path? k %) paths))] x nil))

(defn- project-path? [proj k]
  (or (get-in @state [proj :paths k])
      (get-in @state [proj :paths (wild-path? k
        (filter #(substring? "*" %) (keys (get-in @state [proj :paths]))))])))

(defn- log-request [req]
  (info "request is : " req)
  (info "request method : " (:request-method req))
  (info "request uri : " (:uri req))
  (info "request query params : " (:query-params req)))

(defn handler
  [f & handlers]
  (reduce (fn [handled h] (partial h handled)) f (reverse handlers)))

(defn handle-proj-del-error
  [f & args]
  (try
    (apply f args)
    (catch IOException ioex
      (error (.getMessage ioex))
      {:status 500})))


;; =============================================================================
;; Project pipelines
;; =============================================================================

(defn api [{:keys [uri request-method headers query-params form-params body]
            :as req}]
  (log-request req)
  (let [proj (keyword (second (split uri #"/")))
        k (second (split uri (re-pattern (str "/" (name proj) "/"))))
        errors (get (get-in @state [proj :errors]) :status)
        probability (get (get-in @state [proj :errors]) :probability)
        req {:method request-method :hdrs headers :q-params query-params
             :form-params form-params :body (slurp body)}]
    (if-let [proj-payload (project-path? proj k)]
      (let [rsp (txapi/api-resp-> req proj-payload errors probability)]
        (info "response : " rsp)
        rsp)
      {:status 404})))


;; =============================================================================
;; Admin pipelines
;; =============================================================================

;; projects
;;;;;;;;;;;

(defn projects []
  (assoc json :body (txco/js-> (keys @state))))

(defn project [id]
  (assoc json :body (txco/js-> ((keyword id) @state))))

(defn project-usage [id port]
  (assoc json :body
    (txco/js-> (txc/curly-analysis-> id ((keyword id) @state) port))))

(defn del-proj [id]
  (reset! state (dissoc @state (keyword id)))
  (delete-file (str id ".edn"))
  {:status 204})

(def del-proj-handled (handler del-proj handle-proj-del-error))

(defn put-projects [req]
  (let [file ((:params req) "file")
        data (edn/read-string (slurp (:tempfile file)))]
    (reset! state (merge @state data))
    (doseq [d data]
      (spit (str (name (key d)) ".edn") (pr-str {(key d) (val d)})))
    (projects)))

(defn delete-proj-errors [project]
  (reset! state (ib/dissoc-in @state [(keyword project) :errors :status]))
  {:status 204})

(defn put-proj-error [proj err]
  (reset! state
    (update-in @state [(keyword proj) :errors :status] conj (txco/int-> err)))
  {:status 204})

(defn put-proj-error-prob [proj prob]
  (reset! state
    (assoc-in @state [(keyword proj) :errors :probability] (txco/int-> prob)))
  {:status 204})


;; projects documentation
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn projects-docs [] (txdocs/projects-template (keys @state)))

(defn project-docs [id port]
  (txdocs/project-template id (txan/analysis-> id ((keyword id) @state) port)))

(l/defdocument project-index (file "public/html/index.html") []
  (l/id="project-version") (l/content (txdocs/get-version)))

(l/defdocument project-api (file "public/html/api.html") []
  (l/id="project-version") (l/content (txdocs/get-version)))

(l/defdocument project-documentation (file "public/html/documentation.html") []
  (l/id="project-version") (l/content (txdocs/get-version)))

(l/defdocument project-road (file "public/html/roadmap.html") []
  (l/id="project-version") (l/content (txdocs/get-version)))


;; service status
;;;;;;;;;;;;;;;;;

(defn status [] (assoc json :body (txco/js-> {"status" "ok"})))
