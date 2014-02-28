(defproject hyperloglog "0.2.2"
  :description "A redis-backed hyperloglog implementation in Clojure"
  :url "https://github.com/jwhitbeck/hyperloglog"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[byte-streams "0.1.7"] ; included to get the AOT bug fix
                 [byte-transforms "0.1.1"]
                 [com.taoensso/carmine "2.4.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [org.codehaus.jsr166-mirror/jsr166y "1.7.0"] ; Needed for 1.5 reducers
                 [potemkin "0.3.3"]])
