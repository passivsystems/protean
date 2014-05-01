(ns protean.transformations.testapi
  "Uses output from the analysis transformations to generate a
   datastructure which can drive automated testing.  This variant
   tests the live API surface area."
  (:require [protean.transformations.coerce :as txco]
            [protean.transformations.test :as tst])
  (:use [clojure.string :only [replace split]]
        [taoensso.timbre :as timbre :only (trace debug info warn error)]))

;; =============================================================================
;; Helper functions
;; =============================================================================

; TODO: refactor put in a common place
(defn substring? [sub st] (not= (.indexOf st sub) -1))

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
                            (str strat " " (last (split sauth #" "))))]
            (list (first payload) (second payload) n))
          payload)
        payload)
      payload)))

(defn- v-swap [v seed]
  (if (substring? PSV v)
    (if-let [sv (get-in seed [(last (.split v PSV-EXP))])] sv v)
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
  (-> uri (.split "/psv\\+") first (.split "/") last))

; TODO: weak, only handles 1 instance of uri placeholder
(defn- uri-> [seed payload]
  (let [uri (second payload)]
    (if (substring? (str "/" PSV) uri)
      (let [ns (str (uri-namespace uri) "/")
            v (first (filter #(substring? ns %) (vals seed)))]
        (if v
          (list (first payload)
                (replace uri #"psv\+" (last (.split v "/")))
                (last payload))
          payload))
      payload)))

(defn- seed-> [test seed]
  (->> test
       (header-authzn-> "Basic" seed)
       (header-authzn-> "Bearer" seed)
       (body-> :query-params seed)
       (body-> :body seed)
       (uri-> seed)))

(defn- seeds [tests seed] (if seed (map #(seed-> % seed) tests) tests))

;; =============================================================================
;; Transformation functions
;; =============================================================================

;; First sow all seed items

    ;; Loop
      ; Call whatever we can and grow seed from result if applicable
      ; Track what we have called
(defn testapi-analysis-> [host port codices corpus]
  (info "testing the API")
  (prn "seed is : " (corpus "seed"))
  (let [tests (tst/test-> host port codices corpus)
        seeded (seeds tests (corpus "seed"))]
    (println "***********************************************************")
    (prn "!!!!! ***** tests : " tests)
    (prn "!!!!! ***** seed : " seeded)

    (println "***********************************************************")


    (map #(tst/test! %) tests)))
