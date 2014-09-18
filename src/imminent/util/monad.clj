(ns imminent.util.monad
  (:refer-clojure :exclude [map])
  (require [clojure.core :as clj]
           [uncomplicate.fluokitten.utils :refer [with-context]]
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
  "Given a monad `m` and a list of monads `ms`, it returns a single monad containing a list of
  the values yielded by all monads in `ms`"
  [ms]
  (reduce (mlift2 conj)
          (pure (first ms) [])
          ms))

(defn mlift
  "Lifts a n-ary function `f` into a monadic context"
  [f]
  (fn [& ms]
    (fmap (msequence ms)
          #(apply f %))))

(defn mmap [f vs]
  "Given a monad `m`, a function `f` and a list of values `vs`, it maps `f` over `vs` finally sequencing all resulting monads. See `msequence`"
  (->> (clj/map f vs)
       msequence))

(defn mfilter [pred? vs]
  "`m` is a monad
  `pred?` is a function that receives a `v` from `vs` and returns a monad that yields a boolean
  `vs` is a list of values

  It filters `vs` and returns a monad that yields a list of the values matching `pred?`. Generalises standard `filter` to monads."
  (let [ctx (first vs)
        reducing-fn (fn [acc v]
                      (mdo [satisfies? (pred? v)
                            rs         acc]
                           (if satisfies?
                             (pure  ctx (conj rs v))
                             (pure  ctx rs))))]
    (reduce reducing-fn (pure ctx []) vs)))

(defn mjoin [mm]
  (bind mm identity))
