(ns protean.core.command.testprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as s]
            [clojure.java.io :refer [file]]
            [clojure.data :refer [diff]]
            [ring.util.codec :as cod]
            [io.aviso.ansi :as aa]
            [me.rossputin.diskops :as dsk]
            [silk.cli.api :as silk]
            [protean.core.codex.document :as d]
            [protean.core.codex.placeholder :as ph]
            [protean.core.protocol.http :as h]
            [protean.core.protocol.protean :as pp]
            [protean.core.transformation.coerce :as co]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.curly :as c]
            [protean.core.transformation.validation :as v]
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

(defn- copy-and-swap [payload tree bag source-keys target-keys]
  (if-let [ph (d/get-in-tree tree source-keys)]
    (assoc-in payload target-keys (ph/swap ph tree bag)) ; TODO if all placeholders are not swapped - then bomb
    payload))

(defn- content-type-> [payload method]
  (if (and (some #{method} [:post :put])
           (not (pp/ctype payload)))
    (assoc-in payload [:headers h/ctype] h/jsn-simple)
    payload))

(defn- content-> [payload]
  (let [ctype (pp/ctype payload)
        f (cond
            (h/txt? ctype) identity
            (h/xml? ctype) co/xml
            :else co/js)]
    (update-in payload [:body] f)))

(defn- prepare-request 
  "Translate placeholders when visiting real nodes."
  [uri {:keys [method tree] :as entry} bag]
  (let [parsed-uri (:uri (ph/swap {:uri uri} tree bag))] ; wrapping and unwrapping uri in map to reuse swap
    (-> {:method method :uri parsed-uri}
        (copy-and-swap tree bag [:req :query-params :required] [:query-params])
        (copy-and-swap tree bag [:req :query-params :optional] [:query-params]) ; TODO only include when (corpus) test level is 2?
        (copy-and-swap tree bag [:req :form-params :required] [:form-params])
        (copy-and-swap tree bag [:req :form-params :optional] [:form-params])
        (copy-and-swap tree bag [:req :headers] [:headers])
        (copy-and-swap tree bag [:req :body] [:body])
        (content-type-> method)
        (content->))))

(defn- collect-params [m]
  (let [l (cond (map? m) (vals m) (list? m) m :else (list m))]
    (mapcat (fn [v] (cond
                      (string? v) (map second (ph/holder? v))
                      (map? v) (collect-params v)
                      :else v))
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

(defn- read-from [template ph s]
;  (println "pulling out" ph "from" s "with template" template)
  (let [diff (diff (char-array template) (char-array s))
        left (s/join (first diff))
        right (s/join (second diff))
        diff-match (re-matches ph/ph left)]
        ; note currently only works until first mismatch.
        ; Which only works if our placeholder is the only placeholder, and is at the end of the string.
        ; e.g. abc${def} - ok
        ;      abc${def}ghi - not ok
    (if (= (second diff-match) ph)
      right)))

(defn- outputs-values [tree response]
  (let [res (val (first (d/success-status tree)))
        f-headers (fn [[k v]]
           (when-let [holder (ph/holder? v)]
             (for [ph (map second holder)]
               (do ;(println "ph:" ph)
                 (when-let [response-value (get-in response [:headers k])]
                   (if-let [extract (read-from v ph response-value)]
                     [ph extract]
                     (println "could not extract" ph "from" response-value "with template" v)))))))
        f-body (fn [[k v]]
           (when-let [holder (ph/holder? v)]
             (let [response-body (get-in response [:body])
                   ctype (pp/ctype response)]
               (for [ph (map second holder)]
                 (do ;(println "ph:" ph)
                   (cond
                     (h/txt? ctype) (read-from v ph response-body)
                     (h/xml? ctype) nil ; TODO read from xml
                     :else
                       (let [json (co/clj response-body)]
                         (when-let [response-value (get-in json [k])]
                           (if-let [extract (read-from v ph response-value)]
                             [ph extract]
                             (println "could not extract" ph "from" response-value "with template" v))))))))))]
    (merge
      (into {} (mapcat f-headers (get-in res [:headers])))
      (into {} (mapcat f-body (get-in res [:body]))))))

(defn- uri [host port {:keys [svc path] :as entry}]
  (p/uri host port svc path))

(defmethod pb/build :test [_ {:keys [locs host port] :as corpus} {:keys [tree] :as entry}]
  (println "building a test probe to visit " (:method entry) ":" locs)
  (let [h (or host "localhost")
        p (or port 3000)
        uri (uri h p entry)]
    {:entry entry
     :inputs (inputs uri tree)
     :outputs (outputs-names tree)
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
    (swap! g lg/add-nodes probe)
    (swap! g lat/add-attr probe :label (label probe))

    ; all inputs that cannot be generated (or have been seeded)
    ; form a dependency on endpoint that produces them as outpus
    (doseq [input inputs]
      (let [seed (get-in corpus [:seed input])
            dependencies (remove #{probe} (remove nil? (map #(find-dep input %) probes)))
            can-gen (d/get-in-tree tree [:vars input :gen])]
        (when (and (not seed) (= false can-gen))
          (if (empty? dependencies) (hlr "No endpoint available to provide " input "!"))
          (doseq [dependency dependencies]
            (swap! g lg/add-edges [dependency probe])
            (swap! g lat/add-attr [dependency probe] :label input)))))))

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
      (if ordered-probes
        (do
          (println "\nexecuting probes in the following order:\n"
            (s/join "\n" (map (fn [p] (pr-str (label p) " inputs:" (:inputs p) " outputs:" (:outputs p))) ordered-probes))
            "\n")
          (reverse (execute ordered-probes bag (list))))
        (hlr "no route found to traverse probes (cyclic dependencies)")
      ))))

;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defn- assess [response tree]
  (let [success-rsp (first (d/success-status tree))
        success-rsp-code (key success-rsp)
        success (val success-rsp)
        expected-ctype (d/rsp-ctype success-rsp-code tree)]
    (->> []
      (v/validate-status-> (name success-rsp-code) response)
      (v/validate-headers (d/rsp-hdrs success-rsp-code tree) response)
      (v/validate-body response expected-ctype (:body-schema success) (:body success)))))

(defmethod pb/analyse :test [_ corpus results]
  (hlg "analysing probe data")
  (doseq [[entry [request response]] results]
;    (println "result - request:" request)
;    (println "result - response:" response)
    (let [method (:method request)
          uri (:uri request)
          status (:status response)
          tree (:tree entry)
          ass (assess response tree)
          so (if (empty? ass) (aa/bold-green "pass") (aa/bold-red (str "fail - " (s/join "\n" ass))))]
      (println "Test : " method " - " uri ", status - " status ": " so)))) ; TODO need to identify if couldnt run cos dependencies not met? (currently exp/gen seem to catch all)

