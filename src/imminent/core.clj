(ns imminent.core
  "Convenience namespace. Require this instead of the individual namespaces to prevent
  large namespace declarations. Its use is entirely optional."
  (:refer-clojure :exclude [map filter future future-call promise sequence reduce await])
  (:require imminent.protocols
            imminent.future
            imminent.result
            [imminent.util.namespaces :refer [import-vars]]
            [uncomplicate.fluokitten.protocols :as fkp]
            uncomplicate.fluokitten.core
            uncomplicate.fluokitten.jvm
            imminent.util.applicative))

(import-vars
 [imminent.protocols
  IReturn
  success? failure? map-failure
  IFuture
  on-success on-failure on-complete filter completed? zip
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
  blocking-future-call
  future
  blocking-future
  from-try
  promise
  amb
  map-future
  try-future
  failed-future
  filter-future
  sequence
  const-future
  m-ctx]

 [imminent.util.applicative
  alift]

 [uncomplicate.fluokitten.protocols
  Functor
  fmap

  Applicative
  pure fapply

  Monad
  bind join]

 [uncomplicate.fluokitten.core
  <*>
  mdo
  return]

 [uncomplicate.fluokitten.jvm
  curry])

(def flatmap fkp/bind)
(def map     fkp/fmap)

(def dderef
  "Same as (deref (deref x))"
  (comp deref deref))
