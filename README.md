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

### Executors

The whole point of futures is being able to perform asynchronous operations. By default, the `future` constructor uses an unbounded threadpool for doing work. This can be controlled using the `*executor*` dynamic var in the `executors` namespace. It includes an `immediate-executor` which performs work in the current thread immediately. Useful for tests. 

## Tests

There are only a few. The most important properties are tested using test.check, a property-based testing framework. There are a few explicit tests to highlight certain properties. Have a look at these, they show some sample usages



## Usage

Coming soon...

## License

Copyright © 2014 Leonardo Borges

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
