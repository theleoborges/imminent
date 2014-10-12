(ns imminent.future
  (:refer-clojure :exclude [map filter future future-call promise sequence reduce await])
  (:require [clojure.core :as clj]
            [imminent.protocols :refer :all]
            [imminent.util.monad :as m]
            [imminent.executors  :as executors]
            [imminent.result  :refer [success failure]]
            [uncomplicate.fluokitten.protocols :as fkp]
            [uncomplicate.fluokitten.core :as fkc])
  (:import clojure.lang.IDeref
           [java.util.concurrent TimeUnit CountDownLatch TimeoutException]))


;;
;; Convenience aliases of monadic combinators
;;

(def sequence
  "Given a list of futures, returns a future that will eventually contain a list of the results yielded by all futures. If any future fails, returns a Future representing that failure"
  m/msequence)

(defn reduce
  "Returns a Future containing a list of the results yielded by all futures in `ms` further reduced using `f` and `seed`. See `sequence` and `map`"
  [f seed ms]
  (-> (sequence ms)
      (fkp/fmap #(clj/reduce f seed %))))

(def map-future
  "`f` needs to return a future. Maps `f` over `vs` and sequences all resulting futures. See `sequence`"
  m/mmap)

(def filter-future
  "`pred?` needs to return a Future that yields a boolean. Returns a Future which yields a future containing all Futures which match `pred?`"
  m/mfilter)

;;
;; Future utility functions
;;
(declare promise)
(defn try*
  "Wraps `f` in a try/catch. Returns the result of `f` in a `Success` type if successful. Returns a `Failure` containing the exception otherwise."
  [f]
  (try
    (success (f))
    (catch Exception t
      (failure t))))

(defn from-try
  "Creates a future from a function `f`. See `try*`"
  [f]
  (let [p (promise)]
    (complete p (try* f))
    (->future p)))

(defmacro try-future
  "Wraps body in a try/catch. If an exception is thrown, returns a Future which yields a Failure containg the exception."
  [& body]
  `(try
     ~@body
     (catch Exception t#
       (failed-future t#))))

(defn failed-future [e]
  "Creates a new future and immediately completes it with the Failure `e`"
  (let [p (promise)]
    (complete p (failure e))
    (->future p)))

(defn const-future
  "Creates a new future and immediately completes it successfully with `v`"
  [v]
  (let [p (promise)]
    (complete p (success v))
    (->future p)))

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


(deftype Future [state listeners]
  IDeref
  (deref [_]
    @state)

  IFuture
  (on-success   [this f]
    (on-complete this (comp deref #(fkp/fmap % f))))

  (on-failure     [this f]
    (on-complete this (comp deref #(map-failure % f))))

  (on-complete  [this f]
    (let [st @state]
      (if (= st ::unresolved)
        (swap! listeners conj f)
        (executors/dispatch f st))))

  (filter [this pred?]
    (fkp/fmap this (fn [a]
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

  fkp/Functor
  (fmap [fv g]
    (fkp/bind fv (fn [a]
                 (from-try #(g a)))))
  (fmap [fv g fvs]
    (throw (java.lang.UnsupportedOperationException. "vararg fmap in Future")))

  fkp/Applicative
  (pure [_ v]
    (let [p (promise)]
      (complete p (success v))
      (->future p)))

  (fapply [ag av]
    ((m/mlift2 #(% %2))
     ag av))

  (fapply [ag av avs]
    ((m/mlift2 #(apply % %2))
     ag (sequence (cons av avs))))

  fkp/Monad
  (bind[mv g]
    (let [p (promise)]
      (on-complete mv (fn [a]
                        (if (success? a)
                          (on-complete (try-future (g (deref a)))
                                       (fn [b]
                                         (complete p b)))
                          (complete p a))))
      (->future p)))
  (bind [mv g mvs]
    (fkc/mdo [v  mv
              vs (sequence mvs)]
             (fkc/return (apply g v vs))))

  (join [mm]
    (m/mjoin mm))

  Object
  (equals   [this other] (and (instance? Future other)
                              (= @this @other)))
  (hashCode [this] (hash @this))
  (toString [this] (pr-str @this)))


(def flatmap fkp/bind)
(def map     fkp/fmap)

(defn promise
  "Creates a new, unresolved promise."
  []
  (let [state     (atom ::unresolved)
        listeners (atom [])
        future (Future. state listeners)]
    (Promise. state listeners future)))
