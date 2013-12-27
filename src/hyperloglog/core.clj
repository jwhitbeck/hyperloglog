(ns hyperloglog.core
  (:require [hyperloglog.algorithm :refer [item->index-num-leading-zeros-pair estimate-cardinality]]
            [hyperloglog.hashing :as h]
            [hyperloglog.redis :as redis]
            [taoensso.carmine :as car]))

(set! *warn-on-reflection* true)

(def default-opts
  {:num-observables 1024
   :prefix "hyperloglog"
   :hash-fn h/murmur128->64})

(defn add
  "Adds item to the hyperloglog counter. Takes an optional options map. The following options are available:
    - num-observables: The number of hyperloglog observables. Should be a power of two. (default 1024)
    - prefix:          The redis key under which to store the hyperloglog observables (default hyperloglog).
    - hash-fn:         The item->long 64 bit hash function that will be applied to the item to determine the
                       hyperloglog index and num-leading-zeros observable.
                       By default, `hyperloglog.hashing/murmur128->64`."
  [item & opts]
  (let [{:keys [hash-fn prefix num-observables]} (apply merge default-opts opts)
        [idx num-leading-zeros] (item->index-num-leading-zeros-pair item hash-fn num-observables)]
    (redis/vset-max prefix idx num-leading-zeros)))

(defn fetch-observables
  "Returns the vector of hyperloglog num-leading-zeros observables. Useful for retrieving observables from
  several different hyperloglog counters before merging them.

  Takes an optional opts argument that should be the same as the opts map that was used to add the item to the
  hyperloglog counter."
  [& opts]
  (let [opts (apply merge default-opts opts)]
    (redis/vget (:num-observables opts) (:prefix opts))))

(defn count-distinct
  "Returns the hyperloglog estimate of the number of different items added into the counter.

  Takes an optional opts argument that should be the same as the opts map that was used to add the item to the
  hyperloglog counter. See `add` for the available options."
  [& opts]
  (let [opts (apply merge default-opts opts)]
    (->> (redis/vget (:num-observables opts) (:prefix opts))
         (car/parse estimate-cardinality))))

(defn reset
  "Resets the hyperlolog counter pointed at by the optional opts argument. See `add` for the available
  options."
  [& opts]
  (let [opts (apply merge default-opts opts)]
    (car/del (:prefix opts))))
