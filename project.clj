(defproject protean "0.9.0-SNAPSHOT"
  :description "Simulate RESTful API's, easily"
  :url "http://github.com/passivsystems/protean"
  :license {:name "Apache License v2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.incubator "0.1.3"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [ring/ring-core "1.3.0"]
                 [ring/ring-codec "1.0.0"]
                 [compojure "1.1.8"]
                 [cheshire "5.3.1"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.taoensso/timbre "3.2.1"]
                 [clj-http "0.9.2"]
                 [me.raynes/laser "1.1.1"]
                 [me.raynes/hickory "0.4.2"]
                 [me.rossputin/diskops "0.2.0"]
                 [me.rossputin/pew "0.1.0"]
                 [com.passivsystems/protean-core "0.1.0-SNAPSHOT"]]
  :plugins [[lein-ring "0.8.10"]]
  :aot :all
  :main protean.core)
