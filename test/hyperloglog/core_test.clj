(ns hyperloglog.core-test
  (:use hyperloglog.core
        midje.sweet)
  (:require [hyperloglog.algorithm :refer [standard-error]]
            [taoensso.carmine :as car]))

(defmacro wcar* [& body] `(car/wcar {} ~@body))

(def test-opts {:num-estimators 1024
                :prefix "hyperloglog-test"})

(fact "add works"
  (wcar* (reset test-opts))
  (wcar* (car/hgetall (:prefix test-opts))) => empty?
  (wcar* (add "foobar" test-opts))
  (wcar* (car/hgetall (:prefix test-opts))) => ["759" "1"])

(fact "fetch-num-leading-zeros-vec works"
  (wcar* (reset test-opts))
  (wcar* (add "foobar" test-opts))
  (wcar* (fetch-num-leading-zeros-vec test-opts)) => (-> (repeat (:num-estimators test-opts) 0)
                                                         vec
                                                         (assoc 759 1)))

(fact "count-distinct works"
  (wcar* (reset test-opts))
  (let [num-items 10000
        expected-error (* num-items (standard-error (:num-estimators test-opts)))]
    (doseq [item (range num-items)]
      (wcar* (add (str item) test-opts)))
    (wcar* (count-distinct test-opts)) => (roughly num-items expected-error)))
