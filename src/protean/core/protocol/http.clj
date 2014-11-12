(ns protean.core.protocol.http)

;; =============================================================================
;; Constants
;; =============================================================================

;; Status codes
;;;;;;;;;;;;;;;

(defn success? [status]
  (.startsWith (str status) "2"))

(defn client-err? [status]
  (.startsWith (str status) "4"))


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
