(ns hyperloglog.algorithm-test
  (:use hyperloglog.algorithm
        clojure.test)
  (:require [hyperloglog.hashing :refer [rand-long]]))

(deftest mask-first-n-bits-test
  (is (= (Long/toBinaryString (#'hyperloglog.algorithm/mask-first-n-bits-64 -1 10))
         "1111111111000000000000000000000000000000000000000000000000000000"))
  (is (= (Long/toBinaryString (#'hyperloglog.algorithm/mask-last-n-bits-64 -1 10))
         "1111111111")))

(deftest num-leading-zeros-starting-at-test
  (is (= 64 (#'hyperloglog.algorithm/num-leading-zeros-starting-at 0 63)))
  (is (= 0(#'hyperloglog.algorithm/num-leading-zeros-starting-at 1 0)))
  (is (= 3 (#'hyperloglog.algorithm/num-leading-zeros-starting-at (Long/parseLong "0001001" 2) 6))))

(deftest item->index-num-leading-zeros-pair-test
  (let [item (Long/parseLong "0110001111100000000000000000000000000000000000000000000000000000" 2)]
    (is (= [6 2] (#'hyperloglog.algorithm/item->index-num-leading-zeros-pair item identity 16)))
    (is (= [0 60] (#'hyperloglog.algorithm/item->index-num-leading-zeros-pair 0 identity 16)))))

(deftest harmonic-mean-test
  (is (= (#'hyperloglog.algorithm/harmonic-mean [4 3 3])
         (/ 1 (+ (/ 1 4) (/ 1 3) (/ 1 3))))))

(deftest hamming-weight-test
  (is (= 6 (#'hyperloglog.algorithm/hamming-weight (Long/parseLong "11101011" 2)))))

(deftest power-of-two?-test
  (is (= true (#'hyperloglog.algorithm/power-of-two? 32)))
  (is (= false (#'hyperloglog.algorithm/power-of-two? 34))))

(deftest log2-test
  (is (= 5 (#'hyperloglog.algorithm/log2 32)))
  (is (= 7 (#'hyperloglog.algorithm/log2 128)))
  (is (thrown? AssertionError (#'hyperloglog.algorithm/log2 200))))

(deftest naive-cardinality-estimate-test
  (is (= 32 (#'hyperloglog.algorithm/naive-cardinality-estimate 4))))

(deftest estimate-cardinality-test
  (is (= 172 (estimate-cardinality (repeat 16 3)))))

(deftest merge-observables-test
  (is (= [1 1 2] (merge-observables [0 0 2] [1 1 1])))
  (is (thrown? AssertionError (merge-observables [0 0 1] [1 1])))
  (is (thrown? AssertionError (merge-observables))))

(deftest get-bias-correction-test
  (is (thrown? AssertionError (#'hyperloglog.algorithm/get-bias-correction 8)))
  (is (thrown? AssertionError (#'hyperloglog.algorithm/get-bias-correction 20)))
  (is (= 0.673 (#'hyperloglog.algorithm/get-bias-correction 16)))
  (is (= 0.7152704932638152 (#'hyperloglog.algorithm/get-bias-correction 128))))

(deftest hyperloglog-accuracy-test
  (let [num-estimators 1024 ; 2^10
        num-items 10000 ; 10k
        expected-error (* num-items (standard-error num-estimators))
        num-series 10
        item-series (repeatedly num-series #(repeatedly num-items rand-long)) ; 10 series of 10k items
        hyperloglog-counts (map (partial count-distinct num-estimators) item-series)
        mean-estimate (long (/ (reduce + hyperloglog-counts) num-series))]
    (is (and (< mean-estimate (+ num-items expected-error))
             (> mean-estimate (- num-items expected-error))))))
