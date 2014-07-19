(ns imminent.protocols
  (:refer-clojure :exclude [map filter]))

(defprotocol Functor
  (map [this f]))

(defprotocol IReturn
  (success?  [this])
  (failure?  [this])
  (raw-value [this]))

(defprotocol IFuture
  (on-success   [this f])
  (on-failure   [this f])
  (on-complete  [this f])
  (filter       [this f?]))

(defprotocol IPromise
  (complete [this value])
  (->future [this]))
