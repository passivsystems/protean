(defproject protean "0.11.0-pre.5"
  :description "Take control of your RESTful API's, simulate, doc, test easily."
  :url "http://github.com/passivsystems/protean"
  :license {:name "Apache License v2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/core.incubator "0.1.3"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.namespace "0.2.7"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-codec "1.0.0"]
                 [compojure "1.3.4"]
                 [cheshire "5.6.3"]
                 [environ "1.0.0"]
                 [com.taoensso/timbre "4.7.4"]
                 [clj-http "3.0.1"]
                 [io.aviso/pretty "0.1.30"]
                 [com.novemberain/pantomime "2.3.0"]
                 [expectations "2.0.13"]
                 [lockedon/if-let "0.1.0"]
                 [me.rossputin/diskops "0.4.1"]
                 [org.silkyweb/silk "0.9.0"]
                 [me.rossputin/pew "0.1.0"]
                 [alandipert/enduro "1.2.0"]
                 [com.gfredericks/test.chuck "0.2.7"]
                 [org.slf4j/slf4j-simple "1.7.7"]
                 [overtone/at-at "1.2.0"]
                 [com.github.fge/json-schema-validator "2.1.7"]
                 [aysylu/loom "0.5.0"]
                 [joda-time/joda-time "2.7"]
                 [clj-time "0.9.0"]
                 [com.cemerick/pomegranate "0.3.0"]
                 [yaclot "0.1.5"]
                 [json-path "0.2.0"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-expectations "0.0.8"]]
  :aot :all
  :uberjar-name ~(str (-> "project.clj" slurp read-string (nth 1)) "-" (-> "project.clj" slurp read-string (nth 2)) "-standalone.jar")
  :main protean.cli.main)
