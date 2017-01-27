(ns protean.core.protocol.http
  (:refer-clojure :exclude [methods]))

;; =============================================================================
;; Constants
;; =============================================================================

;; Methods
;;;;;;;;;;

(def methods #{:get :post :put :delete :patch :head :options})

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
(def loc "Location")


;; Content types
;;;;;;;;;;;;;;;;

(def txt "text/plain")
(def html "text/html")
(def xml "text/xml")
(def jsn-simple "application/json")
(def jsn (str jsn-simple "; charset=utf-8"))
(def frm "application/x-www-form-urlencoded")
(def bin "application/octet-stream")


;; =============================================================================
;; Helper functions
;; =============================================================================

(defn mime [url]
  (cond
    (.endsWith url ".json") jsn
    (.endsWith url ".xml") xml
    (.endsWith url ".html") html
    (.endsWith url ".txt") txt
    :else bin))

(defn mime-schema [url]
  (cond
    (.endsWith url ".json") jsn
    (.endsWith url ".xsd") xml
    :else bin))

;; Method
;;;;;;;;;

(defn method? [m] (some #{m} methods))


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
