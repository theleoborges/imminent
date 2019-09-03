# imminent [![Build Status](https://travis-ci.org/leonardoborges/imminent.svg?branch=master)](https://travis-ci.org/leonardoborges/imminent) <a href='https://ko-fi.com/H2H8OH34' target='_blank'><img height='36' style='border:0px;height:36px;' src='https://az743702.vo.msecnd.net/cdn/kofi2.png?v=0' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>

Composable futures for Clojure 

## Table of Contents

* [API docs](http://leonardoborges.github.com/imminent/)
* [Motivation](#motivation)
* [Setup](#setup)
* [TL;DR](#tldr)
* [Basic types](#basic-types)
	* [Promise](#promise)
	* [Future](#future)
	  * [Why both?](#why-both)
	* [IResult](#iresult)
* [Creating futures](#creating-futures)		
* [Combinators](#combinators)	
* [Event handlers](#event-handlers)	
    * [Pattern matching](#pattern-matching) 
* [Awaiting](#awaiting)
* [The 'mdo' macro](#the-mdo-macro)
* [Executors](#executors)	
    * [Blocking IO](#blocking-io)
* [FAQ](#faq)
	* [Why not use core.async?](#why-not-use-coreasync)
	* [Why not use reactive frameworks?](#why-not-use-reactive-frameworks)	
* [Exception Handling](#exception-handling)	
* [Contributing](#contributing)
* [TODO](#todo)
* [CHANGELOG](https://github.com/leonardoborges/imminent/blob/master/CHANGELOG.md)
* [CONTRIBUTORS](https://github.com/leonardoborges/imminent/graphs/contributors)
* [License](#license)

## Motivation

Clojure already provides [futures](http://clojuredocs.org/clojure_core/clojure.core/future) and [promises](http://clojuredocs.org/clojure_core/clojure.core/future) so why another library?

Simply put, because the (1) core abstractions don't compose and (2) in order to get the value out of one of them you have to necessarily block the current thread.

Imminent solves both problems. It is also heavily inspired by Scala's Futures and Twitter Futures.

## Setup

>This is super alpha quality at this stage as it hasn't been battle tested enough. You've been warned :)

Add the following to your `project.clj`:


![Clojars Project](http://clojars.org/com.leonardoborges/imminent/latest-version.svg)


Require the library:

```clojure
(require [imminent.core :as immi])
```

Now you should be ready to rock.
 
## TL;DR

For the impatient, I've included a couple of examples below. I've chosen to translate the examples presented by [Ben Christensen](https://twitter.com/benjchristensen) - of RxJava - in [this gist](https://gist.github.com/benjchristensen/4671081). Albeit them being in Java, they highlight perfectly the problem with blocking futures. Here's their Clojure equivalent:

```clojure
;;
;; Example 1, 2 & 3 are handled by the approach below
;; Original examples: https://gist.github.com/benjchristensen/4671081#file-futuresb-java-L13
;;

(defn example-1 []
  (let [f1     (remote-service-a)
        f2     (remote-service-b)
        f3     (immi/flatmap f1 remote-service-c)
        f4     (immi/flatmap f2 remote-service-d)
        f5     (immi/flatmap f2 remote-service-e)
        result (immi/sequence [f3 f4 f5])]
    (immi/on-success result
                     (fn [[r3 r4 r5]]
                       (prn (format "%s => %s" r3 (* r4 r5)))))))
                       
;;
;; Example 4 & 5 are handled by the approach below
;; Original examples: https://gist.github.com/benjchristensen/4671081#file-futuresb-java-L106
;;

(defn do-more-work [x]
  (prn "do more work => " x))

(defn example-4 []
  (let [futures (conj []
                      (immi/future (remote-service-a))
                      (immi/future (remote-service-b))
                      (immi/future (remote-service-c "A"))
                      (immi/future (remote-service-c "B"))
                      (immi/future (remote-service-c "C"))
                      (immi/future (remote-service-d 1))
                      (immi/future (remote-service-e 2))
                      (immi/future (remote-service-e 3))
                      (immi/future (remote-service-e 4))
                      (immi/future (remote-service-e 5)))]
    (doseq [f futures]
      (immi/on-success f do-more-work))))
```

Both examples above are **non-blocking** and use combinators to operate over single futures or a sequence of futures. The runnable examples can be found under `examples/netflix.clj`


I highly recommend you keep reading for the full list :)

## Basic types

The library contains 2 basic data types with distinct semantics: Promises and Futures. 

### Promise

Promises are write-once containers and can be in once of 3 states: Unresolved, resolved successfully or resolved unsuccessfully. You can obtain a Future from a Promise using the `->future` function.

### Future

Futures are read-only containers and, just like promises, can be in one of 3 states: Unresolved, resolved successfully or resolved unsuccessfully.

Futures will eventually provide a rich set of combinators (but a few are already available, such as `map`, `filter` and `flatmap`) as well as the event handlers `on-complete`, `on-success` and `on-failure`.

Both Futures and Promises implement `IDeref` so you can obtain the current value.

You can optionally block on a future by using `immi/await`.

#### Why both?

Technically you need only a single data type to implement the functionality provided by futures. However this separation is helpful in preventing a future from being completed by the wrong code path. By making futures read-only, this gets mitigated.

### IResult

When a future completes - either in success or failure - its result will be wrapped in a value of type `IResult`. Only two types implement this protocol: `Success` and `Failure`

Just like Futures, they are both [Functors](http://www.leonardoborges.com/writings/2012/11/30/monads-in-small-bites-part-i-functors/), meaning you can map over them just as you would with a list.

There are two ways by which you can get hold of the value wrapped in a IResult. You can `deref` it or map a function over it:

```clojure
  (def result (imminent.core.Success. 10))
  
  (* 10 @result) ;; 100
  (immi/map result #(* 10 %)) ;; #imminent.core.Success{:v 100}
```

You'll see the behaviour for `Failure` is a little different:

```clojure
  (def result (imminent.core.Failure. "Oops!"))
  
  (* 10 @result) ;; ClassCastException java.lang.String cannot be cast to java.lang.Number
  (immi/map result #(* 10 %)) ;; #imminent.core.Failure{:e "Oops!"}
```

As you'd expect, the second line fails but if we try to map a function over a failure, it simply short-circuits and returns itself. 

If you know you are dealing with a failure - because you asked using the `failure?` predicate, you can then use the `map-failure` function which has the semantics of `map` inverted:

```clojure
(immi/map-failure result (fn [e] (prn "the error is" e))) ;; "the error is" "Oops!"
```

## Creating futures

The easiest way to create a new future is using `immi/const-future`. It creates a Future and immediately completes it with the provided value:

```clojure
  (immi/const-future 10) ;; #<Future@37c26da0: #imminent.core.Success{:v 10}>
```

This isn't very useful and is used mostly by the library itself.

A more useful constructor is `immi/future-call` which dispatches the given function to the default `executors/*executor*` and returns a `Future`:

```clojure
  (immi/future-call (fn []
                      (Thread/sleep 1000)
                      ;; doing something important...
                      "done.")) 
  ;; #<Future@79d009ff: :imminent.core/unresolved>
```

There is also a macro, `immi/future`, which is simply a convenient wrapper around `immi/future-call`:

```clojure
  (immi/future (Thread/sleep 1000)
               ;; doing something important...
               "done.")    
  ;; #<Future@79d009ff: :imminent.core/unresolved>
```

## Combinators

Imminent really pays off when you need to compose multiple operations over a future and/or over a list of futures. See the [API Docs](http://leonardoborges.github.com/imminent/) for a full list. Looking at the tests is another great way to get acquainted with them. Nevertheless, a few examples follow:


### map

```clojure
  (-> (immi/const-future 10)
      (immi/map #(* % %)))
  ;; #<Future@34edb5aa: #imminent.core.Success{:v 100}>
```

### filter

```clojure
  (-> (immi/const-future 10)
      (immi/filter odd?))
  ;; #<Future@1c6b016: #imminent.core.Failure{:e #<NoSuchElementException java.util.NoSuchElementException: Failed predicate>}>
```

### bind/flatmap

Monadic bind. Note how in the example below, we bind to a future a function that itself returns another future. `bind`/`flatmap` carries out the operations of both futures and flattens the result, returning a single future.

```clojure
  (-> (immi/const-future 10)
      (immi/bind (fn [n] (immi/const-future (* n n)))))
  ;; #<Future@3603dd0a: #imminent.core.Success{:v 100}>

  (-> (immi/const-future 10)
      (immi/flatmap (fn [n] (immi/const-future (* n n)))))
  ;; #<Future@2385558: #imminent.core.Success{:v 100}>
```  

### amb

`amb` is the *ambiguous* function. 

It derives its name from Common Lisp and Scheme. In *imminent* it simply means that given a sequence of Futures, `amb` returns a Future that will complete with the result of the first Future to complete in the given sequence regardless of whether it was successful:

```clojure
  (defmacro sleepy-future [ms & body]
    `(immi/future
       (Thread/sleep ~ms)
       ~@body))


  (-> (immi/amb (sleepy-future 100 10)
                (sleepy-future 100 10)
                (sleepy-future 10  20)
                (sleepy-future 100 10))
      (immi/await 200)
      immi/deref)
  ;; #object[imminent.result.Success 0x6e6bdd39 {:status :ready, :val 20}]
  
  
  ;; and with a failure:
  (-> (immi/amb (sleepy-future 100 10)
                (sleepy-future 100 10)
                (immi/failed-future (Exception.))
                (sleepy-future 100 10))
      (immi/await 200)
      deref)
  ;; #object[imminent.result.Failure ...]
```

### sequence

Given a list of futures, returns a future that will eventually contain a list of all results:

```clojure
  (-> [(immi/const-future 10) (immi/const-future 20) (immi/const-future 30)]
      immi/sequence)
  ;; #<Future@32afbbca: #imminent.core.Success{:v [10 20 30]}>
```

### reduce

Reduces over a list of futures.

```clojure
  (->> [(immi/const-future 10) (immi/const-future 20) (immi/const-future 30)]
       (immi/reduce + 0))
  ;; #<Future@36783858: #imminent.core.Success{:v 60}>
```  

### map-future

Maps a future returning function over the given list and sequences the resulting futures.

```clojure
  (def f #(immi/future (* % %)))

  (immi/map-future f [1 2 3])
  ;; #<Future@69176437: #imminent.core.Success{:v [1 4 9]}>
```  

## Event handlers

You can register functions to be called once a future has been completed.

### on-success

If successful, calls the supplied function with the value wrapped in the `IResult` type, `Success`.

```clojure
  (-> (immi/const-future 42)
      (immi/on-success prn))

  ;; 42
```

### on-failure

If failed, calls the supplied function with the value wrapped in the `IResult` type, `Failure`.  

```clojure
  (-> (immi/failed-future "Error")
      (immi/on-failure prn))

  ;; "Error"
```  

### on-complete

Calls the given function with the future's result type:

```clojure
  (-> (immi/const-future 42)
      (immi/on-complete prn))

  ;; #imminent.core.Success{:v 42}
```

### Pattern matching

Imminent provides a limited form of pattern matching thanks to [core.match](https://github.com/clojure/core.match). This means you can easily handle both success and failure cases in the `on-complete` handler:

```clojure
(-> (immi/const-future 42)
    (immi/on-complete #(match [%]
                              [{Success v}] (prn "success: " v)
                              [{Failure e}] (prn "failure: " e))))

;; "success:  " 42
```

## Awaiting

Futures implement the `IAwaitable` protocol for the scenario where you truly need to block and wait for a future to complete:

```clojure
  (def result (->> (repeat 3 (fn []
                               (Thread/sleep 1000)
                               10)) 
                   (map immi/future-call) 
                   (immi/reduce + 0)))
  @(immi/await result) ;; will block here until all futures are done
  
  ;; #<Future@485bceb6: #imminent.core.Success{:v 30}>
```

You can optionally provide a timeout - highly recommended - after which an Exception is thrown if the Future hasn't completed yet:

```clojure
  (immi/await (immi/future-call (fn []
                                  (Thread/sleep 5000)))
              500) ;; waits for 500 ms at most
  ;; #<Future@7d27f6c3: #imminent.core.Failure{:e #<TimeoutException java.util.concurrent.TimeoutException: Timeout waiting future>}>
```
## The *mdo* macro

Imminent uses [fluokitten](https://github.com/uncomplicate/fluokitten) to provide its main abstractions such as Monads, Applicatives and Functors. Any Monad can take advantage of the `mdo` macro.

Writing code using `bind/flatmap` can be inconvenient when we have futures where the output of one is the input of another. To demonstrate, suppose we have very expensive functions that double, square and create the range of a number:

```clojure
  (defn f-double [n]
    ;; expensive computation here...
    (immi/const-future (* n 2)))
  (defn f-square [n]
    ;; expensive computation here...
    (immi/const-future (* n n)))
  (defn f-range [n]
    ;; expensive computation here...
    (immi/const-future (range n)))
```

Using `flatmap` the code would look like this:

```clojure
(immi/flatmap (immi/const-future 1)
                (fn [m]
                  (immi/flatmap (f-double m)
                                (fn [n]
                                  (immi/flatmap (f-square n)
                                                f-range)))))

;; #<Future@42f92dbc: #<Success@7529b3fd: (0 1 2 3)>>
```

This is usually not the code you would want to write. There is another - better - way using the `mdo` macro provided by [fluokitten](https://github.com/uncomplicate/fluokitten):

```clojure
(immi/mdo [a (immi/const-future 1)
           b (f-double a)
           o (f-square b)]
         (f-range o))
         
;; #<Future@76150f6f: #<Success@60a87cf9: (0 1 2 3)>>
```

Both snippets are semantically equivalent but the latter is more convenient as it allows us to write sequential-looking code that is, in fact, asynchronous.

As another example, we can rewrite the `example-1` function from the beginning of this guide like so:

```clojure
(defn example-1 []
  (let [result (immi/mdo [f1  (remote-service-a)
                          f2  (remote-service-b)
                          f3  (remote-service-c f1)
                          f4  (remote-service-d f2)
                          f5  (remote-service-e f2)]
                         (immi/const-future [f3 f4 f5]))]
    (immi/on-success result
                     (fn [[r3 r4 r5]]
                       (prn (format "%s => %s" r3 (* r4 r5)))))))

```

Once again we get sequential-looking code without explicitly calling `bind/flatmap`. You might have noticed that the in the example above, the last thing we do is to call `immi/const-future`. There is a reason for that.

The result of the `mdo` macro has to be a Future - strictly speaking, a Monad - and since `f-range` returns a Future, we don't need to do anything else.

Differently - in `example-1` - we are interested in the result `[f3 f4 f5]` but since that is simply a Clojure vector we need a way to put that vector inside a Future. 

Therefore we call `immi/const-future` which we learned about in the section on creating Futures.

## Executors

The whole point of futures is being able to perform parallel tasks. By default imminent uses Java's [ForkJoinPool](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ForkJoinPool.html) with parallelism set to the number of available processors.

The user has fine grained control over this by using the `executors/*executor*` dynamic var. It includes a `blocking-executor` which blocks on any given task, making it useful for testing:

```clojure
  (binding [executors/*executor* executors/blocking-executor]
      (-> (immi/future
            (Thread/sleep 5000)
            41)
          (immi/map inc))) ;; Automatically blocks here, without the need for `await`

  ;; #<Future@ac1c71d: #imminent.core.Success{:v 42}>
```

### Blocking IO

One of the advantages of using a ForkJoinPool is that one can have a large amount of futures in memory being served by a much smaller number of actual threads in the pool. 

The ForkJoinPool is smart enough and tries to keep all threads active by dynamically expanding and shrinking the pool as required. However in order for it to do its job, all computations must be CPU-bound - i.e.: free of side effects.

In the face of blocking IO the performance of the ForkJoinPool may be compromised.

To prevent this, Imminent provides two constructs for creating futures which may block: the function `blocking-future-call` and the macro `blocking-future`, analogous to `future-call` and `future` respectively:

```clojure
  (->> (repeat 3 (fn []
                   (Thread/sleep 1000)
                   10))
       ;; creates 3 "expensive" computations
       (map immi/blocking-future-call)
       ;; dispatches the computations in parallel,
       ;; indicating they might block
       (immi/reduce + 0))
  ;; #<Future@1d4ed70: #<Success@34dda2f6: 30>>
```

They work in exactly the same way as their non-blocking counterparts but use ForkJoinPool-specific API to indicate the current thread may block, but the futures themselves remain asynchronous. 

The pool then might choose to increase its number of threads to keep up with the desired level of parallelism.

## FAQ

### Why not use core.async?

Short answer: semantics.

The longer version:

[core.async](https://github.com/clojure/core.async) channels offer a fine grained coordination mechanism and uses lightweight threads which can be parked, thus optimising thread's idle time. 

For problems which are non-blocking in nature and need a queue-like mechanism to communicate between workers, it is a great abstraction.

It does however mean developers need to learn about channels and their behaviour. Channels are *single take* containers so if you need to share the result of a computation with more than one consumer you need to use core.async's pub/sub features. 

core.async will also swallow exceptions by default and this is in contrast with what imminent provides as you can see in the [Exception Handling](#exception-handling) section.

Additionally the thread pool backing up core.async is for CPU-bound computations only - given enough blocking computations, the pool might starve. As stated under [Blocking IO](#blocking-io), imminent mitigates this by using a ForkJoinPool.

### Why not use reactive frameworks?

Frameworks such as [RxJava](https://github.com/Netflix/RxJava) , [reagi](https://github.com/weavejester/reagi) and others are useful when you have data which is naturally modelled as signals. This includes things such as reading user keyboard input, mouse movement and pretty much anything which is time-dependant and generates a continuous flow of values. 

To put it another way, they provide a way to model mutable state.

It can be overkill for one-off parallel computations - i.e.: a stream that emits a single value and then terminates.


Imminent provides the semantics needed for working with these one-off parallel computations as well as several combinators which can be used to combine and coordinate between them. A complex-enough project will likely benefit from a mix of the 3 approaches.

## Exception handling

Imminent follows the patterns found in Compositional Event Systems such as RxJava: any exception thrown during the execution of a future or any of its combinators will result in a future containing a value of type `Failure`. This value wraps the error/exception.

Additionally, Futures provide `on-success`/`on-failure` event handlers to deal with success and failure values directly.

## Contributing

Pull requests are not only welcome but highly encouraged! Simply make sure your PR contains passing tests for your bug fix.

If you would like to submit a PR for a new feature, open an issue first as someone else - or even myself - might already be working on it.

Feedback on both this library and this guide is welcome.

### Running the tests

Imminent has been developed and tested using Clojure 1.6 (under both Java 7 and Java 8).

To run the tests:

```bash
λ lein test
```

## TODO

- Improve documentation

## License

Copyright © 2014-2019 [Leonardo Borges](http://www.leonardoborges.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
