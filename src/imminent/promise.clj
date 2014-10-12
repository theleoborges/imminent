(ns imminent.promise
  (:require [imminent.protocols :refer [IPromise]]
            [imminent.executors :as executors])
  (:import clojure.lang.IDeref))
