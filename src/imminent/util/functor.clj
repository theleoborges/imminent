(ns imminent.util.functor)

(defprotocol BiFunctor
  (bimap [fv f g]))
