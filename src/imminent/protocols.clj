(ns imminent.protocols
  (:refer-clojure :exclude [map filter]))

(defprotocol IReturn
  (success?    [this])
  (failure?    [this])
  (raw-value   [this])
  (map-failure [this f]))

(defprotocol IFuture
  (on-success   [this f])
  (on-failure   [this f])
  (on-complete  [this f])
  (filter       [this f?]))

(defprotocol IPromise
  (complete [this value])
  (->future [this]))
