# LODF matrix (DC)

Open Load Flow computes the LODF (Line Outage Distribution Factor) matrix in DC, with the same building blocks as the
[Woodbury based fast DC security analysis](../security/parameters.md#dcfastmode).

## Definition

The LODF of a monitored branch $l$ for the outage of a branch $k$ is the change of the active power flow of $l$ caused
by the outage of $k$, divided by the pre-outage active power flow of $k$:

$$ \text{LODF}(l, k) = \frac{\text{PTDF}(l, k)}{1 - \text{PTDF}(k, k)}, \qquad \text{LODF}(k, k) = -1 $$

where $\text{PTDF}(l, k)$ is the flow of $l$ for a $+1/-1$ injection at the terminals of $k$. No load flow is needed: the
factors only depend on the topology and the impedances. The PTDF come from one resolution of the DC system per outaged
branch, reusing the LU decomposition of the Jacobian matrix.

## Sparse result

A dense LODF matrix of a large network does not fit in memory, but most of its factors are tiny. Only the significant
factors are stored, in a sparse matrix (compressed sparse column format), as set by `LodfParameters`:

- `threshold` (default `0.05`): minimum $\lvert\text{LODF}\rvert$ kept. With `0.05`, a monitored branch is kept for an
  outage if it receives at least 5% of the flow lost by the outaged branch. `0` keeps everything, exact zeros included.
- `maxFactorsPerOutage` (default unlimited): for each outage, only the largest factors above the threshold are kept.
  The $-1$ of the outaged branch itself is always kept and not counted.
- `outageBatchSize` (default `500`): number of outages solved together. It does not change the result, only the memory:
  the dense states matrix takes equation count x batch size x 8 bytes, and is capped to 2 GB.

## Conventions

| situation | result |
|---|---|
| $\lvert\text{LODF}\rvert \ge$ threshold | stored |
| $\lvert\text{LODF}\rvert <$ threshold, or not among the `maxFactorsPerOutage` largest of its outage | not stored (reads as 0) |
| monitored branch is the outaged branch | $-1$ |
| monitored branch not connected on both sides, or not in the main synchronous component | empty row |
| outaged branch not connected on both sides, or not in the main synchronous component | empty column, listed in the undefined outages |
| outage breaking the connectivity, or outage of a zero impedance branch ($1 - \text{PTDF}(k, k) < 10^{-6}$) | empty column, listed in the undefined outages |

## Usage

```java
LodfResult result = LodfAnalysis.run(network, monitoredBranchIds, outagedBranchIds, new LoadFlowParameters()); // defaults
LodfParameters lodfParameters = new LodfParameters().setThreshold(0.01).setMaxFactorsPerOutage(20);
result = LodfAnalysis.run(network, monitoredBranchIds, outagedBranchIds, lodfParameters, new LoadFlowParameters(), reportNode);
SparseMatrix lodfMatrix = result.lodfMatrix(); // rows: monitored branches, columns: outaged branches
List<Integer> undefinedOutageIndices = result.undefinedOutageIndices();
```

## Modeling

The computation is done on the main synchronous component. Phase control is off (the phase shifters keep their current
tap), HVDC lines are fixed injections, and zero impedance branches are replaced by minimal impedance ones.
