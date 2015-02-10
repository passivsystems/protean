(ns protean.core.repository.evtsource
  "A simple event sourcing engine.

   No exploitation of parallelisation etc, dumb out of the box reduce.

   (defn v-res [evt] #(:result evt))
   (defn s-ok [{:keys [h] :or {h 0}} evt e-filter]
     (if (= (:type evt) e-filter) (inc h) h))

   (es/step {} {:result :pass} v-res s-ok :attrib)
   (es/safe-step {:total 90 :h 30 :avg 0.3} {:result :pass} v-res s-ok :attrib)
   (es/run {:total 0 :h 0} [{:result :pass} {:result :fail} v-res s-ok :attrib])

  Sample above demonstrates the ES lib for a sample unit test domain.")


(defn step
  "Apply an event to some state.
   A minimal event is {:result :val} where val is something like :pass.
   Our approach assumes aggregation and computation of an average.
   State includes a total and h (hit) where his is success in a given domain."
  [{:keys [total h] :or {total 0 h 0} :as state} evt s-fn e-filter]
  (let [total (inc total)
        h (s-fn state evt e-filter)
        avg (double (/ h total))]
    {:total total :h h :avg avg}))

(defn safe-step
  "Step if event is valid."
  [state evt v-fn s-fn e-filter]
  (if (v-fn evt) (step state evt s-fn e-filter) state))

(defn run
  "Run the program."
  [state evts v-fn s-fn e-filter]
  (reduce #(safe-step %1 %2 v-fn s-fn e-filter) state evts))

(defn steps
  "Inspect all steps of the program."
  [state evts v-fn s-fn e-filter]
  (reductions #(safe-step %1 %2 s-fn v-fn e-filter) state evts))
