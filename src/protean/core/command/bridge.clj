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

   A sensible default is to rely on seed data where no generative information
   is available in the codex for a given node.

   Reconciles the results certain probes record across time.

   Links expectations to probe results where outcome is a concern.")

;; =============================================================================
;; Helper functions
;; =============================================================================



;; =============================================================================
;; Commands
;; =============================================================================

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
   values."
  [{:keys [host port locs commands seed] :as corpus} codices]
  (println "dispatching probe(s) to visit nodes")
  (println "host : " host)
  (println "port : " port)
  (println "locs : " locs)
  (println "commands : " commands))
