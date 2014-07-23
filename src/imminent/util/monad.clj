(ns imminent.util.monad
    (:refer-clojure :exclude [map]))

(defprotocol Functor
  (map [this f]))

(defprotocol Bind
  (bind    [ma fmb])
  (flatmap [ma fmb]))

;;
;; Derived functions
;;

(defn lift2-m [m f]
  (let [point (:point m)
        bind  (:bind m)]
    (fn [ma mb]
      (bind ma
       (fn [a]
         (bind mb
          (fn [b]
            (point (f a b)))))))))

(defn sequence-m [m ms]
  (reduce (lift2-m m conj)
          ((:point m) [])
          ms))

(defn map-m [m f ms]
  (-> (sequence-m m ms)
      (map f)))
