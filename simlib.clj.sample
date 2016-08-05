;; OUT OF THE BOX EXAMPLE SIM LIBRARY
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns simlib
  (:require [protean.core.transformation.sim :refer :all]
            [protean.core.protocol.http :as h]
            [protean.core.transformation.coerce :refer [clj js]]))


;; =============================================================================
;; Generally Useful Functions
;; =============================================================================

;; TODO: should find a more permanent home for this
(defn parse-id [s] (Integer. (re-find  #"\d+" s)))


;; =============================================================================
;; Sim Library Request Functions
;; =============================================================================

(defn bp [p] (body-param p true))

(defn qp [p] (query-param p))

(defn qp= [x p] (= (qp p) x))

(defn pp [p] (path-param p))


;; =============================================================================
;; Sim Library Response Functions
;; =============================================================================

(defn h-rsp [s hdr] {:status s :headers {h/loc hdr}})

(defn b-rsp [s h b] {:status s :headers h :body b})

(defn rsp [s] {:status s})

(defn jsn
  ([s b] (b-rsp s {h/ctype h/jsn} (js b)))
  ([s h b] (b-rsp s (merge h {h/ctype h/jsn}) (js b))))

(defn txt [s b] (b-rsp s {h/ctype h/txt} b))


;; =============================================================================
;; Sim Library Payload Transport Functions
;; =============================================================================

(defn post
  ([url body] (post nil body))
  ([url hdrs body] (simple-request :post url hdrs body)))

(defn put
  ([url body] (put url nil body))
  ([url hdrs body] (simple-request :put url hdrs body)))

(defn patch
  ([url body] (patch url nil body))
  ([url hdrs body] (simple-request :patch url hdrs body)))


;; =============================================================================
;; Sim Library Scenario Modelling and Route Solution
;; =============================================================================

(defn solve [routes]
  (seq (remove nil? (map #(if ((first %)) (last %) nil) routes))))

(defn route-rsp [routes]
  (if-let [errs (solve routes)] ((rand-nth errs)) (success)))
