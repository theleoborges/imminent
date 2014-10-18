(ns imminent.protocols
  (:refer-clojure :exclude [map filter await]))

(defprotocol IReturn
  (success?    [this])
  (failure?    [this])
  (map-failure [this f]
    "applies the supplied function to the internal value only if it is a Failure type"))

(defprotocol IFuture
  (on-success   [fut f]
    "applies f to the result of this future iff it completes successfully")
  (on-failure   [fut f]
    "applies f to the result of this future iff it completes with a failure")
  (on-complete  [fut f]
    "applies f to a value of type 'IResult' once this future completes")
  (filter       [fut pred]
    "Applies pred to the value of this Future. The new Future will contain a value of type success if (pre value) is true. It will contain a Failure wrapping a NoSuchElementException otherwise")
  (zip          [fut other]
    "Zips this future with 'other'. Resturns a new Future containing a two-element vector representing the result of each Future.")
  (completed?   [fut]
    "Returns true when this future has been completed with a value or an exception. False otherwise"))



(defprotocol IAwaitable
  (await
    [awaitable]
    [awaitable ms] "Blocks until the awaitable reference is done. Optionally awaits for up to 'ms' milliseconds, throwing a TimeoutException once time is up."))

(defprotocol IPromise
  (complete   [promise value] "Completes this promise with the given value")
  (->future   [promise]       "Returns a Future backed by this promise"))
