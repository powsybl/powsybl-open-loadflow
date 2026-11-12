# Configuration

This page details the configuration parameters available for network reduction.

## Ward reduction parameters

The following table summarizes all parameters defined in `WardReductionParameters` for operating a network reduction with Ward equivalent.

| Parameter | Type | Default value | Description |
|---|---|---|---|
| [`InternalVoltageLevelIds`](#InternalVoltageLevelIds) | `Set<String>` | `Set.of()` | Set of voltage level IDs defining the internal area to be kept. |
| [`EquivalentType`](#EquivalentType) | `WardEquivalentType` | `STANDARD` | The Ward equivalent type to use for network reduction. |
| [`LoadFlowParameters`](#LoadFlowParameters) | `LoadFlowParameters` | `new LoadFlowParameters()` | The load flow parameters used for the creation of the internal `LfNetwork`. |
| [`EquivalentSuffix`](#EquivalentSuffix) | `String` | `-equivalent` | The suffix for naming equivalent objects. |

---

### InternalVoltageLevelIds

Specifies the set of voltage levels in the network that belong to the internal (study) area. All buses and connected components within these voltage levels will be preserved without modification.

### EquivalentType

Defines the mathematical reduction formulation:
- `STANDARD`: All external network elements are reduced into equivalent branches, shunts, and injections on boundary buses without prior conversion.

### LoadFlowParameters

The `LoadFlowParameters` instance passed to configure the internal `LfNetwork` creation and solver behavior when evaluating the initial state and parameters for reduction.

### EquivalentSuffix

The suffix added to the identifier of equivalent VoltageLevel, Bus, Line, ShuntCompensator, Generator created in the network as part of the reduction process.
