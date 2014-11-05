(ns protean.cli.main
  "A basic command line interface for Protean."
  (:require [clojure.string :as s]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file]]
  	        [clojure.tools.cli :refer [parse-opts]]
            [io.aviso.ansi :as aa]
            [protean.cli.interface :as i]
            [protean.core.transformation.coerce :as c]
            [protean.core.command.bridge :as b])
  (:use protean.cli.simadmin)
  (:import java.net.URI)
  (:gen-class))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defmacro get-version []
  (System/getProperty "protean.version"))

(defn- cli-banner []
  (println "              _")
  (println " _ __ _ _ ___| |_ ___ __ _ _ _ ")
  (println "| '_ \\ '_/ _ \\  _/ -_) _` | ' \\")
  (println (str "| .__/_| \\___/\\__\\___\\__,_|_||_| " "v" (get-version)))
  (println "|_|                            "))

(defn- nice-keys [m] (into {} (for [[k v] m] [(keyword k) v])))

(defn- nice-vals [v] (into [] (map #(keyword %) v)))

(defn- sane-corpus [m] (-> m nice-keys (update-in [:commands] nice-vals)))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 3001
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-H" "--host HOST" "Host name"
     :default "localhost"]
   ["-n" "--name NAME" "Project name"]
   ["-f" "--file FILE" "Project configuration file"]
   ["-d" "--directory DIRECTORY" "Documentation site"]
   ["-b" "--body BODY" "JSON body"]
   ["-s" "--status-err STATUS-ERROR" "Error status code"]
   ["-l" "--level LEVEL" "Error level (probability)"]
   ["-h" "--help"]])

(defn- usage-hud [options-summary]
  (->> [""
        "Usage: protean [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  services               (List services)"
        "  service                -n myservice (List service)"
        "  service-usage          -n myservice (List curl statements to use API)"
        "  add-services           -f service-config-file.edn (Add services in a codex)"
        "  del-service            -n myservice (Delete a service)"
        "  service-errors         -n myservice (Show service level error status codes)"
        "  add-service-error      -n myservice -s 500 (Add an error status code to a service)"
        "  set-service-error-prob -n myservice -l 10 (Set error probability)"
        "  del-service-errors     -n myservice (Delete error response codes)"
        "  visit                  -f codex -b body (Visit node(s) with probe(s) to doc, test etc)"
        ""
        "Please refer to the manual page for more information."]
       (s/join \newline)))

(defn- usage [options-summary] (cli-banner) (usage-hud options-summary))

(defn- usage-exit [options-summary] (usage-hud options-summary))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn- exit [status msg] (println msg) (System/exit status))


;; =============================================================================
;; Domain functionality
;; =============================================================================

(defn- visit [{:keys [host port file body] :as options}]
  (let [b (sane-corpus (c/clj body))]
    (println (aa/bold-green "Exploring quadrant..."))
    (let [codices (edn/read-string (slurp file))]
      (b/visit b codices)
      (println (aa/bold-green "...finished exploring quadrant")))))


;; =============================================================================
;; Application entry point
;; =============================================================================

(defn- bomb [summary] (exit 0 (usage summary))) ; exit nicely and print usage

(defn- handle-errors
  [{:keys [name file] :as options} arguments errors summary]
  (let [cmd (first arguments)]
    (cond
      (:help options) (bomb summary)
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors))
      (and (= cmd i/svc) (not name)) (bomb summary)
      (and (= cmd i/svc-usg) (not name)) (bomb summary)
      (and (= cmd i/add-svcs) (not file)) (bomb summary)
      (and (= cmd i/del-svc) (not name)) (bomb summary)
      (and (= cmd i/svc-errs) (not name)) (bomb summary)
      (and (= cmd i/add-svc-err) (i/add-svc-err? options)) (bomb summary)
      (and (= cmd i/set-svc-err-prob) (i/set-svc-err-prob? options)) (bomb summary)
      (and (= cmd i/del-svc-errs) (not name)) (bomb summary)
      (and (= cmd i/visit (i/visit? options))) (bomb summary))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        cmd (first arguments) name (:name options) file (:file options)]
    (handle-errors options arguments errors summary)
    (println "\n")
    (cond
      (= cmd i/svcs) (projects options)
      (= cmd i/svc) (project options)
      (= cmd i/svc-usg) (project-usage options)
      (= cmd i/add-svcs) (add-projects options)
      (= cmd i/del-svc) (delete-project options)
      (= cmd i/svc-errs) (service-errors options)
      (= cmd i/add-svc-err) (add-project-error options)
      (= cmd i/set-svc-err-prob) (set-project-error-prob options)
      (= cmd i/del-svc-errs) (del-project-errors options)
      (= cmd i/visit) (visit options)
      :else (exit 1 (usage-exit summary)))))
