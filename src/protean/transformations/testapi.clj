(ns protean.transformations.testapi
  "Uses output from the analysis transformations to generate a
   datastructure which can drive automated testing.  This variant
   tests the live API surface area."
  (:require [clojure.string :as stg]
            [protean.transformations.coerce :as txco]
            [protean.transformations.test :as tst])
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error)]))

;; =============================================================================
;; Helper functions
;; =============================================================================

; TODO: refactor put in a common place
(defn substring? [sub st] (not= (.indexOf (str st) sub) -1))

(defonce PSV "psv+")
(defonce PSV-EXP "psv\\+")
(defonce AZN "Authorization")

(defn- token [seed strat]
  (let [tokens (get-in seed [AZN])]
    (first (filter #(substring? strat %) tokens))))

; TODO: needs refactoring, trying to get a prototype out
; strat is either Basic or Bearer
(defn- header-authzn-> [strat seed payload]
  (let [m (last payload)]
    (if-let [auth (get-in m [:headers AZN])]
      (if (and (substring? PSV auth) (substring? strat auth))
        (if-let [sauth (token seed strat)]
          (let [n (assoc-in m [:headers AZN]
                            (str strat " " (last (stg/split sauth #" "))))]
            (list (first payload) (second payload) n))
          payload)
        payload)
      payload)))

(defn- bag-item [v seed]
  (let [ns (first (.split v "/psv\\+"))]
    (first (filter #(substring? (str ns "/") %) (get-in seed ["bag"])))))

; first search in first class seed items, then in the bag
(defn- v-swap [v seed]
  (if (substring? PSV v)
    (if-let [sv (get-in seed [(last (.split v PSV-EXP))])]
      sv
      (if-let [sv (bag-item v seed)] sv v))
    v))

(defn- body-> [k seed payload]
  (let [m (last payload)]
    (if-let [qp (if (= k :body) (txco/clj-> (k m)) (k m))]
      (list
        (first payload)
        (second payload)
        (assoc m k
          (let [res (into {} (for [[k v] qp] [k (v-swap v seed)]))]
            (if (= k :body)
              (txco/js-> res)
              res))))
      payload)))

(defn- uri-namespace [uri]
  (-> uri (.split "/psv\\+") first (.split "/") last (str "/psv+")))

; TODO: weak, only handles 1 instance of uri placeholder
(defn- uri-> [seed payload]
  (let [uri (second payload)]
    (if (substring? (str "/" PSV) uri)
      (let [v (uri-namespace uri)
            sv (bag-item v seed)]
        (if sv
          (list (first payload)
                (stg/replace uri #"psv\+" (last (.split sv "/")))
                (last payload))
          payload))
      payload)))

(defn- seed-> [test seed]
  (->> test
       (header-authzn-> "Basic" seed)
       (header-authzn-> "Bearer" seed)
       (body-> :query-params seed)
       (body-> :body seed)
       (body-> :form-params seed)
       (uri-> seed)))

(defn- seeds [tests seed] (if seed (map #(seed-> % seed) tests) tests))

(defn- untestable-payload? [body k]
  (if-let [qp (if (= k :body) (txco/clj-> (k body)) (k body))]
    (if (first (filter #(substring? PSV %) (vals qp)))
      true
      false)
    false))

; does this test have any unseeded items ?
(defn- untestable? [test]
  (or
   (substring? (str "/" PSV) (second test))
   (if-let [auth (get-in (last test) [:headers AZN])]
     (if (substring? PSV auth) true false)
     false)
   (untestable-payload? (last test) :query-params)
   (untestable-payload? (last test) :body)
   (untestable-payload? (last test) :form-params)))

(defn- result [result test]
  (-> result
      (conj (get-in (last test) [:codex :success-code]))
      (conj (get-in (last test) [:codex :content-type]))
      (conj (get-in (last test) [:codex :body-res]))))

(defn- test-results! [tests] (map #(result (tst/test! %) %) tests))

(defn- body->seed [seed res res-map]
  (if (= (:status res-map) (nth res 2))
    (let [b (if (= (nth res 3) "text/plain")
              (get-in res-map [:body])
              (txco/clj-> (get-in res-map [:body])))
          extract-key (last res)
          extract (if (= (nth res 3) "text/plain") b (get-in b [extract-key]))]
      (if (= extract-key "access_token")
        (update-in seed ["Authorization"] conj (str "Bearer " extract))
        (update-in seed ["bag"] conj extract)))
    seed))

; TODO: basic - does not handle multiple body types, just json payload
; TODO: feed auth header items into bag as per other results (consistent) ?
(defn- seed-stitch [seed res]
  (let [res-map (second res)]
    (cond
     (get-in res-map [:body])
       (body->seed seed res res-map)
     (get-in res-map [:location])
       (update-in seed ["bag"] conj (get-in res-map [:location]))
     :else seed)))

(defn- update-seed [seed payload] (assoc payload :seed seed))

(defn- update-results [res payload] (update-in payload [:results] concat res))

(defn- update-tests [new-tests payload] (assoc payload :tests new-tests))

(defn- update-state [state testable new-seed res new-tests]
  (->> state
       (update-seed new-seed)
       (update-results res)
       (update-tests new-tests)))

(defn- testable [{:keys [tests results]}]
  (let [testable (remove #(untestable? %) tests)
        tested (map #(first %) results)]
    (remove #(some #{(second %)} tested) testable)))

(defn- test! [state]
  (let [testable-tests (testable state)]
    (if (empty? testable-tests)
      state
      (test!
       (let [res (test-results! testable-tests)
             new-seed (reduce seed-stitch (:seed state) res)
             new-tests (seeds (:tests state) new-seed)]
         (update-state state testable-tests new-seed res new-tests))))))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn testapi-analysis-> [host port codices corpus]
  (info "testing the API")
  (let [seed (corpus "seed")
        tests (tst/test-> host port codices corpus)
        seeded (seeds tests seed)]
    (let [state (test! {:tests seeded :seed seed :results []})
          res (:results state)
          part ((juxt filter remove) #(= (:status (second %)) (nth % 2)) res)
          test-uris (map #(second %) (:tests state))
          res-uris (map #(first %) (:results state))
          untested (clojure.set/difference (set test-uris) (set res-uris))]
      {:passed (first part) :failed (last part) :untested untested})))
