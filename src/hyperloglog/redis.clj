(ns hyperloglog.redis
  (:require [hyperloglog.algorithm :refer [zero-vec]]
            [taoensso.carmine :as car]))

(set! *warn-on-reflection* true)

(def ^:private vset-max-lua-script
  (->> ["local key = KEYS[1]"
        "local pos = KEYS[2]"
        "local val = tonumber(ARGV[1])"
        "local m = tonumber(redis.call('hget',key,pos))"
        "if m == nil or m < val then"
        "  redis.call('hset',key,pos,val)"
        "end"]
       (interpose "\n")
       (apply str)))

(defn vset-max
  "Sets the value at position i of the vector under key k to n."
  [k ^long i ^long n] (car/eval* vset-max-lua-script 2 k i n))

(defn- parse-vec [n redis-response]
  (->> (map car/as-long redis-response)
       (partition 2)
       (reduce (fn [parsed-vec [idx n]] (assoc parsed-vec idx n)) (zero-vec n))))

(defn vget
  "Retrieves redis hash under key k and parses it as a vector of n elements. If an element is missing, it is
  initialized to zero."
  [^long n k]
  (->> (car/hgetall k) (car/parse (partial parse-vec n)) car/with-replies car/return))

(defn mvget
  "Returns a list of multiple n-element vectors under the provided keys."
  [n & keys]
  (->> (mapv (partial vget n) keys)
       (car/with-replies :as-pipeline)
       (car/parse nil) ; prevent external parsers from leaking into this internal logic
       car/return))
