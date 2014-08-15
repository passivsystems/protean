(ns protean.core.protocol.http)

;; =============================================================================
;; Constants
;; =============================================================================

;; Status codes
;;;;;;;;;;;;;;;

; non exhaustive error status codes
(def errs #{400 405 500 502 503 504})

; client (request errors)
(def req-errs #{400 405})

; default per method status codes
(def statuses {:get 200 :post 201 :put 204 :delete 204 :head 200})

(defn status [method] {:status (or (method statuses) 500)})


;; Content types
;;;;;;;;;;;;;;;;

(def txt "text/plain")
(def xml "text/xml")
(def jsn "application/json; charset=utf-8")
(def frm "application/x-www-form-urlencoded")
