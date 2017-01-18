(ns imminent.result
  (:require [imminent.protocols :refer [IReturn]]
            [imminent.util.functor :refer [BiFunctor]]
            [imminent.util.monad   :as m]
            [uncomplicate.fluokitten.protocols :as fkp]
            [uncomplicate.fluokitten.core :as fkc]
            [uncomplicate.fluokitten.algo :as fka]
            [clojure.core.match :refer [match]])
  (:import clojure.lang.IDeref))

(declare success)
(declare failure)

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

  BiFunctor
  (bimap [fv f g]
    (try
      (success (f v))
      (catch Exception e
        (failure e))))

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

  BiFunctor
  (bimap [fv f g]
    (try
      (failure (g e))
      (catch Exception ex
        (failure ex))))

  fkp/Monad
  (bind [mv g] mv)
  (bind [mv g mvs] mv)
  (join [mm]
    (m/mjoin mm))

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

;;
;; Applicative support
;;

;; Shared implementation of Applicative for Success and Failure.
(defn sf-fapply
  ([af av]
   (match [af av]
          [{Success f} {Success v}] (success (f v))
          [_           {Failure _}] av
          [{Failure _} _          ] af))
  ([af av avs]
   (match [af av avs]
          [{Success f} {Success v} {Success vs}] (success (apply f v vs))
          [          _           _ {Failure _ }] avs
          [          _ {Failure _}            _] av
          [{Failure _}           _            _] af)))

(extend-type Success
  fkp/Applicative
  (fapply
    ([af av]     (sf-fapply af av))
    ([af av avs] (sf-fapply af av avs)))
  (pure
    ([_ v]
     (success v))
    ([_ v _]
     (success v))))

(extend-type Failure
  fkp/Applicative
  (fapply
    ([af av]     (sf-fapply af av))
    ([af av avs] (sf-fapply af av avs)))
  (pure
    ([_ e]
     (failure e))
    ([_ e _]
     (failure e))))

;;
;; Monad support
;;

(extend-type Success
  fkp/Monad
  (bind
    ([mv g]     (fka/default-bind mv g))
    ([mv g mvs] (fka/default-bind mv g mvs)))
  (join [mv] @mv))
