(ns protean.core.command.testprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as s]
            [clojure.java.io :refer [file]]
            [clojure.set :as st]
            [io.aviso.ansi :as aa]
            [protean.config :as cfg]
            [protean.api.codex.document :as d]
            [protean.api.codex.placeholder :as ph]
            [protean.api.protocol.http :as h]
            [protean.api.protocol.protean :as pp]
            [protean.api.transformation.coerce :as co]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.curly :as c]
            [protean.api.transformation.validation :as v]
            [protean.core.transformation.request :as r]
            [protean.core.command.test :as t]
            [protean.core.command.probe :as pb]
            [protean.core.command.junit :as j]
            [loom.graph :as lg]
            [loom.label :as ll]
            [loom.alg :as la]
            [loom.io :as li]
            [json-path :as jp]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- hlr [& more] (println (aa/bold-red (s/join " " more))))
(defn- hlg [& more] (println (aa/bold-green (s/join " " more))))

;; =============================================================================
;; Probe config
;; =============================================================================

(defn- show-test [level]
  (case level
    1 (hlr "ʘ‿ʘ I'm too young to die")
    2 (hlr "⊙︿⊙ Hey not too rough")
    3 (hlr "ミ●﹏☉ミ Hurt me plenty")
    4 (hlr "✖_✖ Ultra violence")))

(defn test-level [corpus]
  (get-in corpus [:config "test-level"] 1))

(defmethod pb/config :test [_ corpus]
  (show-test (test-level corpus))
  (hlg "building probes"))

;; =============================================================================
;; Probe construction
;; =============================================================================

(defn- collect-params [m]
  (let [tolist (fn [m] (cond
          (map? m) (vals m)
          (list? m) m
          :else (list m)))
        collect (fn [v] (map second (ph/holder? v)))]
    (mapcat collect (tolist m))))

(defn- body-val [tree]
  (let [examples (d/get-in-tree tree [:req :body-examples])
        body (d/get-in-tree tree [:req :body])]
    (if examples
      (-> (first examples) (d/to-path tree) slurp s/trim)
      body)))

(defn- inputs [uri tree corpus]
  (let [include-optional (= 2 (test-level corpus))
        phs (concat (list
              uri
              (d/qps tree include-optional)
              (d/fps tree include-optional)
              (d/get-in-tree tree [:req :headers])
              (d/get-in-tree tree [:req :body])
              (body-val tree)))]
    (mapcat collect-params phs)))

(defn- outputs-names [tree]
  (let [res (val (first (d/success-status tree)))]
    (distinct (concat
      (collect-params (get-in res [:headers]))
      (collect-params (get-in res [:body-data]))))))

(defn- outputs-hdrs [response [k v]]
  (when-let [holder (ph/holder? v)]
    (for [ph (map second holder)]
      (do ;(println "ph:" ph)
        (when-let [response-value (get-in response [:headers k])]
          (if-let [extract (ph/read-from v ph response-value)]
            {:ph ph :val extract}
            {:error (str "could not extract " ph " from " response-value " with template '" v "'")}))))))

(defn- outputs-body [response [k v]]
  (when-let [holder (ph/holder? v)]
    (let [response-body (get-in response [:body])
          ctype (pp/ctype response)]
      (for [ph (map second holder)]
        (do ;(println "ph:" ph)
          (cond
            (h/txt? ctype) (ph/read-from v ph response-body)
            (h/xml? ctype) nil ; TODO read from xml
            :else
              (try
                (let [json (co/clj response-body true)]
                  (when-let [response-value (jp/at-path k json)]
                    (if-let [extract (ph/read-from v ph response-value)]
                      {:ph ph :val extract}
                      {:error (str "could not extract " ph " from " response-value " with template '" v "'")})))
                 (catch Exception e
                   {:error (str "Could not parse json: " response-body " \n " e)}))))))))

(defn- outputs-values [tree response]
  (let [res (val (first (d/success-status tree)))
        header-phs (remove nil? (mapcat (partial outputs-hdrs response) (get-in res [:headers])))
        body-phs (remove nil? (mapcat (partial outputs-body response) (get-in res [:body-data])))
        to-entry (fn [e] [(:ph e) (:val e)])
        bag (into {} (map to-entry (concat header-phs body-phs)))
        errors (remove nil? (map :error (concat header-phs body-phs)))]
    {:bag bag :errors errors}))

(defn- uri [host port {:keys [svc path] :as entry}]
  (p/uri host port svc path))

