(ns imminent.core-test
  (:require [clojure.test :refer :all]
            [imminent.core :as f]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer (defspec)]))


(def success-gen (gen/fmap f/success (gen/not-empty gen/string-alpha-numeric)))
(def failure-gen (gen/fmap f/failure (gen/not-empty gen/string-alpha-numeric)))
(def future-gen  (gen/fmap f/const-future (gen/not-empty gen/string-alpha-numeric)))

(defn functor-laws-identity [generator]
  (prop/for-all [functor generator]
                (= (f/map functor identity)
                   (identity functor))))

(defn functor-laws-associativity [generator]
  (prop/for-all [functor generator]
                (= (f/map functor (comp count str))
                   (f/map (f/map functor str)
                          count))))

(defn monad-laws-left-identity [pure generator]
  (prop/for-all [a   generator]
                (let [fmb #(pure (str %))]
                  (= (f/bind (pure a) fmb)
                     (fmb a)))))

(defn monad-laws-right-identity [pure generator]
  (prop/for-all [a   generator]
                (let [ma (pure a)]
                  (= (f/bind ma pure)
                     ma))))

(defn monad-laws-associativity [pure generator]
  (prop/for-all [a   generator]
                (let [ma (pure a)
                      f  str
                      g  count]
                  (= (f/bind (f/bind ma f) g)
                     (f/bind ma (fn [x]
                                  (f/bind (f x) g)))))))

(defspec result-functor-laws-identity
  (functor-laws-identity (gen/one-of [success-gen failure-gen])))


(defspec result-functor-laws-associativity
  (functor-laws-associativity (gen/one-of [success-gen failure-gen])))

(defspec future-functor-laws-identity
  (functor-laws-identity future-gen))

(defspec future-functor-laws-associativity
  (functor-laws-associativity future-gen))

(defspec future-monad-laws-left-identity
  (monad-laws-left-identity f/const-future (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-right-identity
  (monad-laws-right-identity f/const-future (gen/not-empty gen/string-alpha-numeric)))

(defspec future-monad-laws-associativity
  (monad-laws-right-identity f/const-future (gen/not-empty gen/string-alpha-numeric)))
