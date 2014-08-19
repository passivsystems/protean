(ns protean.core.command.seed
  "Seed with generated values when integration testing or aggregated
   values when incrementally negotiating (workflows etc)."
  (:require [clojure.string :as stg]
            [protean.core.transformation.coerce :as txco]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defonce PSV "psv+")
(defonce PSV-EXP "psv\\+")
(def azn "Authorization")

(defn- token [seed strat]
  (let [tokens (get-in seed [azn])]
    (first (filter #(.contains % strat) tokens))))

; TODO: needs refactoring, trying to get a prototype out
; strat is either Basic or Bearer
(defn- header-authzn-> [strat seed payload]
  (let [m (last payload)]
    (if-let [auth (get-in m [:headers azn])]
      (if (and (.contains auth PSV) (.contains auth strat))
        (if-let [sauth (token seed strat)]
          (let [n (assoc-in m [:headers azn]
                            (str strat " " (last (stg/split sauth #" "))))]
            (list (first payload) (second payload) n))
          payload)
        payload)
      payload)))

(defn- substr? [s sub] (if s (.contains s sub) false))

(defn- bag-item [v seed]
  (let [ns (first (.split v "/psv\\+"))]
    (first (filter #(substr? % (str ns "/")) (get-in seed ["bag"])))))

; first search in first class seed items, then in the bag
(defn- v-swap [v seed]
  (if (.contains v PSV)
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
    (if (.contains uri (str "/" PSV))
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

(defn seeds [tests seed] (if seed (map #(seed-> % seed) tests) tests))
