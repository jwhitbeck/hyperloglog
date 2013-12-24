(defproject hyperloglog "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[byte-transforms "0.1.0"]
                 [com.taoensso/carmine "2.4.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [org.codehaus.jsr166-mirror/jsr166y "1.7.0"] ; Needed for 1.5 reducers
                 [potemkin "0.3.3"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.1.1"]]}})
