(ns hyperloglog.hashing
  (:require [byte-transforms :as bt]
            [byte-streams :refer [to-byte-buffer]]))

(set! *warn-on-reflection* true)

(defn first-64-bits [x] (-> (to-byte-buffer x) .getLong))

(defn murmur128->64 [x] (-> (bt/hash x :murmur128) to-byte-buffer .getLong))

(defn rand-long [] (murmur128->64 (str (rand))))
