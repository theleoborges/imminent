(ns imminent.protocols
  (:refer-clojure :exclude [map filter future promise sequence]))

(defprotocol Functor
  (map [this f]))

(defprotocol Bind
  (bind [ma fmb]))

(defprotocol IReturn
  (success?  [this])
  (failure?  [this])
  (raw-value [this]))

(defprotocol IFuture
  (on-success   [this f])
  (on-failure   [this f])
  (on-complete  [this f])
  (filter       [this f?])
  (flatmap      [this f]))

(defprotocol IPromise
  (complete [this value])
  (->future [this]))
