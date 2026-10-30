# Ward Equivalent Reduction

## Network decomposition

To perform a network reduction, the buses of the power grid are partitioned into three distinct sets based on the user-specified internal voltage levels:

- **Internal buses ($i$)**: Buses inside the area of study that are strictly retained with all their connected equipment.
- **Boundary buses ($b$)**: Buses located in the external area that are directly connected to at least one internal bus via a branch.
- **External buses ($e$)**: Buses in the external area that are only connected to other external buses or boundary buses, and will be eliminated.

```
+-------------------+        +--------------------+        +--------------------+
|   Internal Area   | <----> |   Boundary Buses   | <----> |   External Area    |
|      (kept)       |        |    (equivalent)    |        |    (eliminated)    |
+-------------------+        +--------------------+        +--------------------+
```

## Mathematical formulation

The nodal admittance equation of the linear network model can be expressed in block matrix form:

$$
\left(\begin{array}{c}
I_{i}\\
I_{b}\\
I_{e}
\end{array}\right)=\left(\begin{array}{cc}
Y_{ii} & Y_{ib} & 0\\
Y_{bi} & Y_{bb}^{i} + Y_{bb}^{b+e} & Y_{be}\\
0 & Y_{eb} & Y_{ee}
\end{array}\right)\left(\begin{array}{c}
V_{i}\\
V_{b}\\
V_{e}
\end{array}\right)
$$

where:
- $V_i, V_b, V_e$ are the complex voltage vectors at internal, boundary, and external buses.
- $I_i, I_b, I_e$ are the injected current vectors at internal, boundary, and external buses.
- $Y$ is the bus admittance matrix. Note that there are no direct branches between internal and external buses ($Y_{ie} = Y_{ei} = 0$).

## Gaussian elimination of external nodes

From the third row of the matrix equation:

$$
I_e = Y_{eb} V_b + Y_{ee} V_e
$$

Assuming $Y_{ee}$ is invertible:

$$
V_e = Y_{ee}^{-1} (I_e - Y_{eb} V_b)
$$

Substituting $V_e$ into the equation for boundary currents $I_b$:

$$
I_b = Y_{bi} V_i + Y_{bb}^{i} V_b + Y_{bb}^{b+e} V_b + Y_{be} {Y}_{ee}^{-1} (I_e - Y_{eb} V_b)
$$

Rearranging the terms yields the reduced system:

$$
\left(\begin{array}{c}
I_{i}\\
I_{eq}
\end{array}\right)=\left(\begin{array}{cc}
Y_{ii} & Y_{ib}\\
Y_{bi} & Y_{bb}^{i} + Y_{eq}
\end{array}\right)\left(\begin{array}{c}
V_{i}\\
V_{b}
\end{array}\right)
$$

where:
- $Y_{eq} = Y_{bb}^{b+e} - Y_{be} Y_{ee}^{-1} Y_{eb}$
- $I_{eq} = I_b - Y_{be} Y_{ee}^{-1} I_e$

## Equivalents computation

The equivalent $Y_{eq}$ matrix and $I_{eq}$ are converted to equivalent elements in the network:

### Equivalent branches

The off-diagonal elements of $Y_{eq}$ represent equivalent branches between boundary buses $j$ and $k$:

$$
Y_{\text{branch, jk}} = - Y_{eq}(j, k)
$$

### Equivalent shunts

The diagonal elements yield equivalent shunt admittances on boundary bus $j$:

$$
Y_{\text{shunt, j}} = \sum_{k} Y_{eq}(j, k)
$$

### Equivalent injections

Given the boundary voltages $V_b$, the equivalent complex power injection on boundary bus $j$ is computed as:

$$
S_{\text{inj, j}} = P_{\text{inj, j}} + j Q_{\text{inj, j}} = V_{b}(j) I_{eq}(j)^*
$$

## Reduction of the IIDM network

The Ward reduction process applied to an IIDM network comprises:
1. **Creation of equivalent voltage levels**: For each boundary voltage level and bus, a new voltage level (with the suffix `_EQUIV`) and bus is created in the network to isolate the boundary interface.
2. **Reconnection of tie-lines**: The tie-lines connecting internal buses to boundary buses are moved to connect to the new equivalent voltage levels/buses.
3. **Removal of external elements**: All external lines, transformers, voltage levels, and substations are removed.
4. **Addition of equivalent elements**:
   - **Equivalent lines** (`Line`) added between boundary buses
   - **Equivalent shunts** (`ShuntCompensator`) added at boundary buses
   - **Equivalent generators** (`Generator`) added at boundary buses
