(defproject twitter-sampler "1.0.0-SNAPSHOT"
  :description "Build a corpus of tweets using twitter streaming api."
  :url "https://github.com/apatry/twitter-sampler"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ch.qos.logback/logback-classic "1.1.1"]
                 [cheshire "4.0.3"]
                 [org.clojure/tools.cli "0.2.2"]
                 [twitter-api "0.6.11"]]
  :main com.textjuicer.twitter.sampler)