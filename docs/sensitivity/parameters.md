# Configuration

To use PowSyBl Open Load Flow for all sensitivity analyses, you have to configure the `sensitivity-analysis` module in your configuration file:
```yaml
sensitivity-analysis:
  default-impl-name: OpenLoadFlow
```

## Generic parameters

Generic parameters are documented in [PowSyBl Core](inv:powsyblcore:*:*#simulation/sensitivity/configuration)

The next section details the parameters that are specific to PowSyBl Open LoadFLow.

## Specific parameters

**debugDir**  
Allows to dump debug files to a specific directory.  
The default value is undefined (`null`), disabling any debug files writing.

**startWithFrozenACEmulation**

If `true`, contingence simulation starts with HVDC link configured in AC emulation frozen at their previous active set point
defined by the angles at the HVDC extremities in the base case. If a solution is found then the simulator
continues with the HVDC set to AC emulation mode. Otherwise, the contingence simulation fails.

If `false`, contingence simulation allows HVDC lines to immediatly adapt to the new angles.

The default value is `true`

**threadCount**  
The `threadCount` property defines the number of threads used to run an AC sensitivity analysis (multi-threading is currently not 
supported for DC sensitivity analysis).
The parallelization is implemented at the contingency level, so the contingency list is split into `threadCount` chunks
and each chunk is ran by a different thread. 

The thread pool used for getting threads is the one provided by the `ComputationManager` [![Javadoc](https://img.shields.io/badge/-javadoc-blue.svg)](https://javadoc.io/doc/com.powsybl/powsybl-core/latest/com/powsybl/computation/ComputationManager.html)
(see `ComputationManager.getExecutor` method). By default, when using the local computation manager, this is the `ForkJoinPool` common pool which is used.

The default value is 1.

## Configuration file example
See below an extract of a config file that could help:

```yaml
open-sensitivityanalysis-default-parameters:
  debugDir: /path/to/debug/dir
```

At the moment, overriding the parameters by a JSON file is not supported by Open Load Flow.
