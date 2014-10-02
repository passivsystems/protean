(ns protean.core.protocol.protean
  "Our own concise derived protocol for req/rsp."
  (:require [protean.core.protocol.http :as h]))

;; TODO: deprecate for Ring ?

;; =============================================================================
;; Lense functions
;; =============================================================================

;; Request
;;;;;;;;;;

(defn ctype [req] (get-in req [:hdrs "content-type"]))
