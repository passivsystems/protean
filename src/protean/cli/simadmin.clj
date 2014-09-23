(ns protean.cli.simadmin
  "Administration functions for manipulating simulations."
  (:require [clj-http.client :as clt]
            [protean.core.transformation.coerce :as c]))

(defn projects [{:keys [host port]}]
	(let [rsp (clt/get (str "http://" host ":" port "/services"))]
    (c/pretty-clj (:body rsp))))

(defn project [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/services/" name))]
    (c/pretty-clj (:body rsp))))

(defn project-usage [{:keys [host port name]}]
  (let [rsp (clt/get (str "http://" host ":" port "/services/" name "/usage"))]
    (doseq [j (c/clj (:body rsp))] (println j))))

(defn add-projects [{:keys [file host port]}]
  (let [rsp (clt/put (str "http://" host ":" port "/services")
              {:multipart [{:name "file"
                            :content (clojure.java.io/file file)}]})]
    (println (:body rsp))))

(defn delete-project [{:keys [host port name] :as options}]
  (let [rsp (clt/delete (str "http://" host ":" port "/services/" name)
                        {:throw-exceptions false})]
   (projects options)))

(defn add-project-error [{:keys [host port name status-err] :as options}]
  (let [rsp
    (clt/put (str "http://" host ":" port
                  "/services/" name "/errors/status/" status-err))]
    (project options)))

(defn set-project-error-prob [{:keys [host port name level] :as options}]
  (let [rsp
    (clt/put (str "http://" host ":" port
                  "/services/" name "/errors/probability/" level))]
    (project options)))

(defn del-project-errors [{:keys [host port name] :as options}]
  (let [rsp (clt/delete (str "http://" host ":" port "/services/" name "/errors"))]
    (project options)))
