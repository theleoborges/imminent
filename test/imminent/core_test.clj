(ns imminent.core-test
  (:require [clojure.test :refer :all]
            [imminent.core    :as core]
            [uncomplicate.fluokitten.protocols :as fkp]
            [imminent.executors :as executors]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer (defspec)]))

(defn setup [f]
  (binding [executors/*executor* executors/blocking-executor]
    (f)))

(use-fixtures :each setup)


(def failed-future (core/failed-future (ex-info "error" {})))

(deftest mapping
  (testing "success"
    (let [result (-> (core/const-future 10)
                     (core/fmap #(* % %))
                     deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) 100)))
    )

  (testing "failure"
    (testing "failed future"
      (let [result (-> failed-future
                       (core/fmap #(* % %))
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))
    ))

(deftest filtering
  (testing "success"
    (let [result (-> (core/const-future 10)
                     (core/filter even?)
                     deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) 10)))
    )

  (testing "failure"
    (testing "failed predicate"
      (let [result (-> (core/const-future 10)
                       (core/filter odd?)
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? java.util.NoSuchElementException (deref result)))))

    (testing "failed future"
      (let [result (-> failed-future
                       (core/filter odd?)
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))))


(deftest flatmapping
  (testing "success"
    (let [result (-> (core/const-future 10)
                     (core/bind (fn [n] (core/const-future (* n n))))
                     deref)]
      (is (instance? imminent.result.Success result))
      (is (= (deref result) 100))))

  (testing "failed future"
    (let [result (-> failed-future
                     (core/bind (fn [n] (core/const-future (* n n))))
                     deref)]
      (is (instance? imminent.result.Failure result))
      (is (instance? clojure.lang.ExceptionInfo (deref result))))))

(defn bad-fn [_] (throw (ex-info "bad, bad fn!" {})))

(deftest exception-handling
  (testing "core functions don't blow up"
    (let [future (core/const-future 10)]
      (are [x y ] (instance? x @y)
           imminent.result.Failure (core/fmap     future bad-fn)
           imminent.result.Failure (core/filter   future bad-fn)
           imminent.result.Failure (core/bind     future bad-fn)
           imminent.result.Failure (core/sequence [failed-future])))))


(deftest zipping
  (testing "success"
    (let [f1     (core/const-future 10)
          f2     (core/const-future 20)
          result @(core/zip f1 f2)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) [10 20]))))

  (testing "failure"
    (testing "failed future"
      (let [f1     (core/const-future 10)
            f2     failed-future
            result @(core/zip f1 f2)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))))

(deftest sequencing
  (testing "success"
    (let [result (-> [(core/const-future 10) (core/const-future 20) (core/const-future 30)]
                     (core/sequence)
                     deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) [10 20 30]))))

  (testing "failure"
    (testing "failed future"
      (let [result (-> [failed-future (core/const-future 10) failed-future]
                       (core/sequence)
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))))

(deftest reducing
  (testing "success"
    (let [result (->> [(core/const-future 10) (core/const-future 20) (core/const-future 30)]
                      (core/reduce + 0)
                      deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) 60))))

  (testing "failure"
    (testing "failed future"
      (let [result (->> [(core/const-future 10) failed-future]
                        (core/reduce + 0)
                        deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))))

(deftest mapping-futures
  (testing "success"
    (let [f      #(core/future (* % %))
          result (-> (core/map-future f [1 2 3])
                     deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) [1 4 9]))))

  (testing "failure"
    (testing "failed future"
      (let [f      #(core/future (bad-fn %))
            result (-> (core/map-future f [1 2 3])
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))))

(deftest filtering-futures
  (testing "success"
    (let [pred?      (comp core/const-future even?)
          result (-> (core/filter-future pred? [10 2 3 4 7])
                     deref)]
      (is (= @result [10 2 4])))))

(deftest joining-futures
  (testing "success"
    (let [ff     (core/const-future (core/const-future 42))
          f      (core/join ff)
          result (-> f deref deref)]
      (is (instance? imminent.future.Future f))
      (is (= result 42)))))

(deftest completion
  (are [x y] (= x y)
       false (core/completed? (core/->future (core/promise)))
       true  (core/completed? (core/const-future "Done."))))

(deftest completion-handlers
  (testing "success"
    (let [result (atom nil)]
      (-> (core/const-future 10) (core/on-success #(reset! result %)))
      (is (= @result 10))))

  (testing "failure"
    (let [result (atom nil)]
      (-> failed-future (core/on-failure #(reset! result %)))
      (is (instance? clojure.lang.ExceptionInfo @result))))

  (testing "completion"
    (testing "success"
      (let [result (atom nil)]
        (-> (core/const-future "success") (core/on-complete #(reset! result %)))
        (is (instance? imminent.result.Success @result))
        (is (= (core/dderef result) "success"))))

    (testing "failure"
      (let [result (atom nil)]
        (-> failed-future (core/on-complete #(reset! result %)))
        (is (instance? imminent.result.Failure @result))
        (is (instance? clojure.lang.ExceptionInfo (core/dderef result)))))))


(deftest ambiguous
  (testing "success"
    (let [f     (core/amb (core/->future (core/promise))
                          (core/const-future 42))
          result (-> f deref deref)]
      (is (instance? imminent.future.Future f))
      (is (= result 42))))

  (testing "failure"
    (let [f      (core/amb (core/->future (core/promise))
                           failed-future)
          result (deref f)]
      (is (instance? imminent.result.Failure result))
      (is (instance? clojure.lang.ExceptionInfo (deref result))))))
