(ns imminent.core
  (:refer-clojure :exclude [map filter future promise sequence]))

(set! *warn-on-reflection* true)

(def  repl-out *out*)
(defn prn-to-repl [& args]
  (binding [*out* repl-out]
    (apply prn args)))

(def default-thread-count (+ 2 (.availableProcessors (Runtime/getRuntime))))
(def default-thread-pool (java.util.concurrent.Executors/newFixedThreadPool default-thread-count))

(defprotocol Functor
  (map [this f]))

(defprotocol Bind
  (bind [this other]))

(defprotocol IReturn
  (success?  [this])
  (failure?  [this])
  (raw-value [this]))

(defrecord Success [v]
  IReturn
  (success?  [this] true)
  (failure?  [this] false)
  (raw-value [this] v)
  Functor
  (map       [this f]
    (Success. (f v))))

(defn success [v]
  (Success. v))

(defrecord Failure [e]
  IReturn
  (success?  [this] false)
  (failure?  [this] true)
  (raw-value [this] e)
  Functor
  (map       [this _]
    this))

(defn failure [v]
  (Failure. v))

(defprotocol IFuture
  (value        [this])
  (on-success   [this f])
  (on-failure   [this f])
  (on-complete  [this f])
  (filter       [this f?])
  (flatmap      [this f])
  (sequence     [this]))

(defprotocol IPromise
  (complete [this value]))


;; (defrecord Future [listeners]
;;   IFuture
;;   (on-success   [this f])
;;   (on-failure     [this f])
;;   (on-complete  [this f]))

(defn dispatch [listeners value]
  (doseq [f listeners]
    (f value)))

(declare promise)

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
        (f st))))

  IPromise
  (complete [this value]
    (if (= @state ::unresolved)
      (do
        (reset! state value)
        (dispatch @listeners value))
      (throw (Exception. "Attempted to complete already completed promise"))))

  Functor
  (map [this f]
    (let [p (promise)]
      (on-complete this (fn [v]
                          (complete p (map v f))))
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
    (.submit ^java.util.concurrent.ExecutorService default-thread-pool
             (reify Runnable
               (run [_]
                 (complete p (try* task)))))
    p))

(defn const-future [v]
  (let [p (promise)]
    (complete p (Success. v))
    p))

;; (def f (future (fn []
;;                  (prn-to-repl "thinking...")
;;                  (Thread/sleep 2000)
;;                  (prn-to-repl "done thinking...")
;;                  42)))

;; (def f1 (map f #(* 10 %)))
;; (on-success f1 (fn [v]
;;                 (prn-to-repl "The answer of life is now..." v)))

;; (on-success f (fn [v]
;;                 (prn-to-repl "The answer of life is..." v)))

;; (on-complete f (fn [v]
;;                 (prn-to-repl "done. " v)))

;; (def p (promise))
;; (on-failure p (fn [v] (prn "complete with " v)))

;; (complete p (Failure. (Exception. "Whatever")))
