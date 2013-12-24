(ns hyperloglog.time-series-test
  (:use hyperloglog.time-series
        midje.sweet)
  (:require [hyperloglog.algorithm :refer [merge-num-leading-zeros-vecs standard-error zero-vec]]
            [midje.util :refer [testable-privates]]
            [taoensso.carmine :as car]))

(testable-privates hyperloglog.time-series seconds-since-epoch time-bucket-id time-bucket-prefix)

(defmacro wcar* [& body] `(car/wcar {} ~@body))

(def test-opts {:num-estimators 1024
                :prefix "hyperloglog-test"
                :bucket-length minute
                :max-history (* 10 minute)})

(fact "latest-complete-prefixes works"
  (latest-complete-prefixes minute test-opts) => [(str (:prefix test-opts) ":0")]
  (provided (#'hyperloglog.time-series/seconds-since-epoch) => minute)
  (latest-complete-prefixes minute test-opts) => [(str (:prefix test-opts) ":0")]
  (provided (#'hyperloglog.time-series/seconds-since-epoch) => 90)
  (latest-complete-prefixes (* 2 minute) test-opts) => [(str (:prefix test-opts) ":0")]
  (provided (#'hyperloglog.time-series/seconds-since-epoch) => 90)
  (latest-complete-prefixes (* 2 minute) test-opts) => [(str (:prefix test-opts) ":1")
                                                        (str (:prefix test-opts) ":0")]
  (provided (#'hyperloglog.time-series/seconds-since-epoch) => 120)
  (latest-complete-prefixes (* 2 minute) test-opts) => [(str (:prefix test-opts) ":3")
                                                        (str (:prefix test-opts) ":2")]
  (provided (#'hyperloglog.time-series/seconds-since-epoch) => 260))

(defn- now [] (seconds-since-epoch))
(defn- minutes-ago [n] (- (seconds-since-epoch) (* n minute)))

(fact "add-at works"
  (wcar* (reset test-opts))
  (wcar* (add-at "foobar" (minutes-ago 2) test-opts))
  (->> (wcar* (fetch-num-leading-zeros-vecs-for-prefixes (latest-complete-prefixes minute test-opts)
                                                        test-opts))
       (apply merge-num-leading-zeros-vecs))
    => (zero-vec (:num-estimators test-opts))
  (->> (wcar* (fetch-num-leading-zeros-vecs-for-prefixes (latest-complete-prefixes (* 3 minute) test-opts)
                                                        test-opts))
      (apply merge-num-leading-zeros-vecs))
    =not=> (zero-vec (:num-estimators test-opts))
  ;; Expires are set correctly
  (wcar* (car/ttl (-> (minutes-ago 2)
                      (time-bucket-id (:bucket-length test-opts))
                      (#(time-bucket-prefix (:prefix test-opts) %)))))
    => (roughly (:max-history test-opts) (:bucket-length test-opts)))

(fact "count-latest-distinct works"
  (wcar* (reset test-opts))
  (let [num-items 10000
        expected-error (* num-items (standard-error (:num-estimators test-opts)))]
    (doseq [item (range num-items)]
      (wcar* (add-at (str item) (minutes-ago 2) test-opts)))
    (wcar* (count-latest-distinct (:max-history test-opts) test-opts))
      => (roughly num-items expected-error)))
