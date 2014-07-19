(ns imminent.core-test
  (:require [clojure.test :refer :all]
            [imminent.core :as core]
            [imminent.executors :as executors]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer (defspec)]))


(def success-gen (gen/fmap core/success (gen/not-empty gen/string-alpha-numeric)))
(def failure-gen (gen/fmap core/failure (gen/not-empty gen/string-alpha-numeric)))
(def future-gen  (gen/fmap core/const-future (gen/not-empty gen/string-alpha-numeric)))

(defn setup [f]
  (binding [executors/*executor* executors/immediate-executor]
    (f)))

(use-fixtures :each setup)

(defn functor-laws-identity [generator]
  (prop/for-all [functor generator]
                (= (core/map functor identity)
                   (identity functor))))

(defn functor-laws-associativity [generator]
  (prop/for-all [functor generator]
                (= (core/map functor (comp count str))
                   (core/map (core/map functor str)
                             count))))

(defn monad-laws-left-identity [pure generator]
  (prop/for-all [a   generator]
                (let [fmb #(pure (str %))]
                  (= (core/bind (pure a) fmb)
                     (fmb a)))))

(defn monad-laws-right-identity [pure generator]
  (prop/for-all [a   generator]
                (let [ma (pure a)]
                  (= (core/bind ma pure)
                     ma))))

(defn monad-laws-associativity [pure generator]
  (prop/for-all [a   generator]
                (let [ma (pure a)
                      f  str
                      g  count]
                  (= (core/bind (core/bind ma f) g)
                     (core/bind ma (fn [x]
                                     (core/bind (f x) g)))))))

(defspec result-functor-laws-identity
  (functor-laws-identity (gen/one-of [success-gen failure-gen])))

(defspec result-functor-laws-associativity
  (functor-laws-associativity (gen/one-of [success-gen failure-gen])))

(defspec future-functor-laws-identity
  (functor-laws-identity future-gen))

(defspec future-functor-laws-associativity
  (functor-laws-associativity future-gen))

(defspec future-monad-laws-left-identity
  (monad-laws-left-identity core/const-future (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-right-identity
  (monad-laws-right-identity core/const-future (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-associativity
  (monad-laws-right-identity core/const-future (gen/not-empty gen/string-alpha-numeric)))


(deftest mapping
  (testing "success"
    (let [result (-> (core/const-future 10)
                     (core/map #(* % %))
                     deref)]

      (is (instance? imminent.core.Success result))
      (is (= (core/raw-value result) 100)))
    )

  (testing "failure"
    (testing "failed future"
      (let [result (-> (core/failed-future (ex-info "error" {}))
                       (core/map #(* % %))
                       deref)]
        (is (instance? imminent.core.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (core/raw-value result)))))
    ))

(deftest filtering
  (testing "success"
    (let [result (-> (core/const-future 10)
                     (core/filter even?)
                     deref)]

      (is (instance? imminent.core.Success result))
      (is (= (core/raw-value result) 10)))
    )

  (testing "failure"
    (testing "failed predicate"
      (let [result (-> (core/const-future 10)
                       (core/filter odd?)
                       deref)]
        (is (instance? imminent.core.Failure result))
        (is (instance? java.util.NoSuchElementException (core/raw-value result)))))

    (testing "failed future"
      (let [result (-> (core/failed-future (ex-info "error" {}))
                       (core/filter odd?)
                       deref)]
        (is (instance? imminent.core.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (core/raw-value result)))))

    ))
