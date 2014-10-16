(ns imminent.core
  "Convenience namespace. Require this instead of the individual namespaces to prevent
  large namespace declarations. Its use is entirely optional."
  (:refer-clojure :exclude [map filter future future-call promise sequence reduce await])
  (:require imminent.protocols
            imminent.future
            imminent.result
            [imminent.util.namespaces :refer [import-vars]]
            [uncomplicate.fluokitten.protocols :as fkp]
            uncomplicate.fluokitten.core))

(import-vars
 [imminent.protocols
  IReturn
  success? failure? map-failure
  IFuture
  on-success on-failure on-complete filter completed?
  IPromise
  complete ->future
  IAwaitable
  await]

 [imminent.result
  success
  failure]

 [imminent.future
  reduce
  try*
  future-call
  from-try
  future
  promise
  map-future
  try-future
  failed-future
  filter-future
  sequence
  const-future
  m-ctx]

 [uncomplicate.fluokitten.protocols
  Functor
  fmap

  Applicative
  pure fapply

  Monad
  bind join]

 [uncomplicate.fluokitten.core
  mdo])

(def flatmap fkp/bind)
(def map     fkp/fmap)
