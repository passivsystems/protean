(ns protean.cli.simadmin
  "Administration functions for manipulating simulations."
  (:require [clj-http.client :as clt]
            [protean.api.transformation.coerce :as c]))

(defn services [{:keys [host port]}]
	(let [rsp (clt/get (str "http://" host ":" port "/services"))]
    (c/pretty-clj (:body rsp))))

(defn service [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/services/" name))]
    (c/pretty-clj (:body rsp))))

(defn service-usage [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/services/" name "/usage"))]
    (doseq [j (c/clj (:body rsp))] (println j "\n"))))

(defn add-services [{:keys [file host port]}]
  (let [rsp (clt/put (str "http://" host ":" port "/services")
              {:multipart [{:name "file"
                            :content (clojure.java.io/file file)}]})]
    (c/pretty-clj (:body rsp))))

(defn delete-service [{:keys [host port name] :as options}]
  (let [rsp (clt/delete (str "http://" host ":" port "/services/" name)
                        {:throw-exceptions false})]
   (services options)))

(defn sims [{:keys [host port]}]
	(let [rsp (clt/get (str "http://" host ":" port "/sims"))]
    (c/pretty-clj (:body rsp))))

; TODO: not working with Ring, port to Compojure
(defn add-sims [{:keys [file host port]}]
  (let [rsp (clt/put (str "http://" host ":" port "/sims")
              {:multipart [{:name "file"
                            :content (clojure.java.io/file file)}]})]
    (println (:body rsp))))

(defn delete-sim [{:keys [host port name] :as options}]
  (let [rsp (clt/delete (str "http://" host ":" port "/sims/" name)
                        {:throw-exceptions false})]
   (sims options)))
