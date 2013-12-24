(ns hyperloglog.algorithm
  (:require [clojure.core.reducers :as r]
            [clojure.math.numeric-tower :as math]))

(set! *warn-on-reflection* true)

(defn standard-error
  "Returns the asymptotic standard-error, defined a sqrt(variance(estimate))/num-samples.
   See Flajolet et al. 2007 for full discussion."
  [num-estimators] (/ 1.04 (math/sqrt num-estimators)))

(defn- mask-first-n-bits-64
  "Returns the number corresponding to the first n bits of x in its 64-bit binary representation."
  [^long x n]
  (bit-and x (bit-shift-right (bit-set 0 63) (dec n))))

(defn- mask-last-n-bits-64
  "Returns the number corresponding to the last n bits of x in its 64-bit binary representation."
  [^long x n]
  (bit-and-not x (bit-shift-right (bit-set 0 63) (- 63 n))))

(defn- num-leading-zeros-starting-at
  "Returns the number of zeros in the binary representation of x starting at the bit in position
  'pos' (included)."
  [^long x pos]
  {:pre [(< pos 64) (>= pos 0)]}
  (->> (map bit-test (repeat x) (iterate dec pos))
       (take (inc pos))
       (take-while false?)
       count))

(defn- hamming-weight
  "Returns the hamming weight of the binary representation of x."
  [^long x]
  (->> (map bit-test (repeat x) (range 0 63))
       (filter true?)
       count))

(defn- power-of-two?
  "Returns true if x is a power of two."
  [^long x]
  (= (hamming-weight x) 1))

(defn- log2
  "Returns the base 2 logarithm of x. Assumes x is a power of two."
  [^long x]
  {:pre [(power-of-two? x)]}
  (- 63 (num-leading-zeros-starting-at x 63)))

(defn- hash->index
  "Returns the index of the hyperloglog counter for the provided hash h and index-bits."
  [h index-bits]
  (-> (mask-first-n-bits-64 h index-bits)
      (bit-shift-right (- 64 index-bits))
      (mask-last-n-bits-64 index-bits)))

(defn- hash->num-leading-zeros
  "Returns the number of leading zeros for the part of hash h that is after index-bits."
  [h index-bits]
  (-> (mask-last-n-bits-64 h (- 64 index-bits))
      (num-leading-zeros-starting-at (- 63 index-bits))))

(defn item->index-num-leading-zeros-pair
  "Returns the hyperloglog [index, num-leading-zeros] pair for hash-fn(item)."
  [item hash-fn num-estimators]
  (let [h (hash-fn item)
        index-bits (log2 num-estimators)]
    [(hash->index h index-bits) (hash->num-leading-zeros h index-bits)]))

(defn- naive-cardinality-estimate
  "Returns a cardinality estimate of a set based on the maximum number of leading zeros observed on the hashes
  of items in the set. For example, if in a stream of bit-patterns, we observe an item starting with 00000001,
  then we can estimate that there are roughly 2^(7+1) items in the stream."
  [max-num-leading-zeros]
  (bit-shift-left 1 (inc max-num-leading-zeros)))

(defn- harmonic-mean [numbers]
  (->> (r/map (partial / 1) numbers)
       (r/fold +)
       (/ 1)))

(defn- get-bias-correction
  "Returns the hyperloglog bias correction parameter. See Flajolet et al. 2007 for full discussion."
  [^long num-estimators]
  {:pre [(power-of-two? num-estimators) (>= num-estimators 16)]}
  (case num-estimators
    16 0.673
    32 0.697
    64 0.709
    (/ 0.7213 (+ 1 (/ 1.079 num-estimators)))))

(defn estimate-cardinality
  "Estimates the cardinality of a set using the hyperloglog estimator based on the provided vectors of
  max-num-leading-zeros. If more than one such vector is provided, these vectors are first merged taking the
  max for all values at each index position."
  [max-num-leading-zeros-vec]
  (let [num-estimators (count max-num-leading-zeros-vec)]
    (int (* (get-bias-correction num-estimators)
            num-estimators
            num-estimators
            (harmonic-mean (r/map naive-cardinality-estimate max-num-leading-zeros-vec))))))

(defn zero-vec
  "Returns a vector of n elements initialized to 0."
  [n]
  (vec (repeat n 0)))

(defn merge-num-leading-zeros-vecs
  "Merges num-leading-zeros vectors from hyperloglog counters with the same number of estimators."
  [& vecs]
  {:pre [(->> vecs (map count) (group-by identity) count (= 1))]}
  (apply map max vecs))

(defn count-distinct
  "Counts the number of distinct items using using hyperloglog with num-estimators. This is a local non-redis
  implementation for testing purposes."
  [num-estimators items]
  (->> items
       (map #(item->index-num-leading-zeros-pair % identity num-estimators))
       (reduce (fn [m [idx v]] (assoc m idx (max (m idx) v))) (zero-vec num-estimators))
       estimate-cardinality))
