# Configuration

This page details the configuration parameters available for network reduction using the Ward equivalent method (`WardReductionParameters`).

## Ward reduction parameters

The following table summarizes all parameters defined in `WardReductionParameters`:

| Parameter | Type | Default value | Description |
|---|---|---|---|
| [`internalVoltageLevelIds`](#internalvoltagelevelids) | `Set<String>` | `Set.of()` | Set of voltage level IDs defining the internal area to be kept. |
| [`equivalentType`](#equivalenttype) | `WardEquivalentType` | `STANDARD` | The Ward equivalent formulation type to use for network reduction. |
| [`loadFlowParameters`](#loadflowparameters) | `LoadFlowParameters` | `new LoadFlowParameters()` | The load flow parameters used for the creation and modeling of the internal `LfNetwork`. |
| [`matrixFactory`](#matrixfactory) | `MatrixFactory` | `new SparseMatrixFactory()` | The factory used to instantiate matrices for linear algebraic operations. |

---

### internalVoltageLevelIds

Specifies the set of voltage levels in the network that belong to the internal (study) area. All buses and connected components within these voltage levels will be preserved without modification.

- **Type**: `Set<String>`
- **Default value**: empty set (`Set.of()`)

### equivalentType

Defines the mathematical reduction formulation:
- `STANDARD`: All external network elements are reduced into equivalent branches, shunts, and injections on boundary buses without prior conversion.

- **Type**: `WardEquivalentType`
- **Default value**: `WardEquivalentType.STANDARD`

### loadFlowParameters

The `LoadFlowParameters` instance passed to configure the internal `LfNetwork` creation and solver behavior when evaluating the initial state and parameters for reduction.

- **Type**: `LoadFlowParameters`
- **Default value**: default `LoadFlowParameters` instance

### matrixFactory

Specifies the matrix factory to use for sparse matrix allocation and calculations during Gaussian elimination.

- **Type**: `MatrixFactory`
- **Default value**: `SparseMatrixFactory`
