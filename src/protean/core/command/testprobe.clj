(ns protean.core.command.testprobe
  "Building probes and handling persisting/presenting raw results."
  (:require [clojure.string :as s]
            [clojure.java.io :refer [file]]
            [clojure.set :as st]
            [io.aviso.ansi :as aa]
            [protean.core.codex.document :as d]
            [protean.core.codex.placeholder :as ph]
            [protean.core.protocol.http :as h]
            [protean.core.protocol.protean :as pp]
            [protean.core.transformation.coerce :as co]
            [protean.core.transformation.paths :as p]
            [protean.core.transformation.curly :as c]
            [protean.core.transformation.validation :as v]
            [protean.core.transformation.request :as r]
            [protean.core.command.test :as t]
            [protean.core.command.probe :as pb]
            [protean.core.command.junit :as j]
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

(defn- collect-params [m]
  (let [tolist (fn [m] (cond
          (map? m) (vals m)
          (list? m) m
          :else (list m)))
        collect (fn [v] (map second (ph/holder? v)))]
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

(defn- diff [s1 s2]
  (cond
    (and (nil? (first s1)) (nil? (first s2))) []
    (= (first s1) (first s2)) (diff (rest s1) (rest s2))
    :else [s1 s2]))

(defn- diff-str [s1 s2]
  (into [] (map s/join (diff (char-array (str s1)) (char-array (str s2))))))

(defn- read-from [template ph s]
  (let [[left right] (diff-str template s)
        diff-match (if left (re-matches ph/ph left))]
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
            {:ph ph :val extract}
            {:error (str "could not extract " ph " from " response-value " with template '" v "'")}))))))

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
             (try
               (let [json (co/clj response-body)]
                 (when-let [response-value (get-in json [k])]
                   (if-let [extract (read-from v ph response-value)]
                     {:ph ph :val extract}
                     {:error (str "could not extract " ph " from " response-value " with template '" v "'")})))
                (catch Exception e {:error (str "Could not parse json: " response-body " \n " (.getMessage e))}))))))))

(defn- outputs-values [tree response]
  (let [res (val (first (d/success-status tree)))
        header-phs (remove nil? (mapcat (partial outputs-hdrs response) (get-in res [:headers])))
        body-phs (remove nil? (mapcat (partial outputs-body response) (get-in res [:body])))
        to-entry (fn [e] [(:ph e) (:val e)])
        bag (into {} (map to-entry (concat header-phs body-phs)))
        errors (remove nil? (map :error (concat header-phs body-phs)))]
    {:bag bag :errors errors}))

(defn- uri [host port {:keys [svc path] :as entry}]
  (p/uri host port svc path))

