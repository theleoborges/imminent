(ns imminent.core
  (:refer-clojure :exclude [map filter future promise sequence])
  (:require imminent.protocols
            [imminent.executors :as executors]
            [imminent.util.namespaces :refer [import-vars]]))

(set! *warn-on-reflection* true)

(import-vars
 [imminent.protocols
  Functor   Bind
  map       bind
  IReturn
  success? failure? raw-value
  IFuture
  value on-success on-failure on-complete filter flatmap
  IPromise
  complete])

(def  repl-out *out*)
(defn prn-to-repl [& args]
  (binding [*out* repl-out]
    (apply prn args)))

(defrecord Success [v]
  IReturn
  (success?  [this] true)
  (failure?  [this] false)
  (raw-value [this] v)
  Functor
  (map       [this f]
    (Success. (f v))))

(defrecord Failure [e]
  IReturn
  (success?  [this] false)
  (failure?  [this] true)
  (raw-value [this] e)
  Functor
  (map       [this _]
    this))

(defn success [v]
  (Success. v))

(defn failure [v]
  (Failure. v))

(defn dispatch [f value]
  (.execute ^java.util.concurrent.Executor executors/*executor*
            #(f value)))

(defn dispatch-all [listeners value]
  (doseq [f listeners]
    (dispatch f value)))

(declare promise)
(declare const-future)
(declare from-try)

(deftype Promise [listeners state]
  IFuture
  (value [_] @state)
  (on-success   [this f]
    (on-complete this (fn [value]
                        (when (success? value)
                          (f (raw-value value))))))
  (on-failure     [this f]
    (on-complete this (fn [value]
                        (when (failure? value)
                          (f (raw-value value))))))
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

  IPromise
  (complete [this value]
    (if (= @state ::unresolved)
      (do
        (reset! state value)
        (dispatch-all @listeners value))
      (throw (Exception. "Attempted to complete already completed promise"))))

  Functor
  (map [this f]
    (bind this (fn [a]
                 (from-try #(f a)))))

  Bind
  (bind [ma fmb]
    (let [p (promise)]
      (on-complete ma (fn [a]
                        (if (success? a)
                          (on-complete (fmb (raw-value a))
                                       (fn [b]
                                         (complete p b)))
                          (complete p a))))
      p))

  Object
  (equals   [this other] (= (value this) (value other)))
  (hashCode [this] (hash (value this)))
  (toString [this] (pr-str (value this))))

(defn promise []
  (Promise. (atom []) (atom ::unresolved)))

(defn try* [f]
  (try
    (Success. (f))
    (catch Throwable t
      (Failure. t))))

(defn future [task]
  (let [p (promise)]
    (.execute ^java.util.concurrent.Executor executors/*executor*
              (fn []
                (complete p (try* task))))
    p))

(defn from-try [f]
  (let [p (promise)]
    (complete p (try* f))
    p))

(defn const-future [v]
  (let [p (promise)]
    (complete p (Success. v))
    p))

(defn failed-future [e]
  (let [p (promise)]
    (complete p (Failure. e))
    p))
