(ns protean.core.protocol.http)

;; =============================================================================
;; Constants
;; =============================================================================

;; Status codes
;;;;;;;;;;;;;;;

; non exhaustive error status codes
(def errs #{400 405 500 502 503 504})

; client (request errors)
(def client-errs #{400 405})

; default per method status codes
(def statuses {:get 200 :post 201 :put 204 :delete 204 :head 200})

(defn success? [status]
  (.startsWith (str status) "2"))

(defn status [method] {:status (or (method statuses) 500)})


;; Headers
;;;;;;;;;;

(def ctype "Content-Type")
(def azn "Authorization")


;; Content types
;;;;;;;;;;;;;;;;

(def txt "text/plain")
(def xml "text/xml")
(def jsn-simple "application/json")
(def jsn (str jsn-simple "; charset=utf-8"))
(def frm "application/x-www-form-urlencoded")


;; =============================================================================
;; Helper functions
;; =============================================================================

;; Content type
;;;;;;;;;;;;;;;

(defn xml? [c] (= c xml))
(defn txt? [c] (= c txt))


;; Response
;;;;;;;;;;;

(defn rsp [payload header body]
  (if (get-in payload [:headers ctype])
    (assoc payload :body body)
    (assoc-in (assoc payload :body body) [:headers ctype] header)))
