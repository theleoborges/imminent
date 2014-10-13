(ns imminent.concurrent-test
  (:require [imminent.core :as core]
            [clojure.test :refer :all]))

(set! *warn-on-reflection* true)

;; This test suite uses imminent.executors/default-executor, backed by a real thread pool
;; Tests in here will tend to be slower in nature but important to make sure we are not
;; causing concurrency related issues

;; These tests make heavy use of core/await, which blocks on the future making testing async
;; code simpler

(def failed-future (core/failed-future (ex-info "error" {})))

(defn fact-seq []
  (iterate (fn [[prev fact]]
             (let [next (inc prev)]
               [next (*' next fact)]))
           [0 1]))

(defn slow-fact [n]
  (Thread/sleep (rand 900))
  (-> (take (inc n) (fact-seq))
      last
      second))

(deftest parallel-factorial
  (let [ns [10 20 30]
        fs (map (comp core/future-call #(partial slow-fact %))
                ns)
        result (-> (core/sequence fs)
                   core/await
                   deref)]
    (is (= [3628800
            2432902008176640000
            265252859812191058636308480000000N]
           @result))))


(deftest await-timeout
  (testing "success"
    (let [result (-> (core/future-call (fn [] 42))
                     (core/await 1000)
                     deref)]
      (is (instance? imminent.result.Success result))
      (is (=  42 @result))))

  (testing "timeout"
    (let [never-ending-future (core/->future (core/promise))
          result @(core/await never-ending-future 10)]
      (is (instance? imminent.result.Failure result))
      (is (instance? java.util.concurrent.TimeoutException (deref result))))))

(def ^:dynamic *myvalue* 7)
(deftest thread-bindings
  (binding [*myvalue* 42]
    (let [result (->> (repeat 3 (fn [] *myvalue*))
                      (map core/future-call)
                      (core/reduce + 0)
                      core/await
                      deref
                      deref)]
      (is (= result
             126)))))
