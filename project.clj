(defproject protean "0.12.0-pre.1"
  :description "Take control of your RESTful API's, simulate, doc, test easily."
  :url "http://github.com/passivsystems/protean"
  :license {:name "Apache License v2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/core.incubator "0.1.4"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-codec "1.0.1"]
                 [compojure "1.6.0"]
                 [cheshire "5.7.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [clj-http "3.6.0"]
                 [io.aviso/pretty "0.1.33"]
                 [com.novemberain/pantomime "2.9.0"]
                 [expectations "2.1.9"]
                 [lockedon/if-let "0.1.0"]
                 [me.rossputin/diskops "0.6.0"]
                 [org.silkyweb/silk "0.11.1"]
                 [alandipert/enduro "1.2.0"]
                 [org.slf4j/slf4j-simple "1.7.25"]
                 [aysylu/loom "1.0.0"]
                 [joda-time/joda-time "2.9.9"]
                 [clj-time "0.13.0"]
                 [com.cemerick/pomegranate "0.3.1"]
                 [json-path "0.3.0"]
                 [protean-api "0.12.0-pre.1"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-expectations "0.0.8"]]
  :aot :all
  :uberjar-name ~(str (-> "project.clj" slurp read-string (nth 1)) "-" (-> "project.clj" slurp read-string (nth 2)) "-standalone.jar")
  :main protean.cli.main)
