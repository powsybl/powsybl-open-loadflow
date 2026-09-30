# State estimation

An AC weighted least squares state estimator built on the same network model, equation
terms and linear algebra the load flow uses.

The code is experimental. There is no provider and no public API yet, so the estimator is
used directly through `WlsStateEstimator`.

## Running the parity demo

```sh
mvn test -Dtest=WlsStateEstimatorDemo
```

This estimates the state of IEEE 14-bus from a fixed set of measurements and compares the
answer against pandapower's estimate from the same measurements, printing the largest
deviation in each quantity.

Requires a JDK 21 and Maven, and nothing else. Both the measurements and pandapower's
results are checked in, so nothing is downloaded or recomputed.

Expect four iterations on both sides and agreement several orders of magnitude inside the
tolerances.

## The measurement set

`src/test/resources/se/scenario_ieee14.json` holds 49 measurements: voltage magnitude at
the five generator buses, active and reactive injection at every bus, and active and
reactive flow at the from-side of eight lines.

They were made by running a power flow on the network and adding Gaussian noise to the
result, with one standard deviation per measurement class and a fixed random seed. The
file records the true value alongside the measured one, so the noise can be inspected.

`src/test/resources/se/results_pandapower.json` holds pandapower's estimate of that same
set: per-bus voltage magnitude and angle, per-line flows, the iteration count.

Both files are inputs. Regenerating either one changes what the comparison means.

## What is implemented

Voltage magnitude, active and reactive injection, and active and reactive flow
measurements, each at a bus or at one side of a branch. Gauss-Newton iteration on the
normal equations, from a flat start or from a supplied state. One angle per synchronous
component is held fixed as the reference, and its column is never assembled.

The measurement functions come from this repository's existing AC flow terms, evaluated
through `Derivable`, so the estimator and the load flow interpret a network identically.
