(ns imminent.examples
  (:refer-clojure :exclude [map filter future promise sequence])
  (:require [imminent.core :refer :all]
            [imminent.executors :as executors]))

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
  (on-complete f (fn [v]
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
