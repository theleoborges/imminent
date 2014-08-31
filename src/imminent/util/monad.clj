(ns imminent.util.monad
  (:refer-clojure :exclude [map])
  (require [clojure.core :as clj]))

(defprotocol Functor
  (map [functor f]
    "Applies `f` to the value yielded by `functor`, returning the same functor type"))

(defprotocol Bind
  (bind    [ma fmb]
    "Applies `fmb` to the value yielded by the monad `ma`.
    `fmb` itself returns a monad. `bind` effectively flattens the structure and returns a single monad")
  (flatmap [ma fmb]
    "Alias to bind"))

;;
;; Derived functions
;;

(defn lift2-m
  "Lifts the function `f` into the monad `m`"
  [m f]
  (let [point (:point m)
        bind  (:bind m)]
    (fn [ma mb]
      (bind ma
       (fn [a]
         (bind mb
          (fn [b]
            (point (f a b)))))))))

(defn sequence-m
  "Given a monad `m` and a list of monads `ms`, it returns a single monad containing a list of
  the values yielded by all monads in `ms`"
  [m ms]
  (reduce (lift2-m m conj)
          ((:point m) [])
          ms))

(defn map-m [m f vs]
  "Given a monad `m`, a function `f` and a list of values `vs`, it maps `f` over `vs` finally sequencing all resulting monads. See `sequence-m`"
  (->> (clj/map f vs)
       (sequence-m m)))

(defn filter-m [m pred? vs]
  "`m` is a monad
  `pred?` is a function that receives a `v` from `vs` and returns a monad that yields a boolean
  `vs` is a list of values

  It filters `vs` and returns a monad that yields a list of the values matching `pred?`. Generalises standard `filter` to monads."
  (let [point (:point m)
        bind  (:bind m)
        reducing-fn (fn [acc v]
                      (bind (pred? v)
                            (fn [satisfies?]
                              (bind acc
                                    (fn [rs]
                                      (if satisfies?
                                        (point (conj rs v))
                                        (point rs)))))))]
    (reduce reducing-fn (point []) vs)))

(defn join-m [m mm]
  (let [bind  (:bind m)]
    (bind mm identity)))
