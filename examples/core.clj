(require '[imminent.core :as immi]
         '[imminent.executors :as executors]
         '[imminent.util.monad :as monad])

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
  (binding [executors/*executor* executors/blocking-executor])
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
  (binding [executors/*executor* executors/blocking-executor]
    (monad/fold-m future-monad
                  (fn [a mb]
                    ((:bind future-monad) mb (fn [b]
                               (const-future (conj a b)))))
                  []
                  [(const-future 20) (const-future 30)]))

  (binding [executors/*executor* executors/blocking-executor]
    (monad/fold-m future-monad
                  (fn [a mb]
                    ((:bind future-monad) mb (fn [b]
                                               (prn "got" b)
                                               (const-future (conj a b)))))
                  []
                  [(const-future 20) (failed-future (Exception. "error")) (const-future 30)]))

  (binding [executors/*executor* executors/blocking-executor]
    (reduce + 0 [(const-future 20) (const-future 30)]))

  (binding [executors/*executor* executors/blocking-executor]
    (reduce + 0 [(const-future 20) (failed-future (Exception. "error")) (const-future 30)]))


  )


(comment
  (def sleepy (future (fn []
                        (Thread/sleep 5000)
                        (prn-to-repl "awaking...")
                        57)))
  (prn-to-repl @(await sleepy))
  (prn-to-repl "finished")
  (binding [executors/*executor* executors/blocking-executor]
    (map const-future [1 2 3]))
  (def ^:dynamic *myvalue* "leo")

  (binding [*myvalue* "enif"]
    (prn-to-repl "value is " *myvalue*)
    (let [tasks (repeat 3 (fn []
                            (Thread/sleep 1000)
                            (prn-to-repl "id " (.getId (Thread/currentThread)))
                            (prn-to-repl "the name i have is " *myvalue*)))
          conveyed (clojure.core/map #'clojure.core/binding-conveyor-fn tasks)
          fs (clojure.core/map future-call conveyed)]
      (prn-to-repl "doing...")
      (await (sequence fs))
      (prn-to-repl "done")))

  )

(comment
  ;; README examples...

  (require '[imminent.core :as immi] :reload)

  (def future-result
    (->> (repeat 3 (fn []
                     (Thread/sleep 1000)
                     10))     ;; creates 3 "expensive" computations
         (map immi/future)    ;; dispatches the computations in parallel
         (immi/reduce + 0)))  ;; reduces over the futures

  (immi/map future-result prn)
  (prn "I don't block and can go about my business while I wait...")


  (def result (imminent.core.Success. 10))
  (* 10 @result) ;; 100
  (immi/map result #(* 10 %)) ;; #imminent.core.Success{:v 100}

  (def result (imminent.core.Failure. "Oops!"))
  (* 10 @result) ;; ClassCastException java.lang.String cannot be cast to java.lang.Number
  (immi/map result #(* 10 %)) ;; #imminent.core.Failure{:e "Oops!"}

  (immi/map-failure result (fn [e] (prn "the error is" e))) ;; "the error is" "Oops!"

  (immi/const-future 10) ;; #<Future@37c26da0: #imminent.core.Success{:v 10}>
  (immi/future (fn []
                 (Thread/sleep 1000)
                 ;; doing something important...
                 "done.")) ;; #<Future@79d009ff: :imminent.core/unresolved>



  (-> (immi/const-future 10)
      (immi/map #(* % %)))
  ;; #<Future@34edb5aa: #imminent.core.Success{:v 100}>



  (-> (immi/const-future 10)
      (immi/filter odd?))
  ;; #<Future@1c6b016: #imminent.core.Failure{:e #<NoSuchElementException java.util.NoSuchElementException: Failed predicate>}>



  (-> (immi/const-future 10)
      (immi/bind (fn [n] (immi/const-future (* n n)))))
  ;; #<Future@3603dd0a: #imminent.core.Success{:v 100}>

  (-> (immi/const-future 10)
      (immi/flatmap (fn [n] (immi/const-future (* n n)))))
  ;; #<Future@2385558: #imminent.core.Success{:v 100}>



  (-> [(immi/const-future 10) (immi/const-future 20) (immi/const-future 30)]
      immi/sequence)
  ;; #<Future@32afbbca: #imminent.core.Success{:v [10 20 30]}>


    (-> [(immi/const-future 10) (immi/failed-future "Oops") (immi/const-future 30)]
        immi/sequence)
    ;; #<Future@254acc8e: #imminent.core.Failure{:e "Oops"}>


  (->> [(immi/const-future 10) (immi/const-future 20) (immi/const-future 30)]
       (immi/reduce + 0))
  ;; #<Future@36783858: #imminent.core.Success{:v 60}>


  (def f (fn [n] (immi/future (fn []
                               (* n n)))))

  (immi/map-future f [1 2 3])
  ;; #<Future@69176437: #imminent.core.Success{:v [1 4 9]}>


  (def result (->> (repeat 3 (fn []
                               (Thread/sleep 1000)
                               10))
                   (map immi/future)
                   (immi/reduce + 0)))
  (immi/await result)

  (immi/await (immi/future (fn []
                             (Thread/sleep 5000)))
              500) ;; waits for 500 ms at most

  (binding [executors/*executor* executors/blocking-executor]
    (-> (immi/future (fn []
                       (Thread/sleep 5000)
                       41))
        (immi/map inc))) ;; Automatically blocks here, without the need for `await`

  ;; #<Future@ac1c71d: #imminent.core.Success{:v 42}>


  (-> (immi/const-future 42)
      (immi/on-complete prn))

  ;; #imminent.core.Success{:v 42}

  (-> (immi/const-future 42)
      (immi/on-success prn))

  ;; 42

  (-> (immi/failed-future "Error")
      (immi/on-failure prn))

  ;; "Error"
  )
