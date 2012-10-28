(defproject twitter-sampler "1.0.0-SNAPSHOT"
  :description "Build a corpus of tweets using the sample streaming api."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [cheshire "4.0.3"]
                 [org.clojure/tools.cli "0.2.2"]
                 [twitter-api "0.6.11"]]
  :main com.textjuicer.twitter.sampler)