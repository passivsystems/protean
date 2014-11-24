(ns protean.core.command.testprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as stg]
            [clojure.java.io :refer [file]]
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
            [protean.core.command.probe :as pb])
  (:import java.io.File java.net.URI java.util.UUID))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- hlr [t] (println (aa/bold-red t)))
(defn- hlg [t] (println (aa/bold-green t)))

(defn- show-test [level]
  (cond
    (= level 1) (hlr "ʘ‿ʘ I'm too young to die")
    (= level 2) (hlr "⊙︿⊙ Hey not too rough")
    (= level 3) (hlr "ミ●﹏☉ミ Hurt me plenty")
    (= level 4) (hlr "✖_✖ Ultra violence")))

(defn- swap [ph tree]
  (-> ph
     (ph/holder-swap ph/holder-swap-gen tree)
     (ph/holder-swap ph/holder-swap-exp tree)))

(defn- copy-and-swap [options tree source-keys target-keys]
  (if-let [ph (d/get-in-tree tree source-keys)]
    (assoc-in options target-keys (swap ph tree))
    options))

(defn- body-to-string [options]
  (update-in options [:body] co/js)) ; TODO check content-type and set as appropriate..

(defn- content-type [options method]
  (if (and (some #{method} [:post :put])
           (not (get-in options [:headers "Content-Type"])))
    (assoc-in options [:headers h/ctype]  h/jsn-simple)
    options))

(defn- swap-options [options tree]
  (-> options
    (copy-and-swap tree [:req :query-params :required] [:query-params])
    (copy-and-swap tree [:req :query-params :optional] [:query-params]) ; TODO only include when (corpus) test level is 2?
    (copy-and-swap tree [:req :form-params] [:form-params])
    (copy-and-swap tree [:req :headers] [:headers])
    (copy-and-swap tree [:req :body] [:body])
    (body-to-string)))

(defn- prepare-requests
  "Translate placeholders when visiting real nodes."
  [analysed {:keys [seed] :as corpus}]
  (let [to-request (fn [{:keys [method uri options tree] :as entry}]
    (let [parsed-uri (:uri (swap {:uri uri} tree))] ; wrapping and unwrapping uri in map to reuse holder-swap
      (-> {:method method :uri parsed-uri :tree tree} ; TODO currently storing tree in request - should be passed separately but needs to be preserved down pipeline - should be provided to probe methods instead of codex
          (update-in [:options] swap-options tree)
          (update-in [:options] content-type method))))]
    (map to-request analysed)))


;; =============================================================================
;; Probe config
;; =============================================================================

(defmethod pb/config :test [_ corpus]
  (show-test (get-in corpus [:config "test-level"] 1))
  (hlg "building probes"))

;; =============================================================================
;; Probe construction
;; =============================================================================

(defmethod pb/build :test [_ {:keys [locs] :as corpus} codices]
  (println "building a test probe to visit : " locs)
  [corpus
    (fn engage [{:keys [locs host port] :as corpus} codices res-fn]
     (let [h (or host "localhost")
           p (or port 3000)
           analysed (p/analysis-> host port codices corpus)
           requests (prepare-requests analysed corpus)
           results (map #(t/test! %) requests)]
       (res-fn results)
       results))])


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

(defmethod pb/dispatch :test [_ corpus codices probes]
  (hlg "dispatching probes")
  (let [res (doall (map (fn [x] ((last x) (first x) codices res-persist!)) probes))
        raw-posts (filter #(= (:method (first %)) :post) (apply concat res))
        ps (filter #(or (:location (nth % 1)) (:body (nth % 1))) raw-posts)
        vs (remove nil? (map #(or (:location (nth % 1)) (:body (nth % 1))) ps))
        locs (for [[m p] probes] (:locs m))
        bag (assoc-in corpus [:seed "bag"] (vec vs))
        np (doall (map #(pb/build :test (assoc-in bag [:locs] %) codices) locs)) ; another call to test probe - build?
        nr (doall (map (fn [x] ((last x) (first x) codices res-persist!)) np))
        fr (remove #(or (= (:method (first %)) :post) (not (some #{"seed"} (last %))))  (apply concat nr))]
;    (println "\nres" res)
;    (println "\nraw-posts" raw-posts)
;    (println "\nps" ps)
;    (println "\nvs" vs)
;    (println "\nlocs" locs)
;    (println "\nbag" bag)
;    (println "\nnp" np)
;    (println "\nnr" nr)
;    (println "\nfr" fr)
    (concat (apply concat res) fr)))

;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defn- assess [method status tree]
  (let [expected-status (name (key (first (d/success-status tree))))]
    (if (= (str status) expected-status)
      "pass"
      (str "fail - expected status " expected-status))))

(defmethod pb/analyse :test [_ corpus codices results]
  (hlg "analysing probe data")
;  (println "analyse" results)
  (doseq [[{:keys [method uri tree] :as request} {:keys [status] :as response}] results]
;    (println "result - request:" (dissoc request :tree))
;    (println "result - response:" response)
    (let [ass (assess method status tree)
          so (if (= ass "pass") (aa/bold-green ass) (aa/bold-red ass))]
      (println "Test : " method " - " uri ", status - " status ": " so))))

;  (doseq [[method uri mp phs] results]
;    (let [status (:status mp)
;          ass (assess method status phs)
;          so (if (or (ph/holder? uri) (ph/authzn-holder? mp)) (aa/bold-red "error - untested") (if (= ass "pass") (aa/bold-green ass) (aa/bold-red ass)))]
;      (println "Test : " method " - " uri ", status - " status ": " so))))
