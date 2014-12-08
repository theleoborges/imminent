(ns imminent.util.applicative
  (:require [uncomplicate.fluokitten.utils :refer [with-context]]
            [uncomplicate.fluokitten.core :as fkc :refer [bind pure mdo return fmap <*>]]
            [uncomplicate.fluokitten.jvm :refer [curry]]))


(defn alift
  "Lifts a n-ary function `f` into a applicative context"
  [f]
  (fn [& as]
    {:pre  [(seq as)]}
    (let [curried (curry f (count as))]
      (apply <*>
             (fmap curried (first as))
             (rest as)))))
