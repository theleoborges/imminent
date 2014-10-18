(ns imminent.executors
  (:import [java.util.concurrent Executor ForkJoinPool ForkJoinTask]))


(def default-executor (java.util.concurrent.ForkJoinPool.
                       (.availableProcessors (Runtime/getRuntime))))
(def ^:dynamic *executor* default-executor)

(def blocking-executor
  (reify Executor
    (execute [_ f]
      (.get (.submit default-executor f)))))

(defn forkjoin-task [executor f]
  (proxy [java.util.concurrent.RecursiveAction] []
    (compute []
      (binding [*executor* executor]
        (f)))))

(defmulti dispatch
  "Dispatches the given fuction to the current *executor*. If given a value, dispatches a function which when called applies `f` to `value`

  If the current executor is a ForkJoinPool, dispatches the function wrapped in a ForkJoinTask, otherwise it simply submits to the current executor service"
  (fn
    ([_ _]
       (type *executor*))
    ([_]
       (type *executor*))))

(defmethod dispatch ForkJoinPool
  ([f value] (dispatch #(f value)))
  ([f]
     (let [executor *executor*
           fj-task  (forkjoin-task executor f)]
       (if (ForkJoinTask/inForkJoinPool)
         (.fork fj-task)
         (.execute ^java.util.concurrent.ForkJoinPool executor
                   fj-task)))))

(defmethod dispatch :default
  ([f value] (dispatch #(f value)))
  ([f]
     (let [executor *executor*]
       (.execute ^java.util.concurrent.Executor executor
                 #(binding [*executor* executor]
                    (f))))))

;; (defn dispatch
;;   "Dispatches the given fuction to the current *executor*. If given a value, dispatches a function which when called applies `f` to `value`"
;;   ([f value] (dispatch #(f value)))
;;   ([f]
;;      (let [f (#'clojure.core/binding-conveyor-fn f)]
;;        (.execute ^java.util.concurrent.ForkJoinPool *executor*
;;                  (forkjoin-task f)))))

(defn dispatch-all
  "Dispatches all functions in `fs` to the current *executor* and value `value`"
  [fs value]
  (doseq [f fs]
    (dispatch f value)))
