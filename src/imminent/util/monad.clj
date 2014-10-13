(ns imminent.util.monad
  (:require [uncomplicate.fluokitten.utils :refer [with-context]]
            [uncomplicate.fluokitten.core :as fkc :refer [bind pure mdo return fmap]]))

;;
;; Derived functions
;;

(defn mlift2
  "Lifts a binary function `f` into a monadic context"
  [f]
  (fn [ma mb]
    (mdo [a ma
          b mb]
         (pure ma (f a b)))))

(defn msequence
  "`ctx` is the monadic context.
  Given a monad `m` and a list of monads `ms`, it returns a single monad containing a list of
  the values yielded by all monads in `ms`"
  [ctx ms]
  (reduce (mlift2 conj)
          (pure ctx [])
          ms))

(defn mlift
  "`ctx` is the monadic context.
  Lifts a n-ary function `f` into a monadic context"
  [ctx f]
  (fn [& ms]
    (fmap (msequence ctx ms)
          #(apply f %))))

(defn mmap
  "`ctx` is the monadic context.
  Given a monad `m`, a function `f` and a list of values `vs`, it maps `f` over `vs` finally sequencing all resulting monads. See `msequence`"
  [ctx f vs]
  (msequence ctx (map f vs)))

(defn mfilter
  "`ctx` is the monadic context.
  `pred?` is a function that receives a `v` from `vs` and returns a monad that yields a boolean
  `vs` is a list of values

  It filters `vs` and returns a monad that yields a list of the values matching `pred?`. Generalises standard `filter` to monads."
  [ctx pred? vs]
  (let [reducing-fn (fn [acc v]
                      (mdo [satisfies? (pred? v)
                            rs         acc]
                           (if satisfies?
                             (pure  ctx (conj rs v))
                             (pure  ctx rs))))]
    (reduce reducing-fn (pure ctx []) vs)))

(defn mjoin
  "Takes a nested monad `mm` and removes one monadic level by carrying out the outer context, returning its inner monad"
  [mm]
  (bind mm identity))
