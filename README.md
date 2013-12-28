hyperloglog
===========

A redis-backed hyperloglog implementation in Clojure

[![Build Status](https://travis-ci.org/jwhitbeck/hyperloglog.png)](https://travis-ci.org/jwhitbeck/hyperloglog.png)

Hyperlolog is a highly-accurate probabilistic cardinality estimation algorithm that uses constant storage, first proposed by Flajolet *et al* [1]. This clojure library is designed for the use case where multiple frontends update shared hyperloglog counters stored in [Redis][]. Hyperloglog time-series are also supported, enabling cardinality estimates over arbitrary periods of time (e.g., number of distinct users over the past week).

[Redis]: http://redis.io

## Setup

Hyperloglog leverages the fantastic [carmine][] library for connecting to [Redis][]. Add the following dependencies to your `project.clj`:

```clojure
[com.taoensso/carmine "2.4.0"]
[hyperloglog "0.1.0"]
```

First we need to set up a [carmine][] connection pool. For example, for a simple redis instance running on `redis.company.com`, use:

```clojure
(ns my-app (:require [taoensso.carmine :as car]))
(defmacro wcar* [& body] `(car/wcar {:pool {} :spec {:host "redis.company.com"} ~@body))
```

[carmine]: https://github.com/ptaoussanis/carmine

## Usage

### Simple shared counter

Require the core namespace:

```clojure
(ns my-app (:require [hyperloglog.core :as hll]))
```

Populate the counter:

```clojure
(wcar* (hll/add "5e0b4287-e88d-4d95-99c9-3bac4dded572"))
(wcar* (hll/add "2561d259-6924-4e9b-b9cf-cea7c9eb4cc2"))
...
(wcar* (hll/add "697d99d1-2cd9-4423-a4f8-6577dfd3f923"))
```

Get the estimated number of distinct items:

```clojure
(wcar* (hll/count-distinct))
```

The `add` and `count-distinct` accept a number of options. See their respective docstrings for the full list.


### Time series

Require the time-series namespace:

```clojure
(ns my-app (:require [hyperloglog.time-series :as hll-time]))
```

Populate the counter:

```clojure
(wcar* (hll-time/add-now "5e0b4287-e88d-4d95-99c9-3bac4dded572"))
(wcar* (hll-time/add-now "2561d259-6924-4e9b-b9cf-cea7c9eb4cc2"))
...
(wcar* (hll-time/add-now "697d99d1-2cd9-4423-a4f8-6577dfd3f923"))
```

Get the estimated number of distinct items over recent time periods:

```clojure
(wcar* (hll-time/count-lastest-distinct hll-time/day))        ; last day
(wcar* (hll-time/count-lastest-distinct (* 6 hll-time/hour))) ; last 6 hours
```

Furthermore an `hll/add-at` function is available for adding items at a specific time. By default, the time-series are bucketed per hour but this is configurable (see the `hll/add-at` docstring for details).


## Implementation details

### Accuracy

This library make the following implementation choices

* Uses a 64 bit hashing function for increased accuracy on higher cardinalities (> 1 billion) as suggested by Heule *et al* [2], instead of the 32-bit hashing function with large range corrections in the original paper [1].
* No small range corrections. If *m* is the number of observables, the hyperloglog aymptotic expected relative error is not typically attained for cardinalities less than *n = 5/2 m*.

The table below shows the various hyperloglog operating points depending on the choice of the number of observables.

<table>
    <thead>
        <tr>
            <td>Number of observables (m)</td>
            <td>Minimum number of samples (5/2m)</td>
            <td>Standard error (1.04/sqrt(m))</td>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>16</td>
            <td>40</td>
            <td>26%</td>
        </tr>
        <tr>
            <td>32</td>
            <td>80</td>
            <td>18.38%</td>
        </tr>
        <tr>
            <td>64</td>
            <td>160</td>
            <td>13.00%</td>
        </tr>
        <tr>
            <td>128</td>
            <td>320</td>
            <td>9.19%</td>
        </tr>
        <tr>
            <td>256</td>
            <td>640</td>
            <td>6.50%</td>
        </tr>
        <tr>
            <td>512</td>
            <td>1280</td>
            <td>4.60%</td>
        </tr>
        <tr>
            <td>1024</td>
            <td>2560</td>
            <td>3.25%</td>
        </tr>
        <tr>
            <td>2048</td>
            <td>5120</td>
            <td>2.30%</td>
        </tr>
        <tr>
            <td>4092</td>
            <td>10230</td>
            <td>1.63%</td>
        </tr>
    </tbody>
</table>

* The number of observables scales linearly with the memory requirements. Note that the observables for a given hyperloglog is backed by a redis hash (storing *m* fields and *m* value).
* The minimum number of samples indicates the the number of samples beyond which the estimate is usually within its expected relative error (*5/2m*).
* The standard error is the square root of the variance divided by the number of samples. Its asymptotic value is *1.04/sqrt(m)*. See [1] for full details.

This library defaults to *m=1024*, but this is of course configurable (see for example the `hyperloglog.core/add` docstring`).

### Hashing

The hyperloglog algorithm relies on a good 64 bit hashing function. By default, this library uses the [MurmurHash3][] hashing function in its 128-bit variant and then keeps only the first 64 bits. Heule *et al* tested MurmurHash3 and all the common hash functions (i.e., MD5, SHA1, SHA256) and found no significant performance difference [2]. Under the hood we use the [MurmurHash3][] implementation from the [byte-streams][] library. Therefore, the `hyperloglog.core/add` function should handle anything that [byte-streams][] does (e.g., `String`, `byte[]`, `CharSequence`, `ByteBuffer`). If you need to specify a custom 64-bit hashing function, then you can pass your own as follows.

```clojure
(def hll-opts {:hashing-fn my-hashing-function})
(wcar* (hll/add my-custom-object hll-opts))
```

[MurmurHash3]: https://en.wikipedia.org/wiki/Murmurhash
[byte-streams]: https://github.com/ztellman/byte-streams

## References

1. Philippe Flajolet, Eric Fusy, and Olivier Gandouet. [Hyperloglog: The analysis of a near-optimal cardinality estimation algorithm](http://algo.inria.fr/flajolet/Publications/FlFuGaMe07.pdf). In *Proc. AOFA*, 2007
2. Stefan Heule, Marc Nunkesser, and Alex Hall. [HyperLogLog in Practice: Algorithmic Engineering of a State of The Art Cardinality Estimation Algorithm](http://research.google.com/pubs/pub40671.html). In *Proc EDBT*, 2013

## License

Copyright &copy; 2013 John Whitbeck

Distributed under the Eclipse Public License, the same as Clojure.
