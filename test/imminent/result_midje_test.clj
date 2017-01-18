(ns imminent.result-midje-test
  (:require [uncomplicate.fluokitten.core :refer [fmap, fapply, mdo, return,
                                                  pure]]
            [uncomplicate.fluokitten.test :as ft]
            [midje.sweet :refer :all]
            [imminent.result :refer [success, failure]]))

(fact "success laws"
      ;; --- Functor ---
      (fact "first functor law"
            (fmap identity (success 1)) => (success 1))
      (ft/functor-law2 inc (partial * 2) (success 42))

      ;; --- Applicative ---
      (ft/applicative-law1 inc (success 1))
      (ft/applicative-law2-identity (success 1))
      (ft/applicative-law3-composition (success inc)
                                       (success (partial * 2))
                                       (success 42))
      (ft/applicative-law4-homomorphism (success "dummy value")
                                        (partial * 2)
                                        42)
      (ft/applicative-law5-interchange (success "dummy value")
                                       (partial * 2)
                                       42)
      (ft/fapply-keeps-type (partial * 2) (success 1))

      ;; --- Monad ---
      (ft/monad-law1-left-identity (success "dummy value")
                                   (comp success inc)
                                   42)
      (ft/monad-law2-right-identity (success 1))
      (ft/monad-law3-associativity (comp success inc)
                                   (comp success (partial * 2))
                                   (success 1)))

(fact "monadic actions on success and failure"

      (fact "a successful return places results into a success"
            (mdo [x (success 1)]
                 (return (* 2 x))
                 ) => (success 2))

      (fact "a failure short-circuits"
            (mdo [x (failure "a failure")]
                 (return (* 2 x))
                 ) => (failure "a failure"))

      (fact "fmap acts correctly on success/failure"
            (fmap inc (success 1))       => (success 2)
            (fmap inc (failure "error")) => (failure "error"))

      (fact "fapply works for success/failure"
            (let [inc-lifted (success inc)]
              (fapply inc-lifted (success 1))       => (success 2)
              (fapply inc-lifted (failure "error")) => (failure "error")))

      )