(defmethod pb/build :test [_ {:keys [locs host port] :as corpus} {:keys [method tree] :as entry}]
  (println "building a test probe to visit " method ":" locs)
  (let [h (or host "localhost")
        p (or port 3000)
        uri (uri h p entry)
        request-template (r/prepare-request method uri tree)
        engage-fn (fn [bag]
          (let [request (ph/swap request-template tree bag)]
            (if-let [phs (ph/holder? request)]
              [request {:error (str "Not all placeholders replaced: " (s/join "," (map first phs)))}]
              (t/test! request))))]
    {:entry entry
     :inputs (inputs uri tree)
     :outputs (outputs-names tree)
     :engage engage-fn
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

(defn- build-graphs
  "returns a list of graphs covering all possible ways to walk over endpoints,
   satisfying input/output constraints."
  [corpus probes]
  (let [add-node (fn [g probe]
                   (-> g
                     (lg/add-nodes probe)
                     (lat/add-attr probe :label (label probe))))
        g-nodes (reduce add-node (lg/digraph) probes)
        dependencies (fn [probe] (get-dependencies corpus probes probe))
        input-dependencies (fn [probe] (group-by first (dependencies probe)))
        add-dependencies (fn [probe gs [input dependencies]]
                      (if (empty? dependencies)
                        gs
                        (for [g gs
                              [_ dependency] dependencies] ; the different dependencies that provide input
                          (-> g
                            (lg/add-edges [dependency probe])
                            (lat/add-attr [dependency probe] :label input)))))
        add-all-dependencies (fn [gs probe]
          (let [dependencies (input-dependencies probe)
                res (reduce (partial add-dependencies probe) gs dependencies)]
            (if (empty? dependencies) gs res)))]
    (reduce add-all-dependencies (list g-nodes) probes)))

(defn- execute [[bag reses] probe]
  (let [[req resp] ((:engage probe) bag)
        outputs (outputs-values (:tree (:entry probe)) resp)]
    (println (label probe) "\noutputs" outputs "\n")
    (let [errors (s/join "," (:errors outputs))]
      (if (empty? errors)
        [(merge (:bag outputs) bag) (conj reses {:entry (:entry probe) :request req :response resp})]
        [bag (conj reses {:entry (:entry probe) :request req :response (update-in resp [:failures] conj errors)})]))))

(def counter (atom (int 0)))
(defn- id-use-after-delete
   "if method is delete, then collect all inputs marked as :gen false
     - path is unusable if those inputs are required further down the line"
  [sorted-g]
  (let [f (fn [[deleted-inputs deleted-id-access] probe]
            (let [method (get-in probe [:entry :method])
                  inputs (set (get-in probe [:inputs]))
                  tree (get-in probe [:entry :tree])
                  next-deleted-id-access (or deleted-id-access (not-empty (st/intersection inputs deleted-inputs)))
                  non-generative-inputs (set (for [input inputs] (if (= false (d/get-in-tree tree [:vars input :gen])) input)))
                  next-deleted-inputs (set (concat deleted-inputs (if (= :delete method) non-generative-inputs)))]
;             (println "id-use-after-delete:\nmethod:" method "inputs:" inputs
;             "\n  deleted-inputs:" deleted-inputs "deleted-id-access:" deleted-id-access
;             "\n  next-deleted-inputs:" next-deleted-inputs "next-deleted-id-access:" next-deleted-id-access)
;             (println method "  " inputs)
;             (if (and (not deleted-id-access) next-deleted-id-access) (println "  already deleted: " deleted-inputs))
;             (println "  next-deleted-id-access" next-deleted-id-access)
          [next-deleted-inputs next-deleted-id-access]))
        res (second (reduce f [#{} nil] sorted-g))]
;    (println "result:" res)
    res))

(defn- sort-stream [g]
  ; TODO the sorted graph seems to be memoized, we only get the same graph
  ; and if it is not acceptable, we cannot walk the graph
  ; may need to look at a breadth-first search rather than topsort
  (cons (la/topsort g) (lazy-seq (sort-stream g))))

(defn- select-path
  "return a walkable (non-cyclic) path from optional graphs"
  [gs]
  (let [walkable (remove #(nil? (la/topsort %)) gs)
        selected (first walkable)
        ;acceptable (if-let [sorted (la/topsort selected)] (if (not (id-use-after-delete sorted)) sorted))
        ; topsort returns a random sorted graph - repeat until we have one which does not use an id after it has been deleted
        max-num 10
        acceptable (first (drop-while id-use-after-delete (take max-num (sort-stream selected))))]
;    (println "looking for path out of " (count gs) "walkable:" (count walkable)) ; note: printing this will evaluate all graphs
    ;(if selected (li/view selected)) ; uncomment to open image of graph
    (if (and selected (not acceptable)) (println "Can sort graph, but unable to find an acceptable one."))
    acceptable))

(defmethod pb/dispatch :test [_ corpus probes]
  (hlg "dispatching probes")
  (let [gs (build-graphs corpus probes)
        ordered-probes (select-path gs)
        bag (get-in corpus [:seed])
        no-route-response {:error "no route found to traverse probes (cyclic dependencies)"}
        print-inputs-outputs (fn [p] (pr-str (label p) " inputs:" (:inputs p) " outputs:" (:outputs p)))]
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

(defn- assess [{:keys [entry request response]}]
  (if-let [error (:error response)]
    {:error error}
    (let [tree (:tree entry)
          success-rsp (first (d/success-status tree))
          success-rsp-code (key success-rsp)
          success (val success-rsp)
          expected-ctype (d/rsp-ctype success-rsp-code tree)]
      {:failures (->> (into [] (:failures response))
        (v/validate-status-> (name success-rsp-code) response)
        (v/validate-headers (d/rsp-hdrs success-rsp-code tree) response)
        (v/validate-body response expected-ctype (d/to-path (:body-schema success) tree) (:body success)))})))

(defn- print-result [{:keys [entry request response error failures]}]
  ;      (println "result - request:" request)
  ;      (println "result - response:" response)
  (let [name (str (:method entry) " " (:svc entry) " " (:path entry))
        status (:status response)
        so (cond
          error (aa/bold-red (str "error - " error))
          (seq failures) (aa/bold-red (str "fail - " (s/join "\n" failures)))
          :else (aa/bold-green "pass"))]
    (println "Test : " name ", status - " status ": " so)))

(defmethod pb/analyse :test [_ corpus results]
  (hlg "analysing probe data")
  (let [assessed (map conj results (map assess results))]
    (doall (map print-result assessed))
    (j/write-report assessed)))
