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

(defn- translate [phs type tree]
  (if phs
    (let [res
          (-> phs
              (ph/holders-swap ph/holder-swap-exp type :exp tree)
              ; Note, placeholder generation will be different each time we request them
              ; also may not be url friendly (though we will encode them)
              (ph/holders-swap ph/holder-swap-gen type :vars tree)
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

(defn- curly-form-> [tree payload]
  (let [phs (d/get-in-tree tree [:req :form-params])
        data (if-let [rp (translate phs :form-params tree)]
               (str " --data '" (s/join "&" (map #(str (key %) "=" (val %)) rp)) "'")
               "")]
    (str payload data)))

(defn- curly-body-> [tree payload]
  (let [content-type-req (d/get-in-tree tree [:req :headers "Content-Type"])
        phs (d/get-in-tree tree [:req :body])
        b (translate phs :body tree)
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

(defn- curly-query-params-> [tree payload]
  (let [phs (d/get-in-tree tree [:req :query-params :required])
        query (if-let [rp (translate phs :query-params tree)]
          (if (not (empty? rp))
            (if (d/qp-json? tree)
              (str "?q=" (rp "q"))
              (str "?" (s/join "&" (map #(str (key %) "=" (e/form-encode  (val %))) rp))))))]
      (str payload query)))

(defn- curly-replace-> [s1 s2 payload]
  (s/replace payload s1 s2))

(defn curly-> [{:keys [tree method uri]}]
  (->> "curl -v"
       (curly-method-> method)
       (curly-headers-> tree)
       (curly-form-> tree)
       (curly-body-> tree)
       (curly-literal-> " '")
       (curly-uri-> uri)
       (curly-query-params-> tree)
       (curly-literal-> "'"))) 


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn curly-analysis-> [analysed]
  (map #(curly-> %) analysed))
