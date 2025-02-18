# Contingency Active Power Loss Distribution

This plugin applies only to Security Analysis.

The way active power imbalances created by contingencies (disconnection of loads, of generators, ...) are compensated
can be tailored to specific needs through a plugin.

The plugin to be used can be specified in the `contingencyActivePowerLossDistribution`
security analysis [parameter](../security/parameters.md#specific-parameters).

PowSyBl Open LoadFlow provides only a `Default` plugin, but other plugins can be added.

To add another plugin, you will need to code (in Java) an implementation of the `ContingencyActivePowerLossDistribution`
interface and make this implementation available to the Java ServiceLoader (e.g. using Google's AutoService):
- the `getName()` method should provide the plugin name - which can then be used in the `contingencyActivePowerLossDistribution` security analysis parameter
- the `run(...)` method will be called by the security analysis engine for each contingency and should provide the logic.
  This method has access to:
    - the network
    - the contingency in open-loadflow representation, including among others information about disconnected network elements, and how much active power has been lost.
    - the contingency definition
    - the security analysis parameters
    - the contingency load flow parameters overrides if any (See [Contingency Load Flow Parameters](../security/parameters.md#contingency-load-flow-parameters))
    - the contingency report node - so that the plugin may add any report message needed.
