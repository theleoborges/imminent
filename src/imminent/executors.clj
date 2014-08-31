(ns imminent.executors
  (:import java.util.concurrent.Executor))


(def default-executor (java.util.concurrent.Executors/newCachedThreadPool))
(def ^:dynamic *executor* default-executor)

(def blocking-executor
  (reify Executor
    (execute [_ f]
      (.get (.submit default-executor f)))))


(defn dispatch
  "Dispatches the given fuction to the current *executor*. If given a value, dispatches a function which when called applies `f` to `value`"
  ([f value] (dispatch #(f value)))
  ([f]
     (let [f (#'clojure.core/binding-conveyor-fn f)]
       (.execute ^java.util.concurrent.Executor *executor*
                 f))))

(defn dispatch-all
  "Dispatches all functions in `fs` to the current *executor* and value `value`"
  [fs value]
  (doseq [f fs]
    (dispatch f value)))
