(ns protean.core.transformation.curly
  "Uses output from the analysis transformations to generate a curl
   command structure."
  (:require [clojure.string :as stg]
            [clojure.set :as st]
            [ring.util.codec :as e]
            [cheshire.core :as jsn]
            [protean.core.transformation.coerce :as c]
            [protean.core.protocol.http :as h]
            [protean.core.codex.placeholder :as p]
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
  (cond
    (= (get-in entry [:codex :content-type-req]) h/xml)
      (if-let [b (:body-keys entry)]
        (str payload " --data '" (c/str-xml b) "'")
        payload)
      (or (not (get-in entry [:codex :content-type-req]))
          (= (get-in entry [:codex :content-type-req]) h/jsn-simple))
      (if-let [b (:body-keys entry)]
           (if (map? b)
             (str payload " -H '" h/ctype ": " h/jsn-simple "' --data '"
                  (jsn/generate-string b) "'")
             (str payload " -H '" h/ctype ": " h/jsn-simple "' --data '"
                  (jsn/generate-string (first b)) "'"))
           payload)
    (= (get-in entry [:codex :content-type-req]) h/txt)
      (if-let [b (:body-keys entry)]
        (str payload " --data '"
             (jsn/generate-string (first b)) "'")
        payload)))

(defn curly-uri-> [entry payload] (str payload " '" (:uri entry)))

(defn- translate [phs entry k]
  (if phs
    (let [res
          (-> phs
              (p/holders-swap p/holder-swap-exp entry k :exp)
              (p/holders-swap p/holder-swap-gen entry k :vars))]
      (if (vector? res) (first res) res))
    nil))

(defn curly-query-params-> [{:keys [query-params] :as entry} payload]
  (let [phs (:required query-params)]
    (if-let [rp (translate phs entry :query-params)]
      (if (empty? rp)
        (str payload "'")
        (if (= (get-in entry [:codex :q-params-type]) :json)
          (str payload "?q=" (rp "q") "'")
          (str payload "?"
               (stg/join "&"
               (map #(str (key %) "=" (e/form-encode (val %))) rp)) "'")))
      (str payload "'"))))

(defn curly-postprocess-> [s1 s2 payload] (stg/replace payload s1 s2))

(defn curly-> [entry]
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
