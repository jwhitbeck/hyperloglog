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

;;; We use a lua script for merging potentially very long lists of observables redis-side.
(def ^:private map-max-lua-script
  (->> ["local merged_observable = {}"
        "for ikey,hkey in ipairs(KEYS) do" ; For each provided key
        ; Get the raw redis hash. e.g. ["0" "2" "1" "5"]
        "  local raw_observable = redis.call('hgetall',hkey)"
        "  local i = 1"
        "  while i < #raw_observable do"
        ; We iterate the raw_observable 2 by 2 as it is a flattened list of key/value pairs
        "    local key = raw_observable[i]"
        "    local value = raw_observable[i+1]"
        "    local merged_value = merged_observable[key]"
        "    if not merged_value or merged_value < value then"
        "      merged_observable[key] = value"
        "    end"
        "    i = i + 2"
        "  end"
        "end"
        ; Format the redis response by flattening all the key/value pairs into a lua array
        "local redis_response = {}"
        "for k,v in pairs(merged_observable) do"
        "  table.insert(redis_response,k)"
        "  table.insert(redis_response,v)"
        "end"
        "return redis_response"]
       (interpose "\n")
       (apply str)))

(defn maxmapvget
  "Retrieves the redis hashes for the provided keys, merges then into a single hash using the 'max' operation
  on the redis server, and returns the result as a vector of n elements. If an element is missing, its value
  is set to zero."
  [n & keys]
  (->> (apply car/eval* map-max-lua-script (count keys) keys)
       car/with-replies
       (car/parse (partial parse-vec n))
       car/return))
