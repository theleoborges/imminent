(ns imminent.executors
  (:import [java.util.concurrent
            Executor ForkJoinPool ForkJoinPool$ManagedBlocker ForkJoinTask]))


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

(defn dispatch-all
  "Dispatches all functions in `fs` to the current *executor* and value `value`"
  [fs value]
  (doseq [f fs]
    (dispatch f value)))


(defn managed-blocker [f]
  (let [done (atom false)]
    (reify ForkJoinPool$ManagedBlocker
      (block [this]
        (try (f)
             (finally
               (reset! done true)))
        true)
      (isReleasable [this]
        @done))))

(defmulti dispatch-blocking
  "Same as dispatch. Except that, in a ForkJoinTask, uses `ManagedBlocker` to tell the ForkJoinPool it might block. This allows the pool to make adjustments in order to ensure optimal thread liveness."
  (fn
    ([_ _]
       (type *executor*))
    ([_]
       (type *executor*))))

(defmethod dispatch-blocking ForkJoinPool
  ([f value] (dispatch #(f value)))
  ([f]

     (let [executor *executor*
           fj-task  (forkjoin-task
                     executor (fn []
                                (ForkJoinPool/managedBlock (managed-blocker f))))]
       (if (ForkJoinTask/inForkJoinPool)
         (.fork fj-task)
         (.execute ^java.util.concurrent.ForkJoinPool executor
                   fj-task)))))


(defmethod dispatch-blocking :default
  ([f value] (dispatch #(f value)))
  ([f]
     (dispatch f)))
