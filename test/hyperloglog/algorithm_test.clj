(ns hyperloglog.algorithm-test
  (:use hyperloglog.algorithm
        midje.sweet)
  (:require [midje.util :refer [testable-privates]]
            [hyperloglog.hashing :refer [rand-long]]))

(testable-privates hyperloglog.algorithm mask-first-n-bits-64 mask-last-n-bits-64
                   num-leading-zeros-starting-at harmonic-mean naive-cardinality-estimate hamming-weight
                   power-of-two? log2 get-bias-correction)

(facts "64 bit masks work"
  (Long/toBinaryString (@mask-first-n-bits-64 -1 10))
    => "1111111111000000000000000000000000000000000000000000000000000000"
  (Long/toBinaryString (@mask-last-n-bits-64 -1 10))
    => "1111111111")

(fact "num-leading-zeros-starting-at works"
  (@num-leading-zeros-starting-at 0 63) => 64
  (@num-leading-zeros-starting-at 1 0) => 0
  (@num-leading-zeros-starting-at (Long/parseLong "0001001" 2) 6) => 3)

(fact "item->index-num-leading-zeros-pair works"
  (let [item (Long/parseLong "0110001111100000000000000000000000000000000000000000000000000000" 2)]
    (item->index-num-leading-zeros-pair item identity 16)) => [6 2]
    (item->index-num-leading-zeros-pair 0 identity 16) => [0 60])

(fact "harmonic mean works"
  (harmonic-mean [4 3 3]) => (/ 1 (+ (/ 1 4) (/ 1 3) (/ 1 3))))

(fact "hamming-weight works"
  (@hamming-weight (Long/parseLong "11101011" 2)) => 6)

(fact "power-of-two? works"
  (@power-of-two? 32) => true
  (@power-of-two? 34) => false)

(fact "log2 works"
  (@log2 32) => 5
  (@log2 128) => 7
  (@log2 200) => (throws AssertionError))

(fact "naive cardinality estimate works"
  (naive-cardinality-estimate 4) => 32)

(fact "estimate cardinality works"
  (estimate-cardinality (repeat 16 3)) => 172)

(fact "merge-observables works"
  (merge-observables [0 0 2] [1 1 1]) => [1 1 2]
  (merge-observables [0 0 1] [1 1]) => (throws AssertionError)
  (merge-observables) => (throws AssertionError))

(fact "get-bias-correction works"
  (@get-bias-correction 8) => (throws AssertionError)
  (@get-bias-correction 20) => (throws AssertionError)
  (@get-bias-correction 16) => 0.673
  (@get-bias-correction 128) => 0.7152704932638152)

(fact "sanity check hyperloglog accuracy"
  (let [num-estimators 1024 ; 2^10
        num-items 10000 ; 10k
        expected-error (* num-items (standard-error num-estimators))
        num-series 10
        item-series (repeatedly num-series #(repeatedly num-items rand-long)) ; 10 series of 10k items
        hyperloglog-counts (map (partial count-distinct num-estimators) item-series)
        mean-estimate (int (/ (reduce + hyperloglog-counts) num-series))]
    mean-estimate => (roughly num-items expected-error)))
