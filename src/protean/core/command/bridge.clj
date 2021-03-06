(ns protean.core.command.bridge
  "Dispatches different types of probe to visit nodes for simming, doccing,
   integration testing or negotiation.

   Several different types of probe may be instructed to visit a node to
   perform several different type of analysis on it.

   Reads from codex to analyse nodes to enable generation of documentation
   payloads, sim responses, generation of input values, or rationalisation
   of placeholder values depending on the task.

   N.B. even when in integration test mode where probes generate input values
   we may need a seed value to get things started for authentication etc.

   Reconciles the results certain probes record across time.

   Links expectations to probe results where outcome is a concern."
  (:require [protean.api.transformation.paths :as p]
            [protean.core.command.probe :as pb]
            ; the multi-dispatch probes
            [protean.core.command.docprobe]
            [protean.core.command.testprobe]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- path2loc [path]
  (str (name (:svc path)) " " (:path path)))

;; =============================================================================
;; Commands
;; =============================================================================

(defn- build [cmd corpus idx path]
  (pb/build
    cmd
    (assoc-in corpus [:locs] (path2loc path))
    (assoc path :codex-order idx)))

(defn visit
  "Construct and dispatch a range of probes of different types based on a
   'corpus'.

   The corpus is a composite datastructure containing mandatory; 'host', 'port',
   'locs', 'command', 'seed' and optional 'config'.

   Locs is a vector of Strings where a String may represent a node composition
   or an individual node; ['beers'] or ['beers yeasts'] respectively.  Locs
   defines where the probes will visit.

   Commands is a vector which constructs a range of probe types depending on
   the 'command' issued; :sim, :doc, :test, :negotiate.  E.G. [:doc] or
   [:doc :test].

   Uses node data encoded in 'codex' to optionally calculate generative input
   values, or document or sim."
  [{:keys [locs commands] :as corpus} codex]
  (doseq [cmd commands]
    (pb/config cmd corpus)
    (let [services (sort-by #(.indexOf (:ordered-resources codex) (:path %)) (p/services codex locs))
          probes (filter some? (doall (map-indexed (fn [idx itm] (build cmd corpus idx itm)) services)))
          results (pb/dispatch cmd corpus probes)]
      (pb/analyse cmd corpus results))))
