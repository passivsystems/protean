(ns protean.core.transformation.curly
  "Uses output from the analysis transformations to generate a curl
   command structure."
  (:require [clojure.string :as s]
            [clojure.set :as st]
            [ring.util.codec :as e]
            [cheshire.core :as jsn]
            [protean.core.codex.placeholder :as p]
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
  (if-let [f (d/get-in-tree tree [:req :form-params])]
    (str payload " --data '" (s/join "&" (map #(str (key %) "=" (val %)) f))
      "'")
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
              (p/holders-swap p/holder-swap-exp entry true :exp tree)
              (p/holders-swap p/holder-swap-gen entry true :vars tree))]
      (if (vector? res) (first res) res))
    nil))

(defn- curly-query-params-> [entry tree payload]
  (let [phs (d/get-in-tree tree [:req :query-params :required])
        query (if-let [rp (translate-query-params phs entry tree)]
    (if (not (empty? rp))
      (if (d/qp-json? tree)
        (str "?q=" (rp "q"))
        (str "?" (s/join "&" (map #(str (key %) "=" (e/form-encode (val %))) rp))))))]
      (str payload query)))

(defn- curly-replace-> [s1 s2 payload] (s/replace payload s1 s2))

(defn curly-> [{:keys [tree method uri] :as entry}]
  (->> "curl -v"
       (curly-method-> method)
       (curly-headers-> tree)
       (curly-form-> tree)
       (curly-body-> tree)
       (curly-literal-> " '")
       (curly-literal-> uri)
       (curly-query-params-> entry tree)
       (curly-literal-> "'")
       (curly-replace-> "*" "1")
       (curly-replace-> "psv+" "XYZ"))) ; TODO use generate?


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn curly-analysis-> [analysed]
  (map #(curly-> %) analysed))
