(ns protean.server.pipeline
  (:require [clojure.main :as m]
            [clojure.string :as str]
            [protean.core :as api-core]
            [protean.config :as conf]
            [protean.api.protocol.http :as h]
            [protean.api.transformation.coerce :as co]
            [protean.core.transformation.curly :as txc]
            [protean.api.codex.reader :as r]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.request :as req])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)])
  (:import java.io.IOException))

;; =============================================================================
;; Helper functions and data
;; =============================================================================

(def json {:headers {h/ctype h/jsn "Access-Control-Allow-Origin" "*"}})

(def file-codices (atom {}))
(def file-sims (atom {}))
(def paths (atom {}))

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

(defn- sim-cfg
  "returns a merge of the first two levels of sim config (global and svc)"
   [sim svc] (merge (:sim-cfg sim) (get-in sim [svc :sim-cfg])))

;; =============================================================================
;; Service pipelines
;; =============================================================================

(defn api
  [{:keys [request-method uri query-string] :as req}]
  (debug "request:" req)
  (let [rsp (api-core/sim-rsp (conf/protean-home) req @paths (vals @file-sims))
        {:keys [status headers body]} rsp
        ct (get headers "Content-Type" "")
        show? (some #(str/starts-with? ct %) [h/txt h/html h/xml h/jsn-simple])]
    (info (str/upper-case (name request-method))
          (if query-string (str uri "?" query-string) uri)
          "responded with"
          "\n  status:" status
          "\n  headers:" headers
          "\n  body:" (when body (if show? body "<suppressed>")))
    rsp))

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
  (swap! paths dissoc svc)
  {:status 204})

(def del-service-handled (handler del-service handle-error))

(defn- reload-paths
  []
  (reset! paths {})
  (doseq [codex (vals @file-codices)]
    (doseq [{:keys [svc path method tree]} (p/paths codex (custom-keys codex))]
      (swap! paths assoc-in [svc path method] tree))))

(defn unload-codex [f]
  (let [codex (@file-codices (.getName f))]
    (swap! file-codices dissoc (.getName f))
    (reload-paths)
    (first (custom-keys codex))))

(defn load-codex [f]
  (let [codex (r/read-codex (conf/protean-home) f)]
    (swap! file-codices assoc (.getName f) codex)
    (reload-paths)
    (first (custom-keys codex))))

(defn put-services [req]
  (let [file ((:params req) "file")]
    (load-codex (:tempfile file)))
  (services))

;; sims
;;;;;;;;;;;

(defn sims-names [] (assoc json :body (co/jsn (sort (keys @file-sims)))))

(defn del-sim [f]
  (swap! file-sims dissoc f)
  {:status 204})

(def del-sim-handled (handler del-sim handle-error))

(defn unload-sim [f]
  (let [sim (@file-sims (.getName f))
        svc (first (custom-keys sim))]
    (swap! file-sims dissoc (.getName f))
    (str svc (when-let [c (sim-cfg sim svc)] (str " (sim config: " c ")")))))

(defn load-sim [f]
  (let [sim (m/load-script (.getPath f))
        svc (first (custom-keys sim))]
    (swap! file-sims assoc (.getName f) sim)
    (str svc (when-let [c (sim-cfg sim svc)] (str " (sim config: " c ")")))))

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
