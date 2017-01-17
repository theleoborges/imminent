(defproject com.leonardoborges/imminent "0.1.3-SNAPSHOT"
  :description "A composable Futures library for Clojure"
  :url "https://github.com/leonardoborges/imminent"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/test.check "0.5.8"]
                 [uncomplicate/fluokitten "0.3.0"]
                 [org.clojure/core.match "0.2.1"]]
  :plugins [[codox "0.8.10"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
