(ns protean.core.codex.document
  "Codex data extraction and truthiness functionality."
  (:require [protean.core.protocol.http :as h]))

;; =============================================================================
;; Codex request
;; =============================================================================

(defn qp [c] (get-in c [:req :query-params :required]))

(defn fp [c] (get-in c [:req :form-params]))

(defn hdrs-req [c] (get-in c [:req :headers]))

(defn body-req [c] (get-in c [:req :body]))


;; =============================================================================
;; Codex response
;; =============================================================================

(defn rsp-type [c] (get-in c [:rsp :headers h/ctype]))

(defn hdrs-rsp [c] (get-in c [:rsp :headers]))

(defn body-rsp [c] (get-in c [:rsp :body]))

(defn err-status [c] (get-in c [:rsp :errors :status]))

(defn err-prob [c] (get-in c [:rsp :errors :probability]))


;; =============================================================================
;; Codex fragment functions (codex fragments that travel with tests etc)
;; =============================================================================

(defn qp-type [c] (get-in c [:codex :q-params-type]))

(defn azn [c] (get-in c [:headers h/azn]))


;; =============================================================================
;; Truthiness functions
;; =============================================================================

(defn qp-json? [c] (= (qp-type c) :json))
