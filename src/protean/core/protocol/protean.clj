(ns protean.core.protocol.protean
  "Our own concise derived protocol for req/rsp."
  (:require [protean.core.protocol.http :as h]))

;; =============================================================================
;; Lense functions
;; =============================================================================

;; Request
;;;;;;;;;;

(defn ctype [req] (or (get-in req [:headers "content-type"])
                      (get-in req [:headers "Content-Type"])))

(defn accept [req] (get-in req [:headers "accept"]))
