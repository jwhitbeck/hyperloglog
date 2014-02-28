(ns hyperloglog.redis-test
  (:use hyperloglog.redis
        clojure.test)
  (:require [taoensso.carmine :as car]))

(defmacro wcar* [& body] `(car/wcar {} ~@body))

(deftest vset-max-test
  (wcar* (car/del "foo")
         (vset-max "foo" 0 2))
  (is (= (-> (wcar* (car/hget "foo" 0)) (#(Integer/parseInt %)))
         2))
  (wcar* (vset-max "foo" 0 5))
  (is (= (-> (wcar* (car/hget "foo" 0)) (#(Integer/parseInt %)))
         5))
  (wcar* (vset-max "foo" 0 3))
  (is (= (-> (wcar* (car/hget "foo" 0)) (#(Integer/parseInt %)))
         5)))

(deftest maxmapvget-test
  (wcar* (car/del "foo" "bar")
         (car/hmset "foo" 0 10 2 3 4 5)
         (car/hmset "bar" 0 2 2 1))
  (is (= (wcar* (maxmapvget 10 "foo" "bar"))
         [10 0 3 0 5 0 0 0 0 0])))

(deftest vget-test
  (wcar* (car/del "foo")
         (car/hset "foo" 0 5)
         (car/hset "foo" 2 10))
  (is (= (wcar* (vget 10 "foo"))
         [5 0 10 0 0 0 0 0 0 0])))
