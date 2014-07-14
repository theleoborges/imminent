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
  (prop/for-all [result generator]
                (= (f/map result (comp #(* 2 %) count))
                   (f/map (f/map result count)
                          #(* 2 %)))))

(defn functor-laws-associativity [generator]
  (prop/for-all [result generator]
                (= (f/map result (comp #(* 2 %) count))
                   (f/map (f/map result count)
                          #(* 2 %)))))

(defspec result-functor-laws-identity
  100
  (functor-laws-identity (gen/one-of [success-gen failure-gen])))


(defspec result-functor-laws-associativity
  100
  (functor-laws-associativity (gen/one-of [success-gen failure-gen])))

(defspec future-functor-laws-identity
  100
  (functor-laws-identity future-gen))

(defspec future-functor-laws-associativity
  100
  (functor-laws-associativity future-gen))
