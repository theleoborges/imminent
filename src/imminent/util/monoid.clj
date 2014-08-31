(ns imminent.util.monoid)

(defprotocol Semigroup
  (append [m1 m2]
    "Associative operation between semigroups m1 and m2"))

(defn sconcat
  "Reduces a non-empty list of semigroups using `append`"
  [xs]
  (assert (seq xs) "sconcat requires a non-empty list of semigroups")
  (let [[x & xs] xs]
    (reduce append x xs)))
