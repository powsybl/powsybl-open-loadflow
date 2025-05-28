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

**useWarmStart**

If `true`, contingence simulation use the `WARM_START` voltage initializer. The simulator starts by freezing the AC emulation
HVDC to the active set point defined by the angles at the HVDC extremities. If a solution exists is foudn then the simulator
continues with the HVDC set to AC emulation mode. Otherwise, the contingence simulation fails.

If `false`, contingence simulation use the 'PREVIOUS_VALU' voltage intializer.

The default value is `true`

## Configuration file example
See below an extract of a config file that could help:

```yaml
open-sensitivityanalysis-default-parameters:
  debugDir: /path/to/debug/dir
```

At the moment, overriding the parameters by a JSON file is not supported by Open Load Flow.
