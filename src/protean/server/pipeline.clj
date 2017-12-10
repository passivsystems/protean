(ns protean.server.pipeline
  (:require [clojure.core.incubator :as ib]
            [clojure.main :as m]
            [protean.core :as api-core]
            [protean.config :as conf]
            [protean.api.protocol.http :as h]
            [protean.api.transformation.coerce :as co]
            [protean.core.transformation.curly :as txc]
            [protean.api.codex.reader :as r]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.request :as req])
  (:use [clojure.java.io :refer [file]]
        [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(def json {:headers {h/ctype h/jsn "Access-Control-Allow-Origin" "*"}})

(def file-codices (atom nil))
(def file-sims (atom nil))
(def paths (atom {}))
(def sims (atom {}))

(defn- log-request [{:keys [request-method uri query-params] :as req}]
  (debug "request is : " req)
  (info "method: " request-method ", uri: " uri ", query-params: " query-params))

(defn handler
  [f & handlers]
  (reduce (fn [handled h] (partial h handled)) f (reverse handlers)))

(defn handle-error
  [f & args]
  (try
    (apply f args)
    (catch IOException ioex
      (error (.getMessage ioex))
      {:status 500})))

(defn- custom-keys
  "returns only keys which are not keywords"
  [c] (seq (remove keyword? (keys c))))

(defn- body [req-body]
  (let [rbody (slurp req-body)] (if (not-empty rbody) (co/clj rbody) nil)))

(defn- prepare-analysis [svc host]
  (let [port (conf/sim-port)
        to-map (fn [path]
            (let [endpoint (key path)
                  methods (keys (val path))]
             (for [method methods]
               {:method method
                 :uri (p/uri host port svc endpoint)
                 :tree (get-in @paths [svc endpoint method])})))]
    (mapcat to-map (get-in @paths [svc]))))

(defn- prep-request [{:keys [tree method uri]}]
  (req/prepare-request method uri tree :include-optional true))

(defn- custom-keys
  "returns only keys which are not keywords"
  [c] (seq (remove keyword? (keys c))))

;; =============================================================================
;; Service pipelines
;; =============================================================================

(defn api [req]
  (log-request req)
  (api-core/sim-rsp (conf/protean-home) req @paths @sims))


;; =============================================================================
;; Admin pipelines
;; =============================================================================

;; services
;;;;;;;;;;;

(defn services [] (assoc json :body (co/jsn (sort (keys @paths)))))

; TODO the result of this function is currently affected by collisions between
; file-codices - should use @paths instead
(defn service
  [svc]
  (assoc json :body (co/jsn (first (filter #(% svc) (vals @file-codices))))))

(defn service-analysis [svc host]
  (let [analysed (prepare-analysis svc host)]
    (assoc json :body (co/jsn (map #(prep-request %) analysed)))))

(defn service-usage [svc host]
  (let [analysed (prepare-analysis svc host)]
    (assoc json :body (co/jsn (txc/curly-analysis-> analysed)))))

(defn del-service [svc]
  (reset! paths (ib/dissoc-in @paths [svc]))
  {:status 204})

(def del-service-handled (handler del-service handle-error))

(defn- reload-paths
  []
  (reset! paths {})
  (doseq [codex (map #(first (vals %)) @file-codices)]
    (doseq [{:keys [svc path method tree]} (p/paths codex (custom-keys codex))]
      (swap! paths assoc-in [svc path method] tree))))

(defn unload-codex [f]
  (let [codex (first (remove nil? (map #(% (.getPath f)) @file-codices)))]
    (reset! file-codices (remove #(% (.getPath f)) @file-codices))
    (reload-paths)
    (first (custom-keys codex))))

(defn load-codex [f]
  (reset! file-codices (remove #(% (.getPath f)) @file-codices))
  (let [codex (r/read-codex (conf/protean-home) f)]
    (reset! file-codices (conj @file-codices {(.getPath f) codex}))
    (reload-paths)
    (first (custom-keys codex))))

(defn put-services [req]
  (let [file ((:params req) "file")]
    (load-codex (:tempfile file)))
  (services))

;; sims
;;;;;;;;;;;

(defn sims-names [] (assoc json :body (co/jsn (sort (custom-keys @sims)))))

(defn del-sim [svc]
  (reset! sims (ib/dissoc-in @sims [svc]))
  {:status 204})

(def del-sim-handled (handler del-sim handle-error))

(defn- reload-sims
  []
  (reset! sims {})
  (doseq [sim (map #(first (vals %)) @file-sims)]
    (reset! sims (merge @sims sim))))

(defn unload-sim [f]
  (let [sim (first (remove nil? (map #(% (.getPath f)) @file-sims)))]
    (reset! file-sims (remove #(% (.getPath f)) @file-sims))
    (reload-sims)
    (str (first (custom-keys sim))
         (when-let [cfg (:sim-cfg sim)] (str " (sim config: " cfg ")")))))

(defn load-sim [f]
  (reset! file-sims (remove #(% (.getPath f)) @file-sims))
  (let [sim (m/load-script (.getPath f))]
    (reset! file-sims (conj @file-sims {(.getPath f) sim}))
    (reload-sims)
    (str (first (custom-keys sim))
         (when-let [cfg (:sim-cfg sim)] (str " (sim config: " cfg ")")))))

(defn put-sims [req]
  (let [file ((:params req) "file")]
    (load-sim (:tempfile file)))
  (sims-names))


;; service status
;;;;;;;;;;;;;;;;;

(defn status [] (assoc json :body (co/jsn {"status" "ok"})))


;; service version
;;;;;;;;;;;;;;;;;;

(defn version [v] (assoc json :body (co/jsn {"version" v})))
