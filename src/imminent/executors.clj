(ns imminent.executors
  (:import java.util.concurrent.Executor))


(def default-executor (java.util.concurrent.Executors/newCachedThreadPool))
(def ^:dynamic *executor* default-executor)

(def blocking-executor
  (reify Executor
    (execute [_ f]
      (.get (.submit default-executor f)))))


(defn dispatch
  ([f value] (dispatch #(f value)))
  ([f]
     (let [f (#'clojure.core/binding-conveyor-fn f)]
       (.execute ^java.util.concurrent.Executor *executor*
                 f))))

(defn dispatch-all [listeners value]
  (doseq [f listeners]
    (dispatch f value)))
