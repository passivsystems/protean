(defproject protean "0.9.0-pre.7"
  :description "Take control of your RESTful API's, simulate, doc, test easily."
  :url "http://github.com/passivsystems/protean"
  :license {:name "Apache License v2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/core.incubator "0.1.3"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.namespace "0.2.7"]
                 [ring/ring-jetty-adapter "1.3.1"]
                 [ring/ring-core "1.3.1"]
                 [ring/ring-codec "1.0.0"]
                 [compojure "1.1.9"]
                 [cheshire "5.3.1"]
                 [environ "1.0.0"]
                 [com.taoensso/timbre "3.3.1"]
                 [clj-http "1.0.0"]
                 [io.aviso/pretty "0.1.12"]
                 [com.novemberain/pantomime "2.3.0"]
                 [me.raynes/laser "1.1.1"]
                 [me.raynes/hickory "0.4.2"]
                 [me.rossputin/diskops "0.2.0"]
                 [org.silkyweb/silk "0.7.0-pre.1"]
                 [me.rossputin/pew "0.1.0"]
                 [org.databene/databene-benerator "0.9.8"]
                 [org.slf4j/slf4j-simple "1.6.4"]
                 [overtone/at-at "1.2.0"]
                 [com.github.fge/json-schema-validator "2.1.7"]
                 [aysylu/loom "0.5.0"]]
  :plugins [[lein-ring "0.8.10"]]
  :aot :all
  :uberjar-name ~(str (-> "project.clj" slurp read-string (nth 1)) "-" (-> "project.clj" slurp read-string (nth 2)) "-standalone.jar")
  :main protean.cli.main)
