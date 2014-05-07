(ns protean.transformations.analysis
  "Creates a datastructure which can be used in subsequent pipelines.
   Lowest common denomiator language describing a specification for a
   request/response."
  (:require [clojure.string :as stg]
            [clojure.set :as st]
            [clojure.data.xml :as xml]
            [ring.util.codec :as cod])
  (:import java.net.InetAddress))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn method-> [resource]
  (if-let [method (:method (:req (:spec resource)))]
    {:method method}
    {:method :get}))

(defn assoc-tx->
  "Extracts out-k out of path and assocs to payload as in-k."
  [resource out-k in-k payload]
  (if-let [ext-out (out-k (:req (:spec resource)))]
    (assoc payload in-k ext-out)
    payload))

(defn doc-> [resource payload]
  (if-let [doc (:doc (:spec resource))]
    (assoc payload :doc doc)
    payload))

(defn uri-> [{:keys [svc path]} host port payload]
  (let [uri (str "http://" host ":" port "/" (name svc) "/" path)]
    (assoc payload :uri uri)))

(defn codex-rsp-> [resource payload]
  (assoc payload :codex
    {:body-res (get-in resource [:spec :rsp :body-res])
     :success-code (get-in resource [:spec :rsp :success-code])}))

(defn analyse-> [resource host port]
  (->> (method-> resource)
       (assoc-tx-> resource :headers :headers)
       (assoc-tx-> resource :form :form-keys)
       (assoc-tx-> resource :body :body-keys)
       (uri-> resource host port)
       (assoc-tx-> resource :req-params :req-params)
       (doc-> resource)
       (codex-rsp-> resource)))

(defn- encode [svc path spec] {:svc svc :path path :spec spec})

(defn- combi-paths [codices combi]
  (let [svc (keyword (first combi)) paths (rest combi)]
    (map #(encode svc % (get-in codices [svc :paths %])) paths)))

(defn- svc-paths [codices svc]
  (let [kv (get-in codices [(keyword svc) :paths])]
    (map #(encode svc (key %) (val %)) kv)))

; TODO: replace my empty list guards conditional with nil and empty collection functoinality in my auxilliary functions
(defn- paths-range [codices locs]
  (let [groups ((juxt filter remove) #(= (count (stg/split % #" ")) 1) locs)
        combi (map #(stg/split (apply str %) #" ") (second groups))
        combi-paths (if (empty? (second groups))
                      '()
                      (flatten
                        (reduce conj (map #(combi-paths codices %) combi))))
        svc-paths (reduce conj (map #(svc-paths codices %) (first groups)))]
    (concat combi-paths svc-paths)))

(defn- paths
  "Get all service paths or specified combinations of service/path | service."
  [codices locs]
  (if locs
    (paths-range codices locs)
    (reduce conj (map #(:paths (second %)) codices))))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn analysis-> [host port codices corpus]
  (let [p (paths codices (get corpus "locs"))]
    (map #(analyse-> % host port) p)))
