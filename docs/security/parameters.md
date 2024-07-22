# Configuration

To use PowSyBl OpenLoadFlow for all security analyses, you have to configure the `security-analysis` module in your configuration file:
```yaml
security-analysis:
  default-impl-name: OpenLoadFlow
```

## Generic parameters

Generic parameters are documented in [PowSyBl Core](inv:powsyblcore:*:*#simulation/security/configuration)

The next section details the parameters that are specific to PowSyBl Open LoadFLow.

## Specific parameters

**contingencyPropagation**  
The `contingencyPropagation` property:
- when set to `false`, Security Analysis will simulate the outage of only the equipment(s) defined in the contingency.
- when set to `true` (default) Security Analysis will determine by topological search the switches with type circuit breakers
(i.e. capable of opening fault currents) that must be opened to isolate the fault. Depending on the network structure,
this could lead to more equipments to be simulated as tripped, because disconnectors and load break switches
(i.e., not capable of opening fault currents) are not considered.

The default value is `true`.

**createResultExtension**  
The `createResultExtension` property defines whether open-loadflow specific results extensions should be created
in the security analysis results. Today the available extensions provide information about branches and three-windings
transformers voltages (magnitude and angle):
- `OlfBranchResult` [![Javadoc](https://img.shields.io/badge/-javadoc-blue.svg)](https://javadoc.io/doc/com.powsybl/powsybl-open-loadflow/latest/com/powsybl/openloadflow/network/impl/OlfBranchResult.html)
- `OlfThreeWindingsTransformerResult` [![Javadoc](https://img.shields.io/badge/-javadoc-blue.svg)](https://javadoc.io/doc/com.powsybl/powsybl-open-loadflow/latest/com/powsybl/openloadflow/network/impl/OlfThreeWindingsTransformerResult.html)

The default value is `false`.


## Configuration file example
See below an extract of a config file that could help:

```yaml
open-security-analysis-default-parameters:
  contingencyPropagation: true
  createResultExtension: false
```

At the moment, overriding the parameters by a JSON file is not supported by OpenLoadFlow.