(ns imminent.core
  (:refer-clojure :exclude [map filter future future-call promise sequence reduce await])
  (:require imminent.protocols
            imminent.future
            imminent.result
            [imminent.util.monad :as m]
            [imminent.executors  :as executors]
            [imminent.util.namespaces :refer [import-vars]]
            [uncomplicate.fluokitten.protocols :as fkp]
            [uncomplicate.fluokitten.core :as fkc])
  (:import clojure.lang.IDeref
           [java.util.concurrent TimeUnit CountDownLatch TimeoutException]))

(import-vars
 [imminent.protocols
  IReturn
  success? failure? map-failure
  IFuture
  on-success on-failure on-complete filter
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
  flatmap
  map-future
  try-future
  failed-future
  filter-future
  sequence
  const-future]

 [uncomplicate.fluokitten.protocols
  Functor
  fmap

  Applicative
  pure fapply

  Monad
  bind join
  ])
