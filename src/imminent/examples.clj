(ns imminent.examples
  (:refer-clojure :exclude [map filter future promise sequence])
  (:require [imminent.core :refer :all]
            [imminent.protocols :refer :all]
            [imminent.executors :as executors]))

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
