(ns protean.core.command.testprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
            [clojure.data :refer [diff]]
            [ring.util.codec :as cod]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as dsk]
            [silk.cli.api :as silk]
            [protean.core.codex.document :as d]
            [protean.core.codex.placeholder :as ph]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.coerce :as co]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.curly :as c]
            [protean.core.command.test :as t]
            [protean.core.command.probe :as pb]
            [loom.graph :as lg]
            [loom.alg :as la]
            [loom.attr :as lat]
            [loom.io :as li])
  (:import java.io.File java.net.URI java.util.UUID))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- hlr [t] (println (aa/bold-red t)))
(defn- hlg [t] (println (aa/bold-green t)))

;; =============================================================================
;; Probe config
;; =============================================================================

(defn- show-test [level]
  (cond
    (= level 1) (hlr "ʘ‿ʘ I'm too young to die")
    (= level 2) (hlr "⊙︿⊙ Hey not too rough")
    (= level 3) (hlr "ミ●﹏☉ミ Hurt me plenty")
    (= level 4) (hlr "✖_✖ Ultra violence")))

(defmethod pb/config :test [_ corpus]
  (show-test (get-in corpus [:config "test-level"] 1))
  (hlg "building probes"))

;; =============================================================================
;; Probe construction
;; =============================================================================

(defn- swap [ph tree bag]
  (-> ph
     (ph/holder-swap ph/holder-swap-bag bag)
     (ph/holder-swap ph/holder-swap-gen tree)
     (ph/holder-swap ph/holder-swap-exp tree)))

(defn- copy-and-swap [payload tree bag source-keys target-keys]
  (if-let [ph (d/get-in-tree tree source-keys)]
    (assoc-in payload target-keys (swap ph tree bag))
    payload))

(defn- content-type-> [payload method]
  (if (and (some #{method} [:post :put])
           (not (get-in payload [:headers "Content-Type"])))
    (assoc-in payload [:headers h/ctype]  h/jsn-simple)
    payload))

(defn- uri [host port {:keys [svc path] :as entry}]
  (p/uri host port svc path))

(defn- prepare-request 
  "Translate placeholders when visiting real nodes."
  [uri {:keys [method tree] :as entry} bag]
  (let [parsed-uri (:uri (swap {:uri uri} tree bag))] ; wrapping and unwrapping uri in map to reuse holder-swap
    (-> {:method method :uri parsed-uri}
        (copy-and-swap tree bag [:req :query-params :required] [:query-params])
        (copy-and-swap tree bag [:req :query-params :optional] [:query-params]) ; TODO only include when (corpus) test level is 2?
        (copy-and-swap tree bag [:req :form-params] [:form-params])
        (copy-and-swap tree bag [:req :headers] [:headers])
        (copy-and-swap tree bag [:req :body] [:body])
        (update-in [:body] co/js) ; TODO check content-type and set as appropriate..
        (content-type-> method))))

(defn- uri-> [{:keys [svc path] :as entry} host port]
  (assoc entry :uri ))

(defn- collect-params [m]
  (let [l (cond (map? m) (vals m) (list? m) m :else (list m))]
    (mapcat (fn [v] (if (string? v) (map second (ph/holder? v))
                      (if v (collect-params v))))
            l)))

(defn- inputs [uri tree]
  (distinct (concat
    (collect-params uri)
    (collect-params (d/get-in-tree tree [:req :query-params :required]))
    (collect-params (d/get-in-tree tree [:req :query-params :required]))
    (collect-params (d/get-in-tree tree [:req :query-params :optional])) ; TODO only include when (corpus) test level is 2?
    (collect-params (d/get-in-tree tree [:req :form-params]))
    (collect-params (d/get-in-tree tree [:req :headers]))
    (collect-params (d/get-in-tree tree [:req :body])))))

(defn- outputs-names [tree]
  (let [res (val (first (d/success-status tree)))]
    (distinct (concat
      (collect-params (get-in res [:headers]))
      (collect-params (get-in res [:body]))))))

(defn- outputs-values [tree response]
  (let [res (val (first (d/success-status tree)))
        f-headers (fn [[k v]]
           (when-let [holder (ph/holder? v)]
             (for [ph (map second holder)]
               (do ;(println "ph:" ph)
                 (when-let [response-value (get-in response [:headers k])]
;                   (println "pulling out" ph "from" response-value "with template" v)
                   (let [diff (diff (char-array v) (char-array response-value))
                         left (stg/join (first diff))
                         right (stg/join (second diff))
                         diff-match (re-matches ph/ph left)]
                     ; note currently only works until first mismatch.
                     ; Which only works if our placeholder is the only placeholder, and is at the end of the string.
                     ; e.g. abc${def} - ok
                     ;      abc${def}ghi - not ok
                     (if (= (second diff-match) ph)
                       [ph right]
                       (println "could not extract" ph "from" response-value "with template" v))))))))
        f-body (fn [[k v]]
           (when-let [holder (ph/holder? v)]
             (let [response-body (get-in response [:body])
                   ct (get-in response [:headers "Content-Type"])]
               (for [ph (map second holder)]
                 (do ;(println "ph:" ph)
                   (cond
                     (= ct h/jsn) (do ; TODO need to support all content types..
                       (let [json (co/clj response-body)]
                         (when-let [response-value (get-in json [k])]
                           ;(println "pulling out" ph "from" response-value "with template" v) ; TODO - currently just returning all response-value
                           [ph response-value])))
                     ))))))]
    ;(println "output-values - headers" (get-in res [:headers]))
    ;(println "output-values - body" (get-in res [:body]))
    (merge
      (into {} (mapcat f-headers (get-in res [:headers])))
      (into {} (mapcat f-body (get-in res [:body]))))))


(defmethod pb/build :test [_ {:keys [locs host port] :as corpus} {:keys [tree] :as entry}]
  (println "building a test probe to visit " (:method entry) ":" locs)
  (let [h (or host "localhost")
        p (or port 3000)
        uri (uri h p entry)
        inputs (inputs uri tree)
        outputs (outputs-names tree)]
    {:entry entry
     :inputs inputs
     :outputs outputs
     :engage (fn [bag res-fn]
      (let [request (prepare-request uri entry bag)
            result (t/test! request)]
        (res-fn result)
        result))
    }))


;; =============================================================================
;; Probe result handlers
;; =============================================================================

(defn- res-simple! [result] (println "doing nothing"))

(defn- res-persist!
  "Persist result in its interim state to a store.
   In this protoype the store is the disk."
  [result]
  ;;(println "TODO: reminder placeholder for persisting results")
  )

;; =============================================================================
;; Probe dispatch
;; =============================================================================

(defn- find-dep [input probe]
  (if (some #{input} (:outputs probe)) probe)
)

(defn- label [probe]
  (let [{:keys [method svc path] :as entry} (:entry probe)]
    (str method " " svc " " path)))

(defn- analyse [g corpus probes probe]
  (let [tree (get-in probe [:entry :tree])
        inputs (:inputs probe)
        outputs (:outputs probe)]
    (println "\n" (label probe))
    (swap! g lg/add-nodes probe)
    (swap! g lat/add-attr probe :label (label probe))

    (println " inputs:")
    (doseq [input inputs]
      (let [seed (get-in corpus [:seed input])
            dependencies (remove #{probe} (remove nil? (map #(find-dep input %) probes)))
            example (d/get-in-tree tree [:vars input :examples])
            gen-type (d/get-in-tree tree [:vars input :type])]

        (println "    " input "- satisifed by")
        (if seed
          (println "      seed:" seed)
          (do
            (if (not (empty? dependencies))
              (do
                (println "      dependencies:" (map label dependencies)) ; TODO may need to default to example/generative-type if forms a cyclical graph
                (doseq [dependency dependencies]
                  (swap! g lg/add-edges [dependency probe])
                  (swap! g lat/add-attr [dependency probe] :label input)
                )
              ))
            (if example
              (println "      example:" example)
              (if gen-type  (println "      generative type:" gen-type)))))))
;          :else (println "    " input "  - NOT SATISFIED"))))


;        (cond
;          seed (println "    " input "  - satisfied by seed:" seed)
;          (not (empty? dependencies)) (println "    " input "  - satisfied by dependencies:" (map label dependencies))
;          example (println "    " input "  - satisfied by example:" example)
;          gen-type (println "    " input "  - satisfied by generative type:" gen-type)
;          :else (println "    " input "  - NOT SATISFIED"))))
    (println " outputs:" outputs)))


