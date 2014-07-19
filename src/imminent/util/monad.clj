(ns imminent.util.monad)

(defprotocol Bind
  (bind    [ma fmb])
  (flatmap [ma fmb]))

(defn lift2-m [m f]
  (fn [ma mb]
    ((:bind m) ma
     (fn [a]
       ((:bind m) mb
        (fn [b]
          ((:point m) (f a b))))))))

(defn sequence-m [m ms]
  (reduce (lift2-m m conj)
          ((:point m) [])
          ms))
