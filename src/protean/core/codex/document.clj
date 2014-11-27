(ns protean.core.codex.document
  "Codex data extraction and truthiness functionality."
  (:require [protean.core.protocol.http :as h]))

(defn custom-keys
  "returns only keys which are not keywords"
  [c]
  (seq (remove keyword? (keys c))))

(defn custom-entries
  "returns only entries where the keys are not keywords"
  [c]
  (remove #(keyword? (key %)) c))

(defn to-seq [codices svc path method]
  "creates a sequence (for now aka 'tree' - needs renaming) that can be traversed to resolve required references in scope"
  [(get-in codices [svc path method])
   (get-in codices [svc path])
   (get-in codices [svc])
   (get-in codices [method])
   codices]
)

(defn get-in-tree
  "returns the first result for given sequence of keys from a tree (scope)"
  [tree ks]
  (first (remove nil? (map #(get-in % ks) tree))))

(defn assoc-tree-item->
  "Extracts first out-ks in tree and assocs to target as in-k."
  [tree out-ks in-ks target]
  (if-let [v (get-in-tree tree out-ks)]
    (if (empty? v) target (assoc-in target in-ks v))
    target))

(defn assoc-item->
  "Extracts out-ks in source and assocs to target as in-ks."
  [source out-ks in-ks target]
  (if-let [v (get-in source out-ks)]
    (if (empty? v) target (assoc-in target in-ks v))
    target))


;; =============================================================================
;; Codex request
;; =============================================================================

(defn qp [t] (get-in-tree t [:req :query-params :required]))

(defn fp [t] (get-in-tree t [:req :form-params]))

(defn- codex-req-hdrs [tree]
  (get-in-tree tree [:req :headers]))

(defn req-ctype [tree]
  (let [hdrs (codex-req-hdrs tree)
        ctype (get-in hdrs h/ctype)
        body-schema (get-in-tree tree [:req :body-schema])
        body-example (get-in-tree tree [:req :body-example])]
    (cond
      ctype ctype
      body-schema (h/mime-schema body-schema)
      body-example (h/mime body-example))))

(defn req-hdrs [tree]
  (let [ctype (req-ctype tree)
        ctype-hdr (if ctype {h/ctype ctype} {})]
    (merge ctype-hdr (codex-req-hdrs tree))))

(defn body-req [t] (get-in-tree t [:req :body]))

;; =============================================================================
;; Codex response
;; =============================================================================

(defn- codex-rsp-hdrs [rsp-code tree]
  (merge
    (get-in-tree tree [:rsp :headers])
    (get-in-tree tree [:rsp rsp-code :headers])))

(defn rsp-ctype [rsp-code tree]
  (let [ctype (get-in (codex-rsp-hdrs rsp-code tree) h/ctype)
        body-schema (get-in-tree tree [:rsp rsp-code :body-schema])
        body-example (get-in-tree tree [:rsp rsp-code :body-example])]
    (cond
      ctype ctype
      body-schema (h/mime-schema body-schema)
      body-example (h/mime body-example))))

(defn rsp-hdrs [rsp-code tree]
  (let [ctype (rsp-ctype rsp-code tree)
        ctype-hdr (if ctype {h/ctype ctype} {})]
    (merge ctype-hdr (codex-rsp-hdrs rsp-code tree))))

(defn status-matching [tree filter-exp]
  (let [filter (fn [m] (seq (filter #(re-matches filter-exp (name (key %))) (:rsp m))))
        statuses (some identity (map filter tree))
        include-defaults (fn [[k v]]
      [k (update-in v [:headers] #(merge (get-in-tree tree [:rsp :headers]) %))])]
    (seq (into {} (map include-defaults statuses)))))

(defn success-status [tree]
  (status-matching tree #"2\d\d"))

(defn error-status [tree]
  (status-matching tree #"[1345]\d\d"))


;; =============================================================================
;; Codex fragment functions (codex fragments that travel with tests etc)
;; =============================================================================

(defn qp-type [t] (get-in-tree t [:req :query-params-type]))

(defn azn [c] (get-in c [:headers h/azn]))


;; =============================================================================
;; Truthiness functions
;; =============================================================================

(defn qp-json? [t] (= (qp-type t) :json))
