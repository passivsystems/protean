(ns protean.cli.main
  "A basic command line interface for Protean."
  (:require [clojure.string :as s]
            [clojure.edn :as edn]
            [clojure.java.io :refer [file]]
            [clojure.tools.cli :refer [parse-opts]]
            [io.aviso.ansi :as aa]
            [protean.cli.interface :as i]
            [protean.config :as conf]
            [protean.api.transformation.coerce :as c]
            [protean.core.command.bridge :as b]
            [protean.api.codex.reader :as r]
            [protean.api.codex.document :as d]
            [me.rossputin.diskops :as dsk]
            [protean.server.main :as ps])
  (:use protean.cli.simadmin)
  (:import java.net.URI java.io.File)
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
   ["-d" "--directory DIRECTORY" "Project directory"]
   ["-b" "--body BODY" "JSON body"]
   ["-s" "--status-err STATUS-ERROR" "Error status code"]
   ["-h" "--help"]])

(defn- usage-hud [options-summary]
  (->> [""
        "PROTEAN_CODEX_DIR: " (conf/codex-dir)
        ""
        "Usage: protean [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "Codex actions:"
        ;"  visit                  -f codex -b body (Visit node(s) with probe(s) to doc etc)"
        ;"                           e.g. To generate documentation"
        ;"                             visit -f sample-petstore.cod.edn -b '{\"locs\":[\"petstore\"], \"commands\":[\"doc\"], \"directory\":\"silk_templates/data/protean-api\"}'"
        ;"                           e.g. To run tests against a server"
        ;"                             visit -f sample-petstore.cod.edn -b '{\"host\": \"localhost\", \"port\": 3000, \"locs\":[\"petstore\"], \"commands\":[\"test\"], \"config\":{\"test-level\":1}}'"
        ;""
        "  doc                    -f codex (A shortcut to the doc visit command - makes some assumptions about defaults)"
        "                            e.g. To generate documentation"
        "                              doc -f sample-petstore.cod.edn"
        ""
        "  test                   -f codex (A shortcut to the test visit command - makes assumptions about defaults)"
        "                            e.g. To integration test the sample-petstore service"
        "                              test -f sample-petstore.cod.edn"
        "                              test -f sample-petstore.cod.edn -b '{\"seed\": {\"tokenValue\": \"VALID_TOKEN\"}}'"
        ""
        "  sim                     -d /path/to/codex"
        "                             e.g. To start a sim server for mycodex.cod.edn"
        "                               sim -d /path/to/mycodex"
        ""
        "Interact with running Protean server:"
        "  services               (List services)"
        "  service                -n myservice (List service)"
        "  service-usage          -n myservice (List curl statements to use API)"
        "  add-services           -f service-config-file.cod.edn (Add services in a codex)"
        "  del-service            -n myservice (Delete a service)"
        "  sims                   (List sims)"
        "  add-sims               -f sim-config-file.sim.edn (Add sims in a codex)"
        "  del-sim                -n myservice (Delete a sim)"
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
  ; TODO fail if b has no commands?
  (let [b (sane-corpus (c/clj body))]
    (println (aa/bold-green "Exploring quadrant..."))
    (let [codices (r/read-codex (File. file))]
      (b/visit b codices)
      (println (aa/bold-green "...finished exploring quadrant")))))

(defn- doc
  "If no corpus is passed in to a visit doc command - guess sensible defaults"
  [{:keys [file]}]
  (let [codices (r/read-codex (File. file))
        svc (ffirst (filter #(= (type (key %)) String) codices))
        b (c/js {:locs [svc] :commands [:doc]})
        options {:host nil :port nil :file file :body b}
        cm (if (.contains (conf/os) "Mac") "open" "firefox")
        site-dir (str (conf/target-dir) "/site/index.html")
        abs-site-dir (if (dsk/as-relative site-dir) (str (dsk/pwd) "/" site-dir) site-dir)]
    (visit options)
    (println "Please see your docs, as demonstrated below.")
    (println (aa/bold-green (str cm " " abs-site-dir)) "\n")))


(defn- integration-test
  "If no corpus is passed in to a visit test command - guess sensible defaults"
  [{:keys [host port file body]}]
  (let [codices (r/read-codex (File. file))
        svc (ffirst (filter #(= (type (key %)) String) codices))
        b (c/js (merge
            {:locs [svc] :commands [:test] :config {:test-level 1}}
            (c/clj body true)))
        options {:host host :port port :file file :body b}]
    (visit options)))

(defn- sim [{:keys [host port directory body]}] (ps/start directory))


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
      (and (= cmd i/add-sims) (not file)) (bomb summary)
      (and (= cmd i/del-sim) (not name)) (bomb summary)
      (and (= cmd i/visit (i/visit? options))) (bomb summary)
      (and (= cmd i/doc (i/doc? options))) (bomb summary)
      (and (= cmd i/int-test (i/int-test? options))) (bomb summary)
      (and (= cmd i/sim (i/sim? options))) (bomb summary))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        cmd (first arguments) name (:name options) file (:file options)]
    (handle-errors options arguments errors summary)
    (println "\n")
    (cond
      (= cmd i/svcs) (services options)
      (= cmd i/svc) (service options)
      (= cmd i/svc-usg) (service-usage options)
      (= cmd i/add-svcs) (add-services options)
      (= cmd i/del-svc) (delete-service options)
      (= cmd i/sims) (sims options)
      (= cmd i/add-sims) (add-sims options)
      (= cmd i/del-sim) (delete-sim options)
      (= cmd i/visit) (visit options)
      (= cmd i/doc) (doc options)
      (= cmd i/int-test) (integration-test options)
      (= cmd i/sim) (sim options)
      :else (exit 1 (usage-exit summary)))
    (shutdown-agents))) ; write graph image file seems to create threads which are not shutdown
