(ns imminent.examples
  (:refer-clojure :exclude [map filter future promise sequence reduce await])
  (:require [imminent.core :refer :all]
            [imminent.executors :as executors]
            [imminent.util.monad :as monad]))

(def  repl-out *out*)
(defn prn-to-repl [& args]
  (binding [*out* repl-out]
    (apply prn args)))

(comment
  (def ma (const-future 10))
  (defn fmb [n]
    (const-future (* 2 n)))
  (bind ma fmb)
  )


(comment
  (binding [executors/*executor* executors/immediate-executor])
  (def f1 (future (fn []
                    (Thread/sleep 5000)
                    (prn-to-repl (.getId (Thread/currentThread)))
                    "3")))

  (def f2 (map f1 (fn [x]
                    (prn-to-repl (.getId (Thread/currentThread)))
                    (read-string x))))

  (def f3 (filter f2 (fn [x]
                       (prn-to-repl (.getId (Thread/currentThread)))
                       (even? x))))

  )

(comment
  (def p (promise))
  (def f (->future p))
  (complete p (success 10))
  (on-success f (fn [v]
                   (prn-to-repl "got stuff" v)))
  )

(comment
  (def p1 (promise))
  (def f1 (->future p1))
  (complete p1 (success 10))
  @f1
  (on-success f1 (fn [v]
                   (prn "hmm")
                   (prn-to-repl "got stuff" v)))

  (on-failure f1 (fn [v]
                   (prn "hmm")
                   (prn-to-repl "got stuff" v)))


  (def f2 (failed-future (Exception. "")))
  (on-success f2 (fn [v]
                   (prn "hmm")
                   (prn-to-repl "got stuff on succ" v)))
  (on-failure f2 (fn [v]
                   (prn "hmm")
                   (prn-to-repl "got stuff" v)))

  )


(comment
  (let [tasks [(const-future 10)
               (const-future 20)
               (const-future 30)]]
    (-> (sequence tasks)
        (map (fn [xs]
               (prn-to-repl xs)))))
  )

(comment
  (binding [executors/*executor* executors/immediate-executor]
    (monad/fold-m future-monad
                  (fn [a mb]
                    ((:bind future-monad) mb (fn [b]
                               (const-future (conj a b)))))
                  []
                  [(const-future 20) (const-future 30)]))

  (binding [executors/*executor* executors/immediate-executor]
    (monad/fold-m future-monad
                  (fn [a mb]
                    ((:bind future-monad) mb (fn [b]
                                               (prn "got" b)
                                               (const-future (conj a b)))))
                  []
                  [(const-future 20) (failed-future (Exception. "error")) (const-future 30)]))

  (binding [executors/*executor* executors/immediate-executor]
    (reduce + 0 [(const-future 20) (const-future 30)]))

  (binding [executors/*executor* executors/immediate-executor]
    (reduce + 0 [(const-future 20) (failed-future (Exception. "error")) (const-future 30)]))


  )


(comment
  (def sleepy (future (fn []
                        (Thread/sleep 5000)
                        (prn-to-repl "awaking...")
                        57)))
  (prn-to-repl @(await sleepy))
  (prn-to-repl "finished")
  )
