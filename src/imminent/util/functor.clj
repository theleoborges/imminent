(ns imminent.util.functor)

(defprotocol BiFunctor
  "Functor of two arguments"
  (bimap [fv f g]
    "Map over both arguments at the same time"))
