(ns protean.cli.main
  "A basic command line interface for Protean."
  (:require [clojure.string :as stg]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file]]
  	    [clojure.tools.cli :refer [parse-opts]]
            [ring.util.codec :as cod]
  	    [clj-http.client :as clt]
            [io.aviso.ansi :as aa]
            [protean.core.protocol.http :as pth]
            [protean.core.transformation.coerce :as ptc]
            [protean.core.transformation.analysis :as pta]
            [protean.core.transformation.curly :as txc]
            [protean.core.transformation.testy-cljhttp :as tc]
            [protean.core.command.bridge :as b]
            [protean.core.command.test :as t])
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

(defn- body [ctype body]
  (if-let [b body]
    (cond
      (= ctype pth/xml) (ptc/pretty-xml-> b)
      (= ctype pth/txt) b
      :else (ptc/js-> b))
    "N/A"))

(defn- codices->silk [f n d]
  (let [codices (edn/read-string (slurp f))
        locs {:locs (if n (vector n) n)}
        an (pta/analysis-> "host" 1234 codices locs)]
    (doseq [e an]
      (let [uri-path (-> (URI. (:uri e)) (.getPath))
            path  (stg/replace uri-path #"/" "-")
            id (str (name (:method e)) path)
            body (body (get-in e [:codex :content-type]) (get-in e [:codex :body]))
            full (assoc e :id id :path (subs uri-path 1) :curl (cod/url-decode (txc/curly-> e)) :sample-response body)]
        (spit (str d "/" id ".edn") (pr-str (update-in full [:method] name)))))))

(defn- visit [h p f b]
  (println (aa/bold-green "Exploring quadrant..."))
  (let [codices (edn/read-string (slurp f))
        br (b/visit b codices)]
    (println "finished visiting sim")))

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
        "  add-service-error      -n myservice -s 500 (Add an error status code to a service)"
        "  set-service-error-prob -n myservice -l 10 (Set error probability)"
        "  del-service-errors     -n myservice (Delete error response codes)"
        "  doc                    -f codex -n name -d doc-site (Build API docs)"
        "  visit                  -f codex -b body (Visit node(s) with probe(s) to doc, test etc"
        ""
        "Please refer to the manual page for more information."]
       (stg/join \newline)))

(defn- usage [options-summary] (cli-banner) (usage-hud options-summary))

(defn- usage-exit [options-summary] (usage-hud options-summary))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (stg/join \newline errors)))

(defn- exit [status msg] (println msg) (System/exit status))


;; =============================================================================
;; Domain functionality
;; =============================================================================

(defn doc [{:keys [host port file name directory] :as options}]
  (codices->silk file name directory))

(defn visit [{:keys [host port file body] :as options}]
  (let [b (sane-corpus (ptc/clj-> body))]
    (println (aa/bold-green "Exploring quadrant..."))
    (let [codices (edn/read-string (slurp file))
          bres (b/visit b codices)]


      (b/analyse b codices bres)
      (println (aa/bold-green "...finished exploring quadrant")))))


;; =============================================================================
;; Application entry point
;; =============================================================================

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors))
      (and (= (first arguments) "service")
           (not (:name options))) (exit 0 (usage summary))
      (and (= (first arguments) "add-services")
           (not (:file options))) (exit 0 (usage summary))
      (and (= (first arguments) "del-service")
           (not (:name options))) (exit 0 (usage summary))
      (and (= (first arguments) "add-service-error")
           (or (not (:name options))
               (not (:status-err options)))) (exit 0 (usage summary))
      (and (= (first arguments) "set-service-error-prob")
           (or (not (:name options))
               (not (:level options)))) (exit 0 (usage summary))
      (and (= (first arguments) "del-service-errors")
           (not (:name options))) (exit 0 (usage summary))
      (and (= (first arguments) "doc")
                (or (not (:name options))
                    (not (:file options))
                    (not (:directory options)))) (exit 0 (usage summary))
      (and (= (first arguments) "visit"
                (or (not (:file options))
                    (not (:body options))))) (exit 0 (usage summary)))
    ;; Execute program with options
    (cli-banner)
    (println "\n")
    (case (first arguments)
      "services" (projects options)
      "service" (project options)
      "service-usage" (project-usage options)
      "add-services" (add-projects options)
      "del-service" (delete-project options)
      "add-service-error" (add-project-error options)
      "set-service-error-prob" (set-project-error-prob options)
      "del-service-errors" (del-project-errors options)
      "doc" (doc options)
      "visit" (visit options)
      (exit 1 (usage-exit summary)))))
