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
            [protean.core.protocol.http :as h]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- translate [phs entry type tree]
  (println "translate" type phs)
  (if phs
    (let [res
          (-> phs
              (ph/holders-swap ph/holder-swap-exp entry type :exp tree)
              (ph/holders-swap ph/holder-swap-gen entry type :vars tree)
;              (ph/replace-placeholders "XYZ") ; TODO generated values are not URL friendly.. before just used "XYZ"...
           )]
      (if (vector? res) (first res) res))
    nil))

(defn- curly-method-> [method payload]
  (if (= method :get)
    payload
    (str payload " -X " (s/upper-case (name method)))))

(defn- curly-headers-> [tree payload]
  (let [hstr (map #(str " -H '" (key %) ": " (val %) "'") (d/get-in-tree tree [:req :headers]))]
    (str payload (apply str hstr))))

(defn- curly-form-> [entry tree payload]
  (let [phs (d/get-in-tree tree [:req :form-params])
        data (if-let [rp (translate phs entry :form-params tree)]
               (str " --data '" (s/join "&" (map #(str (key %) "=" (val %)) rp)) "'")
               "")]
    (str payload data)))

(defn- curly-body-> [entry tree payload]
  (let [content-type-req (d/get-in-tree tree [:req :headers "Content-Type"])
        phs (d/get-in-tree tree [:req :body])
        b (translate phs entry :body tree)
        data (cond
               (= content-type-req h/xml)
                 (if b (str " --data '" (c/str-xml b) "'") "")
               (or (not content-type-req) (= content-type-req h/jsn-simple))
                 (if b
                   (if (map? b)
                     (str " -H '" h/ctype ": " h/jsn-simple "' --data '" (jsn/generate-string b) "'")
                     (str " -H '" h/ctype ": " h/jsn-simple "' --data '" (jsn/generate-string (first b)) "'"))
                   "")
               (= content-type-req h/txt)
                 (if b (str " --data '" (jsn/generate-string (first b)) "'") "")
               :else "")]  ;unknown content-type             
    (str payload data)))

(defn- curly-literal-> [s payload] (str payload s))



(defn- curly-uri-> [uri payload]
  (curly-literal-> (ph/replace-placeholders uri "1") payload))

(defn- curly-query-params-> [entry tree payload]
  (let [phs (d/get-in-tree tree [:req :query-params :required])
        query (if-let [rp (translate phs entry :query-params tree)]
          (if (not (empty? rp))
            (if (d/qp-json? tree)
              (str "?q=" (rp "q"))
              (str "?" (s/join "&" (map #(str (key %) "=" (e/form-encode  (val %))) rp))))))]
      (str payload query)))

(defn- curly-replace-> [s1 s2 payload]
  (s/replace payload s1 s2))

(defn curly-> [{:keys [tree method uri] :as entry}]
  (->> "curl -v"
       (curly-method-> method)
       (curly-headers-> tree)
       (curly-form-> entry tree)
       (curly-body-> entry tree)
       (curly-literal-> " '")
       (curly-uri-> uri)
       (curly-query-params-> entry tree)
       (curly-literal-> "'"))) 


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn curly-analysis-> [analysed]
  (map #(curly-> %) analysed))
