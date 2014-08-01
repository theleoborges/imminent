(ns imminent.core
  (:refer-clojure :exclude [map filter future promise sequence reduce await])
  (:require [clojure.core :as clj]
            imminent.protocols
            [imminent.util.monad :as m]
            [imminent.executors  :as executors]
            [imminent.util.namespaces :refer [import-vars]])
  (:import clojure.lang.IDeref
           [java.util.concurrent TimeUnit CountDownLatch TimeoutException]))

(set! *warn-on-reflection* true)

(import-vars
 [imminent.protocols
  IReturn
  success? failure? raw-value map-failure
  IFuture
  on-success on-failure on-complete filter
  IPromise
  complete ->future
  IAwaitable
  await]

 [imminent.util.monad
  Functor
  map
  Bind
  bind flatmap])

(defrecord Success [v]
  IReturn
  (success?    [this] true)
  (failure?    [this] false)
  (raw-value   [this] v)
  (map-failure [this _]
    this)
  Functor
  (map       [this f]
    (Success. (f v))))

(defrecord Failure [e]
  IReturn
  (success?    [this] false)
  (failure?    [this] true)
  (raw-value   [this] e)
  (map-failure [this f]
    (Failure. (f e)))
  Functor
  (map       [this _]
    this))

(defn success [v]
  (Success. v))

(defn failure [v]
  (Failure. v))

(defn dispatch [f value]
  (let [f (#'clojure.core/binding-conveyor-fn f)]
    (.execute ^java.util.concurrent.Executor executors/*executor*
              #(f value))))

(defn dispatch-all [listeners value]
  (doseq [f listeners]
    (dispatch f value)))

(declare promise)
(declare from-try)
(declare failed-future)
(defmacro try-future [& body]
  `(try
     ~@body
     (catch Throwable t#
       (failed-future t#))))

(deftype Future [state listeners]
  IDeref
  (deref [_]
    @state)

  IFuture
  (on-success   [this f]
    (on-complete this (comp raw-value #(map % f))))

  (on-failure     [this f]
    (on-complete this (comp raw-value #(map-failure % f))))

  (on-complete  [this f]
    (let [st @state]
      (if (= st ::unresolved)
        (swap! listeners conj f)
        (dispatch f st))))

  (filter [this pred?]
    (map this (fn [a]
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
  (map [this f]
    (bind this (fn [a]
                 (from-try #(f a)))))

  Bind
  (bind [ma fmb]
    (let [p (promise)]
      (on-complete ma (fn [a]
                        (if (success? a)
                          (on-complete (try-future (fmb (raw-value a)))
                                       (fn [b]
                                         (complete p b)))
                          (complete p a))))
      (->future p)))

  (flatmap [ma fmb] (bind ma fmb))

  Object
  (equals   [this other] (= @this @other))
  (hashCode [this] (hash @this))
  (toString [this] (pr-str @this)))


(deftype Promise [state listeners future]
  IDeref
  (deref [_]
    @state)

  IPromise
  (complete [this value]
    (if (= @state ::unresolved)
      (do
        (reset! state value)
        (dispatch-all @listeners value))
      (throw (Exception. "Attempted to complete already completed promise"))))

  (->future [this]
    future)

  Object
  (equals   [this other] (= @this @other))
  (hashCode [this] (hash @this))
  (toString [this] (pr-str @this)))

(defn promise []
  (let [state     (atom ::unresolved)
        listeners (atom [])
        future (Future. state listeners)]
    (Promise. state listeners future)))

(defn try* [f]
  (try
    (Success. (f))
    (catch Throwable t
      (Failure. t))))

(defn future [task]
  (let [p (promise)
        task (#'clojure.core/binding-conveyor-fn task)]
    (.execute ^java.util.concurrent.Executor executors/*executor*
              (fn []
                (complete p (try* task))))
    (->future p)))

(defn from-try [f]
  (let [p (promise)]
    (complete p (try* f))
    (->future p)))

(defn const-future [v]
  (let [p (promise)]
    (complete p (Success. v))
    (->future p)))

(defn failed-future [e]
  (let [p (promise)]
    (complete p (Failure. e))
    (->future p)))

;;
;; Future monad instance and convenience derived functions
;;

(def future-monad
  {:point const-future
   :bind  bind})

(def  sequence (partial m/sequence-m future-monad))

(defn reduce [f seed ms]
  (-> (sequence ms)
      (map #(clj/reduce f seed %))))

(defn map-future [f ms]
  (m/map-m future-monad f ms))
