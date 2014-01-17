(ns hyperloglog.redis-test
  (:use hyperloglog.redis
        midje.sweet)
  (:require [taoensso.carmine :as car]))

(defmacro wcar* [& body] `(car/wcar {} ~@body))

(fact "vset-max works"
  (wcar* (car/del "foo")
         (vset-max "foo" 0 2))
  (-> (wcar* (car/hget "foo" 0)) (#(Integer/parseInt %))) => 2
  (wcar* (vset-max "foo" 0 5))
  (-> (wcar* (car/hget "foo" 0)) (#(Integer/parseInt %))) => 5
  (wcar* (vset-max "foo" 0 3))
  (-> (wcar* (car/hget "foo" 0)) (#(Integer/parseInt %))) => 5)

(fact "maxmapvget works"
  (wcar* (car/del "foo" "bar")
         (car/hmset "foo" 0 10 2 3 4 5)
         (car/hmset "bar" 0 2 2 1))
  (wcar* (maxmapvget 10 "foo" "bar")) => [10 0 3 0 5 0 0 0 0 0])

(fact "vget works"
  (wcar* (car/del "foo")
         (car/hset "foo" 0 5)
         (car/hset "foo" 2 10))
  (wcar* (vget 10 "foo")) => [5 0 10 0 0 0 0 0 0 0])
