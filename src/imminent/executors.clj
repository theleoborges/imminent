(ns imminent.executors
  (:import java.util.concurrent.Executor))


(def default-executor (java.util.concurrent.Executors/newCachedThreadPool))
(def ^:dynamic *executor* default-executor)

(def blocking-executor
  (reify Executor
    (execute [_ f]
      (.get (.submit default-executor f)))))
