(ns imminent.result
  (:require [imminent.protocols :refer [IReturn]]
            [uncomplicate.fluokitten.protocols :as fkp]
            clojure.core.match)
  (:import clojure.lang.IDeref))

(deftype Success [v]
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
    (Success. (apply g v (map deref fvs))))

  Object
  (equals   [this other] (and (instance? Success other)
                              (= v @other)))
  (hashCode [this] (hash v))
  (toString [this] (pr-str v)))

(deftype Failure [e]
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
    fv)

  Object
  (equals   [this other] (and (instance? Failure other)
                              (= e @other)))
  (hashCode [this] (hash e))
  (toString [this] (pr-str e)))

(defn success [v]
  (Success. v))

(defn failure [v]
  (Failure. v))


;;
;; Limited core.match support
;;

(extend-type Success
  clojure.core.match.protocols/IMatchLookup
  (val-at [this k not-found]
    (if (= imminent.result.Success k)
      @this
      not-found)))


(extend-type Failure
  clojure.core.match.protocols/IMatchLookup
  (val-at [this k not-found]
    (if (= imminent.result.Failure k)
      @this
      not-found)))