(defn- prepare-requests [method uri tree corpus]
  (let [req-templates
         (case (test-level corpus)
           1 [[:success "only mandatory"   (r/prepare-request method uri tree)]]
           2 [[:success "include optional" (r/prepare-request method uri tree :include-optional true)]]
           ; level 3 could turn on generate success cases from schema
           (conj
             (map #(into [:client-error] %) (r/prepare-invalid-requests method uri tree :gen-from-schema true))
             ; including success case, to allow us to proceed to the following endpoint
             [:success "only mandatory" (r/prepare-request method uri tree)]))]
  (for [[type label req-template] req-templates]
    [type label (fn [bag]
        (let [request (ph/swap req-template tree bag)]
          (if-let [phs (ph/holder? request)]
            [request {:error (str "Not all placeholders replaced: " (s/join "," (map first phs)))}]
            (t/test! request))))])))

(defmethod pb/build :test [_ {:keys [locs host port excludes] :as corpus} {:keys [svc path method tree] :as entry}]
  (let [f (fn [[esvc emethod epath]] (and
                                       (= esvc svc)
                                       (= emethod (name method))
                                       (= epath path)))]
    ; TODO we may just want to exclude endpoint for success testing -
    ;      could still cover it for negative test cases
    (if (some f excludes)
      (do
        (println "skipping " method ":" locs)
        nil)
      (do
        (println "building test probes to visit " method ":" locs)
        (let [h (or host "localhost")
              p (or port 3000)
              uri (uri h p entry)
              engage-fns (prepare-requests method uri tree corpus)]
          {:entry entry
           :inputs (inputs uri tree corpus)
           :outputs (outputs-names tree)
           :engage engage-fns
         })))))


;; =============================================================================
;; Probe dispatch
;; =============================================================================

(defn- label [probe]
  (let [{:keys [method svc path] :as entry} (:entry probe)]
    (str method " " svc " " path)))

(defn- get-dependencies [corpus probes probe]
  "Returns seq of vectors [input dependency]."
  "All inputs that cannot be generated (or have been seeded)"
  "form a dependency on endpoint that produces them as outputs"
  (let [dependency-for (fn [input]
    (let [tree (get-in probe [:entry :tree])
          seed (get-in corpus [:seed input])
          find-dep (fn [probe] (if (some #{input} (:outputs probe)) probe))
          dependencies (remove #{probe} (remove nil? (map find-dep probes)))
          can-gen (d/get-in-tree tree [:vars input :gen])]
      (when (and (not seed) (= false can-gen))
        (if (empty? dependencies)
            ; TODO should mark test status as fail..
            (hlr "No endpoint available to provide" input "!"))
          (map #(->[input %]) dependencies))))]
  (mapcat dependency-for (:inputs probe))))


(defn- all-input-possibilities
  "Generates graphs for all ways the inputs may be satisfied
   we may later pick one that can be walked (i.e. no cycles)"
  [gs dependencies probe input]
  (for [g gs
    [_ dependency] dependencies] ; the different dependencies that provide input
    (ll/add-labeled-edges g [dependency probe] input)))

(defn- delete-after-other-input-consumers
  "add dependency for delete endpoint on all other endpoints that use same input
   this ensures delete is called last (since will probably consume the input)"
  [gs probe input probes]
  (if (= :delete (get-in probe [:entry :method]))
    (let [add-delete-dependency (fn [g provider-probe]
            (if (some #{input} (:inputs provider-probe))
              (ll/add-labeled-edges g [provider-probe probe] (str "delete (" input ")"))
              g))
          add-delete-dependencies (fn [g probes]
            (reduce add-delete-dependency g (remove #{probe} probes)))]
      (for [g gs] (add-delete-dependencies g probes)))
    gs))

(defn- add-manual-dependencies
  "endpoint ordering that may not be inferred from codex may be provided in corpus"
  [gs corpus probes]
  (let [probe-match (fn [[svc method path] p]
                    (and
                       (= (get-in p [:entry :svc]) svc)
                       (= (get-in p [:entry :method]) (keyword method))
                       (= (get-in p [:entry :path]) path)))
        find-probe (fn [o]
          (let [p (first (filter (partial probe-match o) probes))]
            (if p
              p
              (hlr "No endpoint found matching" o "!"))))
        h (fn [g [from to]]
            (let [from-probe (find-probe from)
                  to-probe (find-probe to)]
            (ll/add-labeled-edges g [from-probe to-probe] (str "manual"))))
        f (fn [g] (reduce h g (get-in corpus [:order])))]
    (for [g gs] (f g))))

(defn- build-graphs
  "returns a list of graphs covering all possible ways to walk over endpoints,
   satisfying input/output constraints."
  [corpus probes]
  (let [add-node (fn [g probe] (ll/add-labeled-nodes g probe (label probe)))
        g-nodes (reduce add-node (lg/digraph) probes)
        dependencies (fn [probe] (get-dependencies corpus probes probe))
        add-dependencies (fn [probe gs [input dependencies]]
          (if (empty? dependencies)
            gs
            (-> gs
              (all-input-possibilities dependencies probe input)
              (delete-after-other-input-consumers probe input probes)
              (add-manual-dependencies corpus probes))))
        add-all-dependencies (fn [gs probe]
          (let [dependencies (group-by first (dependencies probe))
                res (reduce (partial add-dependencies probe) gs dependencies)]
            (if (empty? dependencies) gs res)))]
    (reduce add-all-dependencies (list g-nodes) probes)))

(defn- collect-rsp [probe [bag reses] [type l engage-fn]]
  (println "executing" l "(" type ")")
  (let [[req rsp] (engage-fn bag)
        tree (get-in probe [:entry :tree])
        ; skip output parsing for asserted failures
        outputs (if (= type :success) (outputs-values tree rsp))]
    (println (label probe) "\noutputs" outputs "\n")
    (let [errors (s/join "," (:errors outputs))]
      (if (empty? errors)
        [(merge (:bag outputs) bag)
         (conj reses {:entry (:entry probe) :request req :response rsp :label l :type type})]
        (do
          [bag (conj reses {:entry (:entry probe)
                          :request req
                          :response (update-in rsp [:failures] conj errors)
                          :label l
                          :type type})])))))

(defn- execute [[bag reses] probe]
  (reduce (partial collect-rsp probe) [bag reses] (:engage probe)))

(defn- render-graph [graph graph-name]
  (try
    (if graph
      (let [file-path (str (cfg/target-dir) "/" graph-name ".png")]
        (with-open [w (clojure.java.io/output-stream file-path)]
          (.write w ^bytes (li/render-to-bytes (second graph))))
        (println "traversal graph rendered to: " file-path)))
    (catch Exception e
      (println "By installing graphviz on path, we can render traversal graph."))))

(defn- select-path
  "return a walkable (non-cyclic) path from optional graphs"
  [gs graph-name]
  (let [tuples (map vector (map la/topsort gs) gs)
        walkable (remove #(nil? (first %)) tuples)
        selected (first walkable)]
    (render-graph selected graph-name)
    (first selected)))

(defmethod pb/dispatch :test [_ corpus probes]
  (hlg "dispatching probes")
  (let [gs (build-graphs corpus probes)
        ordered-probes (select-path gs (get-in (first probes) [:entry :svc]))
        bag (get-in corpus [:seed])
        no-route-response {:error "no route found to traverse probes (cyclic dependencies)"}
        print-inputs-outputs (fn [p]
          (pr-str (label p) " inputs:" (:inputs p) " outputs:" (:outputs p)))]
      (if (empty? ordered-probes)
        (map #(-> {:entry (:entry %) :request nil :response no-route-response}) probes)
        (do
          (println "\nexecuting probes in the following order:\n"
            (s/join "\n" (map print-inputs-outputs ordered-probes))
            "\n")
          (reverse (second (reduce execute [bag (list)] ordered-probes)))))))

;; =============================================================================
;; Probe data analysis
;; =============================================================================

(defn- assess [{:keys [entry request response label type]}]
  (if-let [error (:error response)]
    {:error error}
    (let [tree (:tree entry)
          success-rsp (first (d/success-status tree))
          success-rsp-code (key success-rsp)
          success (val success-rsp)
          ; TODO we may get other 4xx errors (e.g. 401 for missing auth header)
          expected-status (if (= type :client-error) "400" (name success-rsp-code))
          ; TODO we may have error headers defined in codex
          expected-hdrs (if (= type :client-error) [] (d/rsp-hdrs success-rsp-code tree))
          expected-ctype (d/rsp-ctype success-rsp-code tree)]
      {:failures (->> (into [] (:failures response))
                      (v/validate-status expected-status response)
                      (v/validate-headers expected-hdrs response)
                      (v/validate-body response expected-ctype
                      (d/to-path (:body-schema success) tree)
                      (:body success)))})))

(defn- interpret-resp [type response error failures]
  (cond
    error          (aa/bold-red (str "error - " error))
    (seq failures) (aa/bold-red (str "fail - " (s/join "\n" failures)))
    :else          (aa/bold-green "pass")))

(defn- print-result [{:keys [entry request response error failures label type]}]
  ;      (println "result - request:" request)
  ;      (println "result - response:" response)
  (let [name (str (:method entry) " " (:svc entry) " " (:path entry))
        status (:status response)
        so (interpret-resp type response error failures)]
    (println "\nTest:" name "\n" (str label " (" type "), status: " status " - " so))))

(defmethod pb/analyse :test [_ corpus results]
  (let [assessed (map conj results (map assess results))]
    (doall (map print-result assessed))
    (j/write-report assessed)))