(defn- execute [probes bag reses]
  (let [probe (first probes)]
    (if probe
      (let [res ((:engage probe) bag res-persist!)
            outputs (outputs-values (:tree (:entry probe)) (second res))]
        (println (label probe) "\noutputs" outputs "\n")
        (recur (rest probes) (merge outputs bag) (conj reses [(:entry probe) res])))
      reses)))


(defmethod pb/dispatch :test [_ corpus probes]
  (hlg "dispatching probes")
  (let [g (atom (lg/digraph))] ; TODO refactor out atom usage
    (doall (map #(analyse g corpus probes %) probes))
;    (li/view @g) ; uncomment to open image of graph
    (let [bag (get-in corpus [:seed])
          ordered-probes (la/topsort @g)]
      (println "\nexecuting probes in order:\n"
        (stg/join "\n" (map (fn [p] (pr-str (label p) " inputs:" (:inputs p) " outputs:" (:outputs p))) ordered-probes)) "\n")
      (reverse (execute ordered-probes bag (list))))))

;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defn- assess [method status tree]
  (let [expected-status (name (key (first (d/success-status tree))))]
    (if (= (str status) expected-status)
      "pass"
      (str "fail - expected status " expected-status))))

(defmethod pb/analyse :test [_ corpus results]
  (hlg "analysing probe data")
  (doseq [[entry [request response]] results]
;    (println "result - request:" request)
;    (println "result - response:" response)
    (let [method (:method request)
          uri (:uri request)
          status (:status response)
          tree (:tree entry)
          ass (assess method status tree)
          so (if (= ass "pass") (aa/bold-green ass) (aa/bold-red ass))]
      (println "Test : " method " - " uri ", status - " status ": " so)))) ; TODO need to identify if couldnt run cos dependencies not met? (currently exp/gen seem to catch all)
;  (doseq [[method uri mp phs] results]
;    (let [status (:status mp)
;          ass (assess method status phs)
;          so (if (or (ph/holder? uri) (ph/authzn-holder? mp)) (aa/bold-red "error - untested") (if (= ass "pass") (aa/bold-green ass) (aa/bold-red ass)))]
;      (println "Test : " method " - " uri ", status - " status ": " so))))
