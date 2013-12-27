(ns hyperloglog.time-series
  (:require [hyperloglog.algorithm :refer [merge-observables estimate-cardinality]]
            [hyperloglog.core :as core]
            [hyperloglog.redis :as redis]
            [taoensso.carmine :as car]))

(set! *warn-on-reflection* true)

(def minute 60)
(def hour (* 60 minute))
(def day (* 24 hour))
(def week (* 7 day))

(def default-opts
  (merge core/default-opts
         {:bucket-length hour
          :max-history week}))

(defn- seconds-since-epoch [] (int (/ (System/currentTimeMillis) 1000)))

(defn- time-bucket-id [seconds-since-epoch bucket-length] (int (/ seconds-since-epoch bucket-length)))

(defn- list-available-time-bucket-ids [now {:keys [bucket-length max-history]}]
  (->> (iterate #(- % bucket-length) now)
       (take-while (comp not neg?))
       (map #(time-bucket-id % bucket-length))
       (take-while (partial < (time-bucket-id (- now max-history) bucket-length)))))

(defn- time-bucket-prefix [prefix time-bucket-id] (car/key prefix time-bucket-id))

(defn- opts-with-time-bucket-prefix [opts time-bucket-id]
  (assoc opts :prefix (time-bucket-prefix (:prefix opts) time-bucket-id)))

(defn- expire-at [{:keys [max-history bucket-length]}] (+ max-history bucket-length))

(defn add-at
  "Adds item to the hyperloglog counter corresponding to the time-bucket for the date specified by the
  unix-timestamp. This time bucket will expire in redis after max-history.
  Takes an optional options map. The following options are available in addition to those supported by
  hyperloglog.core/add
    - bucket-length:  The duration in seconds of each time-bucket (default 3600, i.e. one hour).
    - max-history:    Do not keep bucket older than max-history seconds (default 604800, i.e., on week)"
  [item unix-timestamp & opts]
  (let [opts (apply merge default-opts opts)
        opts (->> (time-bucket-id unix-timestamp (:bucket-length opts))
                  (opts-with-time-bucket-prefix opts))]
    (assert (<= (- (seconds-since-epoch) unix-timestamp) (:max-history opts)))
    (core/add item opts)
    (car/expire (:prefix opts) (expire-at opts))))

(defn add-now
  "Add item to the latest time bucket."
  [item & opts]
  (add-at item (seconds-since-epoch) opts))

(defn latest-complete-prefixes
  "Returns the list of prefixes for complete buckets that cover the latest seconds. The number of seconds
  should be a multiple of the bucket length."
  [seconds & opts]
  (let [opts (apply merge default-opts opts)]
    (assert (<= seconds (:max-history opts)))
    (assert (zero? (rem seconds (:bucket-length opts))))
    (let [now (seconds-since-epoch)
          at-bucket-limit? (zero? (rem now (:bucket-length opts)))
          num-buckets-to-take (int (/ seconds (:bucket-length opts)))]
      (->> (list-available-time-bucket-ids now opts)
           rest #_(#(if at-bucket-limit? % (rest %)))
           (take num-buckets-to-take)
           (mapv (partial time-bucket-prefix (:prefix opts)))))))

(defn fetch-observables-list-for-prefixes
  "Returns a vector of the hyperloglog num-leading-zeros observables for the provided prefixes. Useful
  for retrieving observables from several different hyperloglog counters before merging them.

  Takes an optional opts argument that should be the same as the opts map that was used to add the item to the
  hyperloglog counter (see `add-at`)."
  [prefixes & opts]
  (let [opts (apply merge default-opts opts)]
    (apply redis/mvget (:num-observables opts) prefixes)))

(defn count-latest-distinct
  "Returns the hyperloglog estimate of the number of different items added into the counters over the latest
  seconds.

  Takes an optional opts argument that should be the same as the opts map that was used to add the item to the
  hyperloglog counter (see `add-at`)."
  [seconds & opts]
  (let [opts (apply merge default-opts opts)]
    (->> (apply redis/mvget (:num-observables opts) (latest-complete-prefixes seconds opts))
         (car/parse (comp estimate-cardinality (partial apply merge-observables))))))

(defn reset
  "Resets all hyperlolog counters pointed at by the optional opts argument. See `add-at` for the available
  options."
  [& opts]
  (let [opts (apply merge default-opts opts)]
    (doseq [time-bucket-id (list-available-time-bucket-ids (seconds-since-epoch) opts)]
      (core/reset (opts-with-time-bucket-prefix opts time-bucket-id)))))
