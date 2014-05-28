(defproject protean "0.8.0-pre.2"
  :description "Simulate RESTful API's, easily"
  :url "http://github.com/passivsystems/protean"
  :license {:name "Apache License v2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.incubator "0.1.3"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-codec "1.0.0"]
                 [compojure "1.1.6"]
                 [cheshire "5.3.1"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.taoensso/timbre "3.1.6"]
                 [clj-http "0.9.1"]
                 [me.raynes/laser "1.1.1"]
                 [me.raynes/hickory "0.4.2"]
                 [me.rossputin/diskops "0.1.1"]
                 [me.rossputin/pew "0.1.0"]]
  :plugins [[lein-ring "0.8.7"]]
  :aot :all
  :main protean.core)
