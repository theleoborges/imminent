(ns imminent.laws-test
  (:require [clojure.test :refer :all]
            [imminent.core    :as core]
            [uncomplicate.fluokitten.protocols :as fkp]
            [imminent.executors :as executors]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer (defspec)]))

(set! *warn-on-reflection* true)

(def success-gen (gen/fmap core/success (gen/not-empty gen/string-alpha-numeric)))
(def failure-gen (gen/fmap core/failure (gen/not-empty gen/string-alpha-numeric)))
(def future-gen  (gen/fmap (partial core/pure (imminent.future.Future. nil nil)) (gen/not-empty gen/string-alpha-numeric)))

(defn setup [f]
  (binding [executors/*executor* executors/blocking-executor]
    (f)))

(use-fixtures :each setup)

(defn functor-laws-identity [generator]
  (prop/for-all [functor generator]
                (= (core/fmap functor identity)
                   (identity functor))))

(defn functor-laws-associativity [generator]
  (prop/for-all [functor generator]
                (= (core/fmap functor (comp count str))
                   (core/fmap (core/fmap functor str)
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
