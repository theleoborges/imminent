(ns imminent.executors
  (:import [java.util.concurrent Executor ForkJoinPool]))


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

(defmulti dispatch (fn
                     ([_ _]
                        (type *executor*))
                     ([_]
                        (type *executor*))))

(defmethod dispatch ForkJoinPool
  ([f value] (dispatch #(f value)))
  ([f]
     (let [executor *executor*]
       (.execute ^java.util.concurrent.ForkJoinPool executor
                 (forkjoin-task executor f)))))

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
