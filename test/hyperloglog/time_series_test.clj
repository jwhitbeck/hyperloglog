(ns hyperloglog.time-series-test
  (:use hyperloglog.time-series
        clojure.test)
  (:require [hyperloglog.algorithm :refer [merge-observables standard-error zero-vec]]
            [taoensso.carmine :as car]))

(defmacro wcar* [& body] `(car/wcar {} ~@body))

(def test-opts {:num-observables 1024
                :prefix "hyperloglog-test"
                :bucket-length minute
                :max-history (* 10 minute)})

(deftest latest-complete-prefixes-test
  (with-redefs [hyperloglog.time-series/seconds-since-epoch (constantly minute)]
    (is (= [(str (:prefix test-opts) ":0")] (latest-complete-prefixes minute test-opts))))
  (with-redefs [hyperloglog.time-series/seconds-since-epoch (constantly 90)]
    (is (= [(str (:prefix test-opts) ":0")] (latest-complete-prefixes minute test-opts))))
  (with-redefs [hyperloglog.time-series/seconds-since-epoch (constantly 90)]
    (is (= [(str (:prefix test-opts) ":0")] (latest-complete-prefixes (* 2 minute) test-opts))))
  (with-redefs [hyperloglog.time-series/seconds-since-epoch (constantly 120)]
    (is (= [(str (:prefix test-opts) ":1") (str (:prefix test-opts) ":0")]
           (latest-complete-prefixes (* 2 minute) test-opts))))
  (with-redefs [hyperloglog.time-series/seconds-since-epoch (constantly 260)]
    (is (= [(str (:prefix test-opts) ":3") (str (:prefix test-opts) ":2")]
           (latest-complete-prefixes (* 2 minute) test-opts)))))

(defn- now [] (#'hyperloglog.time-series/seconds-since-epoch))
(defn- minutes-ago [n] (- (#'hyperloglog.time-series/seconds-since-epoch) (* n minute)))

(deftest add-at-test
  (wcar* (reset test-opts))
  (wcar* (add-at "foobar" (minutes-ago 2) test-opts))
  (is (= (wcar* (fetch-observables-for-prefixes (latest-complete-prefixes minute test-opts) test-opts))
         (zero-vec (:num-observables test-opts))))
  (is (not= (wcar* (fetch-observables-for-prefixes (latest-complete-prefixes (* 3 minute) test-opts)
                                                   test-opts))
            (zero-vec (:num-observables test-opts))))
  (let [expire-timestamp
          (wcar* (car/ttl (-> (minutes-ago 2)
                              (#'hyperloglog.time-series/time-bucket-id (:bucket-length test-opts))
                              (#(#'hyperloglog.time-series/time-bucket-prefix (:prefix test-opts) %)))))]
    (is (and (<= expire-timestamp (+ (:max-history test-opts) (:bucket-length test-opts)))
             (>= expire-timestamp (- (:max-history test-opts) (:bucket-length test-opts))))
        "expires are set correctly")))

(deftest count-latest-distinct-test
  (wcar* (reset test-opts))
  (let [num-items 10000
        expected-error (* num-items (standard-error (:num-observables test-opts)))]
    (doseq [item (range num-items)]
      (wcar* (add-at (str item) (minutes-ago 2) test-opts)))
    (let [num-estimated (wcar* (count-latest-distinct (:max-history test-opts) test-opts))]
      (is (and (> num-estimated (- num-items expected-error))
               (< num-estimated (+ num-items expected-error)))))))
