(ns protean.core.command.testprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as s]
            [clojure.java.io :refer [file]]
            [clojure.data :refer [diff]]
            [io.aviso.ansi :as aa]
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
            [loom.io :as li]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- hlr [& more] (println (aa/bold-red (s/join " " more))))
(defn- hlg [& more] (println (aa/bold-green (s/join " " more))))

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
    (let [res (assoc-in payload target-keys (ph/swap ph tree bag))]
       (if (ph/holder? res) (hlr "Could not resolve all placeholders:" (ph/holder? res))) ; TODO should mark test status as fail..
       res)
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
  (let [parsed-uri (ph/swap uri tree bag)]
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
  (let [tolist (fn [m] (cond
          (map? m) (vals m)
          (list? m) m
          :else (list m)))
        collect (fn [v] (cond
          (string? v) (map second (ph/holder? v))
          (map? v) (collect-params v)
          :else v))]
    (mapcat collect (tolist m))))

(defn- inputs [uri tree]
  (let [phs (list
              uri
              ; TODO only include optional when (corpus) test level is 2?
              (d/get-in-tree tree [:req :query-params :required])
              (d/get-in-tree tree [:req :query-params :optional])
              (d/get-in-tree tree [:req :form-params :required])
              (d/get-in-tree tree [:req :form-params :optional])
              (d/get-in-tree tree [:req :headers])
              (d/get-in-tree tree [:req :body]))]
    (mapcat collect-params phs)))

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

(defn- outputs-hdrs [response [k v]]
  (when-let [holder (ph/holder? v)]
    (for [ph (map second holder)]
      (do ;(println "ph:" ph)
        (when-let [response-value (get-in response [:headers k])]
          (if-let [extract (read-from v ph response-value)]
            [ph extract]
            (println "could not extract" ph "from" response-value "with template" v)))))))

(defn- outputs-body [response [k v]]
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
                   (println "could not extract" ph "from" response-value "with template" v))))))))))

(defn- outputs-values [tree response]
  (let [res (val (first (d/success-status tree)))]
    (merge
      (into {} (mapcat (partial outputs-hdrs response) (get-in res [:headers])))
      (into {} (mapcat (partial outputs-body response) (get-in res [:body]))))))

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
     :engage (fn [bag]
      (let [request (prepare-request uri entry bag)]
        (t/test! request)))
    }))


;; =============================================================================
;; Probe dispatch
;; =============================================================================

(defn- label [probe]
  (let [{:keys [method svc path] :as entry} (:entry probe)]
    (str method " " svc " " path)))

(defn- get-dependencies [corpus probes probe]
  "Returns seq of vectors [input dependency]."
  "All inputs that cannot be generated (or have been seeded)"
  "form a dependency on endpoint that produces them as outpus"
  (let [dependency-for (fn [input]
    (let [tree (get-in probe [:entry :tree])
          seed (get-in corpus [:seed input])
          find-dep (fn [probe] (if (some #{input} (:outputs probe)) probe))
          dependencies (remove #{probe} (remove nil? (map find-dep probes)))
          can-gen (d/get-in-tree tree [:vars input :gen])]
      (when (and (not seed) (= false can-gen))
        (if (empty? dependencies)
            (hlr "No endpoint available to provide" input "!")) ; TODO should mark test status as fail..
          (map #(->[input %]) dependencies))))]
  (mapcat dependency-for (:inputs probe))))

(defn- build-dependency-graph [g corpus probes probe]
  (let [tree (get-in probe [:entry :tree])
        dependencies (get-dependencies corpus probes probe)
        add-node (fn [g probe]
          (-> g
            (lg/add-nodes probe)
            (lat/add-attr probe :label (label probe))))
        add-dependencies (fn [g [input dependency]]
          (-> g
            (lg/add-edges [dependency probe])
            (lat/add-attr [dependency probe] :label input)))]
    (reduce add-dependencies (add-node g probe) dependencies)))

(defn- execute [[bag reses] probe]
  (let [res ((:engage probe) bag)
        outputs (outputs-values (:tree (:entry probe)) (second res))]
    (println (label probe) "\noutputs" outputs "\n")
    [(merge outputs bag) (conj reses [(:entry probe) res])]))

(defmethod pb/dispatch :test [_ corpus probes]
  (hlg "dispatching probes")
  (let [analyse (fn [g probe] (build-dependency-graph g corpus probes probe))
        g (reduce analyse (lg/digraph) probes)]
;    (li/view g) ; uncomment to open image of graph
    (let [bag (get-in corpus [:seed])
          ordered-probes (la/topsort g)]
      (if (empty? ordered-probes)
        (let [response {:error "no route found to traverse probes (cyclic dependencies)"}]
          (map #(-> [(:entry %) [nil response]]) probes))
        (do
          (println "\nexecuting probes in the following order:\n"
            (s/join "\n" (map (fn [p] (pr-str (label p) " inputs:" (:inputs p) " outputs:" (:outputs p))) ordered-probes))
            "\n")
          (reverse (second (reduce execute [bag (list)] ordered-probes))))
      ))))

;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defn- assess [response tree]
  (if-let [error (:error response)]
    [error]
    (let [success-rsp (first (d/success-status tree))
          success-rsp-code (key success-rsp)
          success (val success-rsp)
          expected-ctype (d/rsp-ctype success-rsp-code tree)]
      (->> []
        (v/validate-status-> (name success-rsp-code) response)
        (v/validate-headers (d/rsp-hdrs success-rsp-code tree) response)
        (v/validate-body response expected-ctype (:body-schema success) (:body success))))))

(defmethod pb/analyse :test [_ corpus results]
  (hlg "analysing probe data")
  (doseq [[entry [request response]] results]
;    (println "result - request:" request)
;    (println "result - response:" response)
    (let [method (if request (:method request) (:method entry))
          uri (if request (:uri request) (str (:svc entry) (:path entry)))
          status (:status response)
          tree (:tree entry)
          ass (assess response tree)
          so (if (empty? ass) (aa/bold-green "pass") (aa/bold-red (str "fail - " (s/join "\n" ass))))]
      (println "Test : " method " - " uri ", status - " status ": " so)))) ; TODO need to identify if couldnt run cos dependencies not met? (currently exp/gen seem to catch all)
