# imminent

Composable futures for Clojure

## Brain dump

The library contains 2 basic data types with distinct semantics: Promises and Futures. 

### Promises

Promises are write-once containers and can be in once of 3 states: Unresolved, resolved successfully or resolved unsuccessfully. You can obtain a Future from a Promise using the `->future` function.

### Futures

Futures are read-only containers and, just like promises, can be in one of 3 states: Unresolved, resolved successfully or resolved unsuccessfully.

Futures will eventually provide a rich set of combinators (but a few are already available, such as `map`, `filter` and `flatmap`) as well as the event handlers `on-complete`, `on-success` and `on-failure`.

Both Futures and Promises implement `IDeref` so you can obtain the current value.

There is no blocking `get` operation at the moment.

### Why both?

Technically you need only a single data type to implement the functionality provided by futures. However this separation is helpful in preventing a future from being completed by the wrong code path. By making futures read-only, this gets mitigated.

### Executors

The whole point of futures is being able to perform asynchronous operations. By default, the `future` constructor uses an unbounded threadpool for doing work. The user has fine grained control over this by using the `executors/*executor*` dynamic var. It includes an `immediate-executor` which performs work in the current thread immediately. Useful for tests. 

## Tests

There are only a few. The most important properties are tested using test.check, a property-based testing framework. There are a few explicit tests to highlight certain properties. Have a look at these, they show some sample usages

## Why not use core.async channels or streams/observables/rx/etc...?

Short answer: semantics.

core.async channels offer a fine grained coordination mechanism and uses lightweight threads. That makes it powerful but unsuited for IO operations as it can drain its threadpool quite quickly. Additionally, channels are *single take* containers so if you need to share the result of a computation with more than one consumer you need pub/sub.

Streams are useful when you have data which is naturally modelled as signals such as reading user keyboard input, mouse movement and pretty anything which is time-dependant and generates a continuous stream of values. It's overkill for one-off parallel computations.

Imminent provides the semantics needed for working with these one-off parallel computations as well as several combinators which can be used to combine and coordinate between them. A complex-enough project will likely benefit from a combination of the 3 approaches.

### Exception handling

Imminent follows the patterns found in Compositional Event Systems such as RxJava: any exception thrown during the execution of a future or any of its combinators will result in a future containing a value of type `Failure`, wrapping the error/exception.

Additionally, Futures provide `on-success`/`on-failure` event handlers to deal with success and failure values directly.

## Priorities
1. Correctness
1. Rich set of combinators
1. Documentation
1. Performance 
1. Clojurescript
 * could use core.async for this on the client-side


## Usage

Coming soon...

## License

Copyright © 2014 Leonardo Borges

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.