(ns imminent.core-test
  (:require [clojure.test :refer :all]
            [imminent.future    :as f]
            [imminent.protocols :as p]
            [imminent.result    :as r]
            [uncomplicate.fluokitten.protocols :as fkp]
            [imminent.executors :as executors]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer (defspec)]))

(set! *warn-on-reflection* true)

(def success-gen (gen/fmap r/success (gen/not-empty gen/string-alpha-numeric)))
(def failure-gen (gen/fmap r/failure (gen/not-empty gen/string-alpha-numeric)))
(def future-gen  (gen/fmap (partial fkp/pure (imminent.future.Future. nil nil)) (gen/not-empty gen/string-alpha-numeric)))

(defn setup [f]
  (binding [executors/*executor* executors/blocking-executor]
    (f)))

(use-fixtures :each setup)

(defn functor-laws-identity [generator]
  (prop/for-all [functor generator]
                (= (fkp/fmap functor identity)
                   (identity functor))))

(defn functor-laws-associativity [generator]
  (prop/for-all [functor generator]
                (= (fkp/fmap functor (comp count str))
                   (fkp/fmap (fkp/fmap functor str)
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
  (monad-laws-left-identity f/const-future fkp/bind (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-right-identity
  (monad-laws-right-identity f/const-future fkp/bind (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-associativity
  (monad-laws-right-identity f/const-future fkp/bind (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-left-identity-flatmap
  (monad-laws-left-identity f/const-future f/flatmap (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-right-identity-flatmap
  (monad-laws-right-identity f/const-future f/flatmap (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-associativity-flatmap
  (monad-laws-right-identity f/const-future f/flatmap (gen/not-empty gen/string-alpha-numeric)))

(def failed-future (f/failed-future (ex-info "error" {})))


(deftest mapping
  (testing "success"
    (let [result (-> (f/const-future 10)
                     (fkp/fmap #(* % %))
                     deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) 100)))
    )

  (testing "failure"
    (testing "failed future"
      (let [result (-> failed-future
                       (fkp/fmap #(* % %))
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))
    ))

(deftest filtering
  (testing "success"
    (let [result (-> (f/const-future 10)
                     (p/filter even?)
                     deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) 10)))
    )

  (testing "failure"
    (testing "failed predicate"
      (let [result (-> (f/const-future 10)
                       (p/filter odd?)
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? java.util.NoSuchElementException (deref result)))))

    (testing "failed future"
      (let [result (-> failed-future
                       (p/filter odd?)
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))

    ))


(deftest flatmapping
  (testing "success"
    (let [result (-> (f/const-future 10)
                     (fkp/bind (fn [n] (f/const-future (* n n))))
                     deref)]
      (is (instance? imminent.result.Success result))
      (is (= (deref result) 100))))

  (testing "failed future"
    (let [result (-> failed-future
                     (fkp/bind (fn [n] (f/const-future (* n n))))
                     deref)]
      (is (instance? imminent.result.Failure result))
      (is (instance? clojure.lang.ExceptionInfo (deref result))))))

(defn bad-fn [_] (throw (ex-info "bad, bad fn!" {})))

(deftest exception-handling
  (testing "core functions don't blow up"
    (let [future (f/const-future 10)]
      (are [x y ] (instance? x @y)
           imminent.result.Failure (fkp/fmap future bad-fn)
           imminent.result.Failure (p/filter future bad-fn)
           imminent.result.Failure (fkp/bind future bad-fn)
           imminent.result.Failure (f/sequence [failed-future])))))


(deftest sequencing
  (testing "success"
    (let [result (-> [(f/const-future 10) (f/const-future 20) (f/const-future 30)]
                     (f/sequence)
                     deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) [10 20 30]))))

  (testing "failure"
    (testing "failed future"
      (let [result (-> [failed-future (f/const-future 10) failed-future]
                       (f/sequence)
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))))

(deftest reducing
  (testing "success"
    (let [result (->> [(f/const-future 10) (f/const-future 20) (f/const-future 30)]
                      (f/reduce + 0)
                      deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) 60))))

  (testing "failure"
    (testing "failed future"
      (let [result (->> [(f/const-future 10) failed-future]
                        (f/reduce + 0)
                        deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))))

(deftest mapping-futures
  (testing "success"
    (let [f      #(f/future (* % %))
          result (-> (f/map-future f [1 2 3])
                     deref)]

      (is (instance? imminent.result.Success result))
      (is (= (deref result) [1 4 9]))))

  (testing "failure"
    (testing "failed future"
      (let [f      #(f/future (bad-fn %))
            result (-> (f/map-future f [1 2 3])
                       deref)]
        (is (instance? imminent.result.Failure result))
        (is (instance? clojure.lang.ExceptionInfo (deref result)))))))

(deftest filtering-futures
  (testing "success"
    (let [pred?      (comp f/const-future even?)
          result (-> (f/filter-future pred? [10 2 3 4 7])
                     deref)]

      (is (= @result [10 2 4])))))

(deftest joining-futures
  (testing "success"
    (let [ff     (f/const-future (f/const-future 42))
          f      (fkp/join ff)
          result (-> f deref deref)]
      (is (instance? imminent.future.Future f))
      (is (= result 42)))))

(deftest completion-handlers
  (testing "success"
    (let [result (atom nil)]
      (-> (f/const-future 10) (p/on-success #(reset! result %)))
      (is (= @result 10))))

  (testing "failure"
    (let [result (atom nil)]
      (-> failed-future (p/on-failure #(reset! result %)))
      (is (instance? clojure.lang.ExceptionInfo @result))))

  (testing "completion"
    (testing "success"
      (let [result (atom nil)]
        (-> (f/const-future "success") (p/on-complete #(reset! result %)))
        (is (instance? imminent.result.Success @result))
        (is (= (deref @result) "success"))))

    (testing "failure"
      (let [result (atom nil)]
        (-> failed-future (p/on-complete #(reset! result %)))
        (is (instance? imminent.result.Failure @result))
        (is (instance? clojure.lang.ExceptionInfo (deref @result)))))))
