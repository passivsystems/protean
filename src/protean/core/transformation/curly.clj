(ns protean.core.transformation.curly
  "Uses output from the analysis transformations to generate a curl
   command structure."
  (:require [clojure.string :as s]
            [ring.util.codec :as e]
            [protean.config :as conf]
            [protean.api.codex.placeholder :as ph]
            [protean.core.transformation.request :as r]))

;; =============================================================================
;; Helper functions
;; =============================================================================

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
  (let [query (when (and query-params (not (empty? query-params)))
                (->> query-params
                     (map #(str (key %) "=" (e/form-encode  (val %))))
                     (s/join "&")
                     (str "?")
                     e/url-decode))]
    (str payload query)))

(defn curly-flatten-> [payload]
  (if (conf/curl-flatten?) (s/trim (s/replace payload #"\s+" " ")) payload))

(defn curly-request-> [request]
  (->> (str "curl " (conf/curl-option))
       (curly-method-> request)
       (curly-headers-> request)
       (curly-form-> request)
       (curly-body-> request)
       (curly-literal-> " '")
       (curly-uri-> request)
       (curly-query-params-> request)
       (curly-literal-> "'")
       (curly-flatten->)))

(defn curly-entry-> [{:keys [tree method uri]}]
  (let [request-template (r/prepare-request method uri tree :include-optional true)
        ; Note, placeholder generation will be different each time we request them
        ; also may not be url friendly (though we will encode them)
        request (ph/swap request-template tree {} :gen-all true)]
    (curly-request-> request)))

;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn curly-analysis-> [analysed]
  (map #(curly-entry-> %) analysed))
