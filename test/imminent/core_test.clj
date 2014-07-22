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

(defn monad-laws-left-identity [pure bind generator]
  (prop/for-all [a   generator]
                (let [fmb #(pure (str %))]
                  (= (bind (pure a) fmb)
                     (fmb a)))))

(defn monad-laws-right-identity [pure bind generator]
  (prop/for-all [a   generator]
                (let [ma (pure a)]
                  (= (bind ma pure)
                     ma))))

(defn monad-laws-associativity [pure bind generator]
  (prop/for-all [a   generator]
                (let [ma (pure a)
                      f  str
                      g  count]
                  (= (bind (bind ma f) g)
                     (bind ma (fn [x]
                                (bind (f x) g)))))))

(defspec result-functor-laws-identity
  (functor-laws-identity (gen/one-of [success-gen failure-gen])))

(defspec result-functor-laws-associativity
  (functor-laws-associativity (gen/one-of [success-gen failure-gen])))

(defspec future-functor-laws-identity
  (functor-laws-identity future-gen))

(defspec future-functor-laws-associativity
  (functor-laws-associativity future-gen))

(defspec future-monad-laws-left-identity
  (monad-laws-left-identity core/const-future core/bind (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-right-identity
  (monad-laws-right-identity core/const-future core/bind (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-associativity
  (monad-laws-right-identity core/const-future core/bind (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-left-identity-flatmap
  (monad-laws-left-identity core/const-future core/flatmap (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-right-identity-flatmap
  (monad-laws-right-identity core/const-future core/flatmap (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-associativity-flatmap
  (monad-laws-right-identity core/const-future core/flatmap (gen/not-empty gen/string-alpha-numeric)))


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


(deftest flatmapping
  (testing "success"
    (let [result (-> (core/const-future 10)
                     (core/bind (fn [n] (core/const-future (* n n))))
                     deref)]
      (is (instance? imminent.core.Success result))
      (is (= (core/raw-value result) 100))))

  (testing "failed future"
    (let [result (-> (core/failed-future (ex-info "error" {}))
                     (core/bind (fn [n] (core/const-future (* n n))))
                     deref)]
      (is (instance? imminent.core.Failure result))
      (is (instance? clojure.lang.ExceptionInfo (core/raw-value result))))))


(deftest exception-handling
  (testing "core functions don't blow up"
    (let [bad-fn (fn [_] (throw (ex-info "bad, bad fn!" {})))
          future (core/const-future 10)]
      (are [x y ] (instance? x @y)
           imminent.core.Failure (core/map future bad-fn)
           imminent.core.Failure (core/filter future bad-fn)
           imminent.core.Failure (core/bind future bad-fn)
           imminent.core.Failure (core/sequence [(core/failed-future (ex-info "error" {}))])))))


(deftest sequencing
  (testing "success"
    (let [result (-> [(core/const-future 10) (core/const-future 20) (core/const-future 30)]
                     (core/sequence)
                     deref)]

      (is (instance? imminent.core.Success result))
      (is (= (core/raw-value result) [10 20 30]))))

  (testing "failure"
    (testing "failed future"
      (let [result (-> [(core/const-future 10) (core/failed-future (ex-info "error" {}))]
                       (core/sequence)
                       deref)]
        (is (instance? imminent.core.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (core/raw-value result)))))))

(deftest completion-handlers
  (testing "success"
    (let [result (atom nil)]
      (-> (core/const-future 10) (core/on-success #(reset! result %)))
      (is (= @result 10))))

  (testing "failure"
    (let [result (atom nil)]
      (-> (core/failed-future (ex-info "error" {})) (core/on-failure #(reset! result %)))
      (is (instance? clojure.lang.ExceptionInfo @result))))

  (testing "completion"
    (testing "success"
      (let [result (atom nil)]
        (-> (core/const-future "success") (core/on-complete #(reset! result %)))
        (is (instance? imminent.core.Success @result))
        (is (= (core/raw-value @result) "success"))))

    (testing "failure"
      (let [result (atom nil)]
        (-> (core/failed-future (ex-info "error" {})) (core/on-complete #(reset! result %)))
        (is (instance? imminent.core.Failure @result))
        (is (instance? clojure.lang.ExceptionInfo (core/raw-value @result)))))))
