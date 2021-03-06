(ns protean.cli.main
  "A basic command line interface for Protean."
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [io.aviso.ansi :as aa]
            [protean.cli.interface :as i]
            [protean.config :as conf]
            [protean.api.transformation.coerce :as c]
            [protean.core.command.bridge :as b]
            [protean.api.codex.reader :as r]
            [me.rossputin.diskops :as dsk]
            [protean.server.main :as ps]
            [protean.cli.simadmin :as admin]
            [hawk.core :as hawk])
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

;; No defaults
(def cli-doc-options
  [["-p" "--port PORT" "Port number"]
   ["-H" "--host HOST" "Host name"]
   ["-f" "--file FILE" "Project configuration file"]
   ["-r" "--reload" "Reload sim server on codex, sim or *.clj change & doc on codex or silk_templates/* change"]])

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
   ["-r" "--reload" "Reload sim server on codex, sim or *.clj change & doc on codex or silk_templates/* change"]
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
        "  doc                    -f codex (A shortcut to the doc visit command - makes some assumptions about defaults)"
        "                            e.g. To generate documentation"
        "                              doc -f examples/petstore/petstore.cod.edn"
        ""
        "  test                   -f codex (A shortcut to the test visit command - makes assumptions about defaults)"
        "                            e.g. To integration test the sample-petstore service"
        "                              test -f examples/petstore/petstore.cod.edn"
        "                              test -f examples/petstore/petstore.cod.edn -b '{\"seed\": {\"tokenValue\": \"VALID_TOKEN\"}}'"
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
        "  del-sim                -f sim-config-file.sim.edn (Delete a sim)"
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

; TODO fail if b has no commands?
(defn- visit
  ([{:keys [file] :as options}]
    (visit options (r/read-codex (conf/protean-home) (io/file file))))
  ([{:keys [host port body]} codex]
    (println (aa/bold-green "Exploring quadrant..."))
    (let [options (merge (sane-corpus (c/clj body)) {:host host :port port})]
      (b/visit options codex)
      (println (aa/bold-green "...finished exploring quadrant")))))

(defn- doc
  "If no corpus is passed in to a visit doc command - guess sensible defaults"
  [{:keys [host port file reload]}]
  (defn gen-doc []
    (let [codex (r/read-codex (conf/protean-home) (io/file file))
          body (c/jsn {:locs (r/services codex) :commands [:doc]})
          options {:host host :port port :file file :body body}
          cm (if (.contains (conf/os) "Mac") "open" "firefox")
          site-dir (str (conf/target-dir) "/site/index.html")
          abs-site-dir (if (dsk/as-relative site-dir) (str (dsk/pwd) "/" site-dir) site-dir)]
      (visit options codex)
      (println "Please see your docs, as demonstrated below.")
      (println (aa/bold-green (str cm " " abs-site-dir)) "\n")))

  (defn handler [ctx _]
    (gen-doc)
    (println "Watching for changes. Press enter to exit")
    ctx)

  (gen-doc)
  (when reload
    (println "Watching for changes. Press enter to exit")
    (hawk/watch! [{:paths [(io/file file)]
                   :filter hawk/file?
                   :handler handler}
                  {:paths [(io/file (dsk/parent (io/file file)) "silk_templates")
                           (io/file (conf/protean-home) "silk_templates")]
                   :filter hawk/file?
                   :handler handler}])
    (loop [input (read-line)]
      (when-not (= "\n" input)
        (System/exit 0)
        (recur (read-line))))))

(defn- integration-test
  "If no corpus is passed in to a visit test command - guess sensible defaults"
  [{:keys [host port file body reload]}]
  (let [codex (r/read-codex (conf/protean-home) (io/file file))
        svc (ffirst (filter #(= (type (key %)) String) codex))
        b (c/jsn (merge
            {:locs [svc] :commands [:test] :config {:test-level 1}}
            (c/clj body true)))
        options {:host host :port port :file file :body b :reload reload}]
    (visit options codex)))

(defn- sim [{:keys [host port directory body reload]}] (ps/start directory reload))


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
      (and (= cmd i/del-sim) (not file)) (bomb summary)
      (and (= cmd i/visit) (i/visit? options)) (bomb summary)
      (and (= cmd i/doc) (i/doc? options)) (bomb summary)
      (and (= cmd i/int-test) (i/int-test? options)) (bomb summary)
      (and (= cmd i/sim) (i/sim? options)) (bomb summary))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        cmd (first arguments) name (:name options) file (:file options)]
    (handle-errors options arguments errors summary)
    (println "\n")
    (cond
      (= cmd i/svcs) (admin/services options)
      (= cmd i/svc) (admin/service options)
      (= cmd i/svc-usg) (admin/service-usage options)
      (= cmd i/add-svcs) (admin/add-services options)
      (= cmd i/del-svc) (admin/delete-service options)
      (= cmd i/sims) (admin/sims options)
      (= cmd i/add-sims) (admin/add-sims options)
      (= cmd i/del-sim) (admin/delete-sim options)
      (= cmd i/visit) (visit options)
      (= cmd i/doc) (doc (:options (parse-opts args cli-doc-options)))
      (= cmd i/int-test) (integration-test (update options :port #(if (= % 3001) 3000 %)))
      (= cmd i/sim) (sim options)
      :else (exit 1 (usage-exit summary)))
    (shutdown-agents))) ; write graph image file seems to create threads which are not shutdown
