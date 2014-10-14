(ns imminent.result-test
  (:require [imminent.core :as core]
            [imminent.result :refer :all]
            [clojure.test :refer :all]
            [clojure.core.match :refer [match]])
  (:import (imminent.result Success Failure)))

(deftest pattern-matching
  (testing "success"
    (are [x y] (= x y)

         10      (match [(success 10)]
                        [{Success v}] v)

         10      (match [(success 10)]
                        [{Failure e}] :shouldnt-get-here
                        [{Success v}] v)

         "Error" (match [(failure "Error")]
                        [{Failure v}] v)

         "Error" (match [(failure "Error")]
                        [{Success v}] :shouldnt-get-here
                        [{Failure v}] v))))
