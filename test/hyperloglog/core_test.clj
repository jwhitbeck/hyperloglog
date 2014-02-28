(ns hyperloglog.core-test
  (:use hyperloglog.core
        clojure.test)
  (:require [hyperloglog.algorithm :refer [standard-error]]
            [taoensso.carmine :as car]))

(defmacro wcar* [& body] `(car/wcar {} ~@body))

(def test-opts {:num-observables 1024
                :prefix "hyperloglog-test"})

(deftest add-test
  (wcar* (reset test-opts))
  (is (empty? (wcar* (car/hgetall (:prefix test-opts)))))
  (wcar* (add "foobar" test-opts))
  (is (= (wcar* (car/hgetall (:prefix test-opts)))
         ["759" "1"])))

(deftest fetch-obversables-test
  (wcar* (reset test-opts))
  (wcar* (add "foobar" test-opts))
  (is (= (wcar* (fetch-observables test-opts))
         (-> (repeat (:num-observables test-opts) 0) vec (assoc 759 1)))))

(deftest count-distinct-test
  (wcar* (reset test-opts))
  (let [num-items 10000
        expected-error (* num-items (standard-error (:num-observables test-opts)))]
    (doseq [item (range num-items)]
      (wcar* (add (str item) test-opts)))
    (let [num-estimated (wcar* (count-distinct test-opts))]
      (is (and (> num-estimated (- num-items expected-error))
               (< num-estimated (+ num-items expected-error)))))))
