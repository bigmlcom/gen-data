(defproject bigml/gen-data "0.1.0-SNAPSHOT"
  :description "A tiny project for generating synthetic data"
  :url "https://github.com/bigmlcom/gen-data"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/data.csv "0.1.3"]
                 [bigml/sampling "3.0"]]
  :main bigml.gen-data.core)
