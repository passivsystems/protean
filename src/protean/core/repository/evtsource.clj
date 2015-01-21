(ns protean.core.repository.evtsource
  "A simple event sourcing engine.

  (binding [es/*v-fn* #(:result %)
            es/*s-fn* (fn [{:keys [h] :or {h 0}} evt]
                        (if (= :pass (:result evt)) (inc h) h))]
    (es/step {} {:result :pass})
    (es/safe-step {:total 90 :h 30 :avg 0.3} {:result :pass})
    (es/run {:total 0 :h 0} [{:result :pass} {:result :fail}])
  )

  Sample above demonstrates the ES lib for a sample unit test domain.")

(def ^:dynamic *v-fn*) ; event validation function (bind in client)
(def ^:dynamic *s-fn*) ; success determinance (bind in client)

(defn step
  "Apply an event to some state.
   A minimal event is {:result :val} where val is something like :pass.
   Our approach assumes aggregation and computation of an average.
   State includes a total and h (hit) where his is success in a given domain."
  [{:keys [total h] :or {total 0 h 0} :as state} evt]
  (let [total (inc total)
        h (*s-fn* state evt)
        avg (double (/ h total))]
    {:total total :h h :avg avg}))

(defn safe-step
  "Step if event is valid."
  [state evt] (if (*v-fn* evt) (step state evt) state))

(def run "Run the program." #(reduce safe-step %1 %2))

(def steps "Inspect all steps of the program." #(reductions safe-step %1 %2))
