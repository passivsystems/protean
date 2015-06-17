(ns protean.core.transformation.curly
  "Uses output from the analysis transformations to generate a curl
   command structure."
  (:require [clojure.string :as s]
            [clojure.set :as st]
            [ring.util.codec :as e]
            [cheshire.core :as jsn]
            [protean.core.codex.placeholder :as ph]
            [protean.core.codex.document :as d]
            [protean.core.transformation.coerce :as c]
            [protean.core.protocol.http :as h]
            [protean.core.protocol.protean :as p]
            [protean.core.transformation.request :as r]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- translate [phs tree]
  (if phs
    ; Note, placeholder generation will be different each time we request them
    ; also may not be url friendly (though we will encode them)
    (ph/swap phs tree {})))

(defn- curly-method-> [{:keys [method] :as request} payload]
  (if (= method :get)
    payload
    (str payload " -X " (s/upper-case (name method)))))

(defn- curly-headers-> [{:keys [headers] :as request} payload]
  (let [hstr (if headers (apply str (map #(str " -H '" (key %) ": " (val %) "'") headers)))]
    (str payload hstr)))

(defn- curly-form-> [{:keys [form-params] :as request} payload]
  (let [data (if form-params (str " --data '" (s/join "&" (map #(str (key %) "=" (val %)) form-params)) "'"))]
    (str payload data)))

(defn- curly-body-> [{:keys [body] :as request} payload]
  (let [data (if body (str " --data '" body "'"))]
    (str payload data)))

(defn- curly-literal-> [s payload] (str payload s))

(defn- curly-uri-> [{:keys [uri] :as request} payload]
  (str payload (e/url-decode uri)))

(defn- curly-query-params-> [{:keys [query-params] :as request} payload]
  (let [query (if (and query-params (not (empty? query-params)))
                (->> query-params
                     (map #(str (key %) "=" (e/form-encode  (val %))))
                     (s/join "&")
                     (str "?")
                     e/url-decode))]
    (str payload query)))

(defn curly-request-> [request]
  (->> "curl -v"
       (curly-method-> request)
       (curly-headers-> request)
       (curly-form-> request)
       (curly-body-> request)
       (curly-literal-> " '")
       (curly-uri-> request)
       (curly-query-params-> request)
       (curly-literal-> "'")))

(defn curly-entry-> [{:keys [tree method uri]}]
  (let [request-template (r/prepare-request method uri tree)
        request (ph/swap request-template tree {})]
    (curly-request-> request)))

;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn curly-analysis-> [analysed]
  (map #(curly-entry-> %) analysed))
