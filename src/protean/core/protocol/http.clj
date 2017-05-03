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

;; Status doc values
;;;;;;;;;;;;;;;;;;;;

(def status-docs
  {
    :200 "OK"
    :201 "Created"
    :202 "Accepted"
    :203 "Non-Authoritative Information"
    :204 "No Content"
    :205 "Reset Content"
    :206 "Partial Content"
    :300 "Multiple Choices"
    :301 "Moved Permanently"
    :302 "Found"
    :303 "See Other"
    :304 "Not Modified"
    :305 "Use Proxy"
    :307 "Temporary Redirect"
    :400 "Bad Request"
    :401 "Unauthorized"
    :403 "Forbidden"
    :404 "Not Found"
    :405 "Method Not Allowed"
    :406 "Not Acceptable"
    :407 "Proxy Authentication Required"
    :408 "Request Timeout"
    :409 "Conflict"
    :410 "Gone"
    :411 "Length Required"
    :412 "Precondition Failed"
    :413 "Request Entity Too Large"
    :414 "Request-URI Too Long"
    :415 "Unsupported Media Type"
    :416 "Request Range Not Satisfiable"
    :417 "Expectation Failed"
    :418 "I'm A Teapot"
    :500 "Internal Server Error"
    :501 "Not Implemented"
    :502 "Bad Gateway"
    :503 "Service Unavailable"
    :504 "Gateway Timeout"
  })


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
