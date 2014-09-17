(ns imminent.core
  (:refer-clojure :exclude [map filter future future-call promise sequence reduce await])
  (:require [clojure.core :as clj]
            imminent.protocols
            [imminent.util.monad :as m]
            [imminent.executors  :as executors]
            [imminent.util.namespaces :refer [import-vars]]
            [uncomplicate.fluokitten.protocols :as fkp])
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

 [uncomplicate.fluokitten.protocols
  Functor
  fmap

  Applicative
  pure fapply

  Monad
  bind join
  ])

(defrecord Success [v]
  IDeref
  (deref [_] v)
  IReturn
  (success?    [this] true)
  (failure?    [this] false)
  (map-failure [this _]
    this)

  Functor
  (fmap [fv g]
    (Success. (g v)))
  (fmap [fv g fvs]
    (Success. (apply g v (clj/map deref fvs)))))

(defrecord Failure [e]
  IDeref
  (deref [_] e)
  IReturn
  (success?    [this] false)
  (failure?    [this] true)
  (map-failure [this f]
    (Failure. (f e)))
  Functor
  (fmap [fv _]
    fv)
  (fmap [fv g _]
    fv))

(prefer-method print-method clojure.lang.IRecord clojure.lang.IDeref)

(defn success [v]
  (Success. v))

(defn failure [v]
  (Failure. v))

(declare promise)
(declare from-try)
(declare failed-future)

(defmacro try-future
  "Wraps body in a try/catch. If an exception is thrown, returns a Future which yields a Failure containg the exception."
  [& body]
  `(try
     ~@body
     (catch Exception t#
       (failed-future t#))))

(deftype Future [state listeners]
  IDeref
  (deref [_]
    @state)

  IFuture
  (on-success   [this f]
    (on-complete this (comp deref #(fmap % f))))

  (on-failure     [this f]
    (on-complete this (comp deref #(map-failure % f))))

  (on-complete  [this f]
    (let [st @state]
      (if (= st ::unresolved)
        (swap! listeners conj f)
        (executors/dispatch f st))))

  (filter [this pred?]
    (fmap this (fn [a]
                (if (pred? a)
                  a
                  (throw (java.util.NoSuchElementException. "Failed predicate"))))))

  IAwaitable
  (await [this]
    (let [latch (CountDownLatch. 1)]
      (on-complete this (fn [_] (.countDown latch)))
      (.await latch)
      this))

  (await [this ms]
    (let [latch (CountDownLatch. 1)]
      (on-complete this (fn [_] (.countDown latch)))
      (if-not (.await latch ms TimeUnit/MILLISECONDS)
        (failed-future (TimeoutException. "Timeout waiting future"))
        this)))

  Functor
  (fmap [fv g]
    (bind fv (fn [a]
                 (from-try #(g a)))))
  (fmap [fv g fvs]
    (throw (java.lang.UnsupportedOperationException. "vararg fmap in Future")))

  Applicative
  (pure [_ v]
    (let [p (promise)]
      (complete p (Success. v))
      (->future p)))

  (fapply [ag av]
    ((m/lift2-m {} #(% %1))
     ag av))

  (fapply [ag av avs]
    (throw (java.lang.UnsupportedOperationException. "vararg fmap in Future")))

  Object
  (equals   [this other] (and (instance? Future other)
                              (= @this @other)))
  (hashCode [this] (hash @this))
  (toString [this] (pr-str @this)))



(defn const-future
  "Creates a new future and immediately completes it successfully with `v`"
  [v]
  (let [p (promise)]
    (complete p (Success. v))
    (->future p)))

(def future-monad-impls
  {:bind (fn [mv g]
           (let [p (promise)]
             (on-complete mv (fn [a]
                               (if (success? a)
                                 (on-complete (try-future (g (deref a)))
                                              (fn [b]
                                                (complete p b)))
                                 (complete p a))))
             (->future p)))})

(def future-monad
  {:point const-future
   :bind  (:bind future-monad-impls)})

(def monad-default-impls
  {:join (partial m/join-m future-monad)})

(extend imminent.core.Future
  Monad
  (merge monad-default-impls
         future-monad-impls))

(def flatmap bind)

(deftype Promise [state listeners future]
  IDeref
  (deref [_]
    @state)

  IPromise
  (complete [this value]
    (if (= @state ::unresolved)
      (do
        (reset! state value)
        (executors/dispatch-all @listeners value))
      (throw (Exception. "Attempted to complete already completed promise"))))

  (->future [this]
    future)

  Object
  (equals   [this other] (and (instance? Promise other)
                              (= @this @other)))
  (hashCode [this] (hash @this))
  (toString [this] (pr-str @this)))

(defn promise
  "Creates a new, unresolved promise."
  []
  (let [state     (atom ::unresolved)
        listeners (atom [])
        future (Future. state listeners)]
    (Promise. state listeners future)))

(defn try*
  "Wraps `f` in a try/catch. Returns the result of `f` in a `Success` type if successful. Returns a `Failure` containing the exception otherwise."
  [f]
  (try
    (Success. (f))
    (catch Exception t
      (Failure. t))))

(defn future-call
  "Dispatches `task` on a separate thread and returns a future that will eventually contain the result of `task`"
  [task]
  (let [p (promise)]
    (executors/dispatch (fn [] (complete p (try* task))))
    (->future p)))

(defmacro future
  "Dispatches `body` on a separate thread and returns a future that will eventually contain the result. See `future-call`"
  [& body]
  `(future-call (fn [] ~@body)))

(defn from-try
  "Creates a future from a function `f`. See `try*`"
  [f]
  (let [p (promise)]
    (complete p (try* f))
    (->future p)))

(defn failed-future [e]
  "Creates a new future and immediately completes it with the Failure `e`"
  (let [p (promise)]
    (complete p (Failure. e))
    (->future p)))

;;
;; Future monad instance and convenience derived functions
;;

(def sequence
  "Given a list of futures, returns a future that will eventually contain a list of the results yielded by all futures. If any future fails, returns a Future representing that failure"
  (partial m/sequence-m future-monad))

(defn reduce
  "Returns a Future containing a list of the results yielded by all futures in `ms` further reduced using `f` and `seed`. See `sequence` and `map`"
  [f seed ms]
  (-> (sequence ms)
      (fmap #(clj/reduce f seed %))))

(defn map-future
  "`f` needs to return a future. Maps `f` over `vs` and sequences all resulting futures. See `sequence`"
  [f vs]
  (m/map-m future-monad f vs))

(defn filter-future
  "`pred?` needs to return a Future that yields a boolean. Returns a Future which yields a future containing all Futures which match `pred?`"
  [pred? vs]
  (m/filter-m future-monad pred? vs))
