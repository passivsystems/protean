(ns protean.core.transformation.analysis
  "Accepts codices and a corpus of information describing what to do.
   Creates a datastructure which can be used in subsequent pipelines.
   Lowest common denomiator language describing a specification for a
   request/response.

   Codices here may be the entire body of codices Protean includes.

   The corpus may then contain instructions including the locations 'locs'
   in the API surface area to range over in order to build the analysis.

   If no range is specified every resource under every service Protean knows
   about will be included in the analysis.

   If the corpus includes a 'locs' specification 'sample get/simple' an
   analysis will be generated for one 'sample' service resource like:

     {
       :codex {:body-res nil :success-code nil :content-type nil}
       :doc 'Simplext example of a resource - doc is optional'
       :uri 'http://localhost:3000/sample/get/simple'
       :method :get
     }
  "
  (:require [protean.core.transformation.paths :as p]
            [protean.core.codex.document :as d]
            [clojure.pprint]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- method-> [resource]
  (if-let [method (:method resource)]
    {:method method}
    {:method :get}))

(defn- uri-> [{:keys [svc path]} host port payload]
  (let [uri (str "http://" host ":" port "/" (name svc) "/" path)]
    (assoc payload :uri uri)))

;TODO since tree has everything we need, we should just read from that directly (using codex fully qualified path).
;For now, adding tree to analysis result so available further down the pipeline
(defn analyse-> [{:keys [tree] :as entry} host port]
  (->> (method-> entry)
       (uri-> entry host port)
       (d/assoc-tree-item-> tree [:req :headers] [:headers])
       (d/assoc-tree-item-> tree [:req :form-params] [:form-params])
       (d/assoc-tree-item-> tree [:req :body] [:body-keys])
       (d/assoc-tree-item-> tree [:req :vars] [:vars])
       (d/assoc-tree-item-> tree [:req :query-params] [:query-params])
       (d/assoc-tree-item-> tree [:doc] [:doc])
       (d/assoc-tree-item-> tree [:description] [:desc]) ; (not used internally - required by silk? Should move to probe's pre-silk adjustments?) (input: tree, output -> silk done there?)
       ; codex-resp:
       (d/assoc-tree-item-> tree [:req :query-params-type] [:codex :q-params-type])
       (d/assoc-tree-item-> tree [:rsp :body] [:codex :body])
       (d/assoc-tree-item-> tree [:rsp :body-res] [:codex :body-res])
       (d/assoc-tree-item-> tree [:rsp :status] [:codex :success-code])
       (d/assoc-tree-item-> tree [:rsp :errors :status] [:codex :errors])
       (d/assoc-tree-item-> tree [:req :headers "Content-Type"] [:codex :content-type-req])
       (d/assoc-tree-item-> tree [:rsp :headers "Content-Type"] [:codex :content-type])
       (d/assoc-tree-item-> tree [:rsp :headers] [:codex :headers])
       ; and preserve tree..
       (d/assoc-item-> entry [:tree] [:tree])))
; Note: probe dumps this result (with a few adjustments) to file for feeding into silk - includes tree...


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn analysis-> [host port codices corpus]
  (let [p (p/paths-> codices (:locs corpus))]
    (map #(analyse-> % host port) p)))
