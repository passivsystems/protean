(ns protean.core.command.bridge
  "Dispatches different types of probe to visit nodes for simming, doccing,
   integration testing or negotiation.

   Several different types of probe may be instructed to visit a node to
   perform several different type of analysis on it.

   Reads from codices to analyse nodes to enable generation of documentation
   payloads, sim responses, generation of input values, or rationalisation
   of placeholder values depending on the task.

   N.B. even when in integration test mode where probes generate input values
   we may need a seed value to get things started for authentication etc.

   Reconciles the results certain probes record across time.

   Links expectations to probe results where outcome is a concern."
  (:require [protean.core.transformation.paths :as p]
            [protean.core.command.probe :as pr]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- paths2locs [locs corpus codices]
  (map #(str (name (:svc %)) " " (:path %)) (p/paths-> codices locs)))


;; =============================================================================
;; Commands
;; =============================================================================

(defn- analyse
  "Analyse a probe data."
  [{:keys [locs] :as corpus} codices results command]
  (pr/analyse command corpus codices results))

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

   Uses node data encoded in 'codices' to optionally calculate generative input
   values, or document or sim."
  [{:keys [host port locs commands seed] :as corpus} codices]
  (doseq [cmd commands]
    (pr/config cmd corpus)
    (let [paths (distinct (paths2locs locs corpus codices))
          probes (doall (map #(pr/build cmd (assoc-in corpus [:locs] [%]) codices) paths))
          results (pr/dispatch cmd corpus codices probes)]
      (analyse corpus codices results cmd))))
