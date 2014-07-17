(ns imminent.executors
  (:import java.util.concurrent.Executor))


(def default-executor (java.util.concurrent.Executors/newCachedThreadPool))
(def ^:dynamic *executor* default-executor)

(def immediate-executor
  (reify Executor
    (execute [_ f]
      (f))))
