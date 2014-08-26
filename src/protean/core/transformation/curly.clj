(ns protean.core.transformation.curly
  "Uses output from the analysis transformations to generate a curl
   command structure."
  (:require [clojure.string :as stg]
            [clojure.set :as st]
            [ring.util.codec :as cod]
            [cheshire.core :as jsn]
            [protean.core.codex.examples :as e]
            [protean.core.transformation.analysis :as txan]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn curly-method-> [entry payload]
  (if (= (:method entry) :get)
    payload
    (str payload " -X " (stg/upper-case (name (:method entry))))))

(defn curly-headers-> [entry payload]
  (let [hstr (map #(str " -H '" (key %) ": " (val %) "'") (:headers entry))]
    (str payload (apply str hstr))))

(defn curly-form-> [entry payload]
  (if-let [f (:form-params entry)]
    (str payload " --data '" (stg/join "&" (map #(str (key %) "=" (val %)) f))
      "'")
    payload))

(defn curly-body-> [entry payload]
  (if-let [b (:body-keys entry)]
    (str payload " -H 'Content-type: application/json'" " --data '"
      (jsn/generate-string b) "'")
    payload))

(defn curly-uri-> [entry payload] (str payload " '" (:uri entry)))

(defn curly-query-params-> [{:keys [query-params] :as entry} payload]
  (println "query params in curly : " query-params)
  (println "swapped qp : " (e/holders-swap query-params entry))
  (if-let [rp (e/holders-swap query-params entry)]
    (if (empty? rp)
      (str payload "'")
      (str payload "?" (stg/join "&" (map #(str (key %) "="
        (cod/form-encode (val %))) rp)) "'"))
    (str payload "'")))

(defn curly-postprocess-> [s1 s2 payload] (stg/replace payload s1 s2))

(defn curly-> [entry]
  (println "entry : " entry)
  (->> "curl -v"
       (curly-method-> entry)
       (curly-headers-> entry)
       (curly-form-> entry)
       (curly-body-> entry)
       (curly-uri-> entry)
       (curly-query-params-> entry)
       (curly-postprocess-> "*" "1")
       (curly-postprocess-> "psv+" "XYZ")))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn curly-analysis-> [host port codices service]
  (let [corpus {:locs [service]}
        analysed (txan/analysis-> host port codices corpus)]
    (map #(curly-> %) analysed)))
