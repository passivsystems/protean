(ns protean.core.transformation.testy-cljhttp
  "Transforms datastructures from payload to clj-http client ready.

   Still concurrency mechanism agnostic at this point, ie may use reducers,
   futures, agents etc.  We are not acutally calling any of the functions."
  (require [protean.core.transformation.payload :as p]))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn- method [entry payload]
  (let [method
         (cond
           (= (:method entry) :post) 'client/post
           (= (:method entry) :put) 'client/put
           (= (:method entry) :delete) 'client/delete
           :else 'client/get)]
    (conj payload method)))

(defn- uri [entry payload] (conj payload (:uri entry)))

(defn- options [entry payload]
  (conj payload (assoc (:options entry) :throw-exceptions false)))

(defn- clj-http [entry]
  (->> []
       (method entry)
       (uri entry)
       (options entry)
       seq))


;; =============================================================================
;; Transformation functions
;; =============================================================================

(defn clj-httpify [host port codices corpus]
  (let [payloaded (p/build-payload host port codices corpus)]
    (map #(clj-http %) payloaded)))
