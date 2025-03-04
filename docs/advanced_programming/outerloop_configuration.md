# Load Flow Outer-Loops configuration

PowSyBl Open LoadFlow provides out of the box two mechanisms for configuring Outer-Loops via
[Load Flow parameters](../loadflow/parameters.md#specific-parameters):
- Option 1: using "basic" parameters: This is the standard usage. For example setting `useReactiveLimits`
will configure an OuterLoop dedicated to handling reactive power limits of generators, static VAR compensators, etc...
- Option 2: using the more advanced `outerLoopNames` parameter, allowing to tune not only 
the outer-loops creation, but also specifying a specific order for OuterLoops execution.

Refer to the `outerLoopNames` [Load Flow parameter documentation](../loadflow/parameters.md#specific-parameters)
for more details about these two standard ways of configuring Outer Loops.

For even more advanced usage, it is possible to plug your own Outer-Loops configuration into PowSyBl Open LoadFlow.
To do so, you will have to provide via the Java Service Loader your own implementation of the following interfaces,
defined in package `com.powsybl.openloadflow.lf.outerloop.config`:
- `AcOuterLoopConfig` for AC LoadFlow
- `DcOuterLoopConfig` for DC LoadFlow

In fact, PowSyBl Open LoadFlow internally is using these interfaces for the two options described at the beginning
of this chapter:
- if `outerLoopNames` parameter is blank, PowSyBl Open LoadFlow uses its own internal implementation
called `DefaultAcOuterLoopConfig` / `DefaultDcOuterLoopConfig`
- if `outerLoopNames` parameter is used, PowSyBl Open LoadFlow uses its own internal implementation
called `ExplicitAcOuterLoopConfig` / `ExplicitDcOuterLoopConfig`

If an alternative implementation of `AcOuterLoopConfig` / `DcOuterLoopConfig`is provided via the Java Service Loader,
it will be used as a replacement of the Open LoadFlow implementations above. The use cases typically are:
- customizing the creation and/or ordering of the PowSyBl Open LoadFlow provided Outer-Loops
- creating new Outer-Loop types/implementations in your own private code, by implementing new
`AcOuterLoop` / `DcOuterLoop`, then instantiating these Outer-Loops in your custom Outer-Loops configuration.

Some tips:
- instead of re-creating your own Outer-Loop configuration implementation from scratch, you could instead benefit
from existing classes and extend them as needed, e.g. for AC Load Flow Outer-Loops configuration you may find it easier
to extend either `AbstractAcOuterLoopConfig` or `DefaultAcOuterLoopConfig`.
- If you are creating new Outer-Loop types/implementations, typically you are going to need more data from the network.
This is a typical use case of the [LfNetwork Loader PostProcessors](lfnetwork_loader_postprocessor.md)
