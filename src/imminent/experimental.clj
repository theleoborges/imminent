(ns imminent.experimental
  (:require [clojure.walk :as w]
            [imminent.util.monad :as monad]
            [imminent.future :as future]
            [uncomplicate.fluokitten.protocols :as fkp]))


(defn extract-awaits [form]
  (->> (tree-seq sequential? identity form)

       (filter (fn [a]
                 (and (list? a)
                      (= (first a) 'await))))))

(defn replace-awaits [bindings form]
  (w/prewalk (fn [a]
               (if (and (list? a)
                        (= (first a) 'await))
                 (get bindings a)
                 a))
             form))

(defmacro async [& body]
  (let [awaits (extract-awaits body)
        bindings (map vector
                      (repeatedly gensym)
                      (map second awaits))
        body (replace-awaits (zipmap awaits (map first bindings))
                             body)]
    `(let [fs# (monad/msequence future/m-ctx [~@(map second bindings)])]
       (fkp/fmap fs# (fn [[~@(map first bindings)]]
                   ~@body)))))
