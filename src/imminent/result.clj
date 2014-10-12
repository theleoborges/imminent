(ns imminent.result
  (:require [imminent.protocols :refer [IReturn]]
            [uncomplicate.fluokitten.protocols :as fkp])
  (:import clojure.lang.IDeref))

(defrecord Success [v]
  IDeref
  (deref [_] v)
  IReturn
  (success?    [this] true)
  (failure?    [this] false)
  (map-failure [this _]
    this)

  fkp/Functor
  (fmap [fv g]
    (Success. (g v)))
  (fmap [fv g fvs]
    (Success. (apply g v (map deref fvs)))))

(defrecord Failure [e]
  IDeref
  (deref [_] e)
  IReturn
  (success?    [this] false)
  (failure?    [this] true)
  (map-failure [this f]
    (Failure. (f e)))
  fkp/Functor
  (fmap [fv _]
    fv)
  (fmap [fv g _]
    fv))

(defn success [v]
  (Success. v))

(defn failure [v]
  (Failure. v))
