(defproject twitter-sampler "1.0.0-SNAPSHOT"
  :description "Build a corpus of tweets using the sample streaming api."
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ch.qos.logback/logback-classic "1.1.1"]
                 [cheshire "4.0.3"]
                 [org.clojure/tools.cli "0.2.2"]
                 [twitter-api "0.6.11"]]
  :main com.textjuicer.twitter.sampler)