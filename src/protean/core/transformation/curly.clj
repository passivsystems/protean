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

(defn- curly-method-> [method payload]
  (if (= method :get)
    payload
    (str payload " -X " (s/upper-case (name method)))))

(defn- curly-headers-> [tree payload]
  (let [hstr (map #(str " -H '" (key %) ": " (val %) "'") (d/get-in-tree tree [:req :headers]))]
    (str payload (apply str hstr))))

(defn- curly-form-> [tree payload]
  (if-let [f (d/get-in-tree tree [:req :form-params])
           param-pairs (map #(str (key %) "=" (val %)) f)]
    (str payload " --data '" (s/join "&" param-pairs) "'")
    payload))

(defn- curly-body-> [tree payload]
  (let [content-type-req (d/get-in-tree tree [:req :headers "Content-Type"])]
  (cond
    (= content-type-req h/xml)
      (if-let [b (d/get-in-tree tree [:req :body])]
        (str payload " --data '" (c/str-xml b) "'")
        payload)
    (or (not content-type-req) (= content-type-req h/jsn-simple))
      (if-let [b (d/get-in-tree tree [:req :body])]
        (if (map? b)
          (str payload " -H '" h/ctype ": " h/jsn-simple "' --data '"
            (jsn/generate-string b) "'")
          (str payload " -H '" h/ctype ": " h/jsn-simple "' --data '"
            (jsn/generate-string (first b)) "'"))
        payload)
    (= content-type-req h/txt)
      (if-let [b (d/get-in-tree tree [:req :body])]
        (str payload " --data '"
           (jsn/generate-string (first b)) "'")
        payload)
    ;:else unknown content-type
)))

(defn- curly-literal-> [s payload] (str payload s))

(defn- translate-query-params [phs entry tree]
  (if phs
    (let [res
          (-> phs
              (ph/holders-swap ph/holder-swap-exp entry :query-params :exp tree)
              (ph/holders-swap ph/holder-swap-gen entry :query-params :vars tree)
;              (ph/replace-placeholders "XYZ") ; TODO generated values are not URL friendly.. before just used "XYZ"...
           )]
      (if (vector? res) (first res) res))
    nil))


(defn- curly-uri-> [uri payload]
  (curly-literal-> (ph/replace-placeholders uri "1") payload))

(defn- curly-query-params-> [entry tree payload]
  (let [phs (d/get-in-tree tree [:req :query-params :required])
        query (if-let [rp (translate-query-params phs entry tree)]
          (if (not (empty? rp))
            (if (d/qp-json? tree)
              (str "?q=" (rp "q"))
              (str "?" (s/join "&" (map #(str (key %) "=" (e/form-encode  (val %))) rp))))))]
      (str payload query)))

(defn- curly-replace-> [s1 s2 payload]
  (s/replace payload s1 s2))

(defn curly-> [{:keys [tree method uri] :as entry}]
  (println "\ncurly" method uri)
  (->> "curl -v"
       (curly-method-> method)
       (curly-headers-> tree)
       (curly-form-> tree)
       (curly-body-> tree)
       (curly-literal-> " '")
       (curly-uri-> uri)
       (curly-query-params-> entry tree)
       (curly-literal-> "'"))) 


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn curly-analysis-> [analysed]
  (map #(curly-> %) analysed))
