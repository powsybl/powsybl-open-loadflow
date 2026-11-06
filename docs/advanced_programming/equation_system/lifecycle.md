# Lifecycle and incremental updates

An equation system is usually built once and solved many times: in a load flow, after each outer loop iteration
(a generator reaching a reactive power limit, a transformer tap change...), and in a security analysis, after each
contingency. Between two solves, the state, the targets and the structure of the system change. The framework tracks
these changes and updates the index, the vectors and the Jacobian matrix lazily, doing as little work as possible.

## Changing the system

The structure of the system is changed by:
- activating or deactivating an equation (`Equation.setActive()`), or an element of an
  [equation array](vectorized.md) (`EquationArray.setElementActive()`);
- activating or deactivating a term (`EquationTerm.setActive()`);
- creating an equation, or removing one (`EquationSystem.removeEquation()`).

The values of the terms also depend on constants (a branch impedance, for example). When such a constant changes (a
transformer tap change, for example), the code changing it must call `EquationSystem.notifyEquationTermConstantsChange()`
so that the Jacobian matrix is updated.

Deactivating is preferred to removing: it is cheaper, can be undone, and keeps the matrix elements of the deactivated
terms so that reactivating them does not change the structure of the Jacobian matrix. For the same reason, the usual
practice is to create upfront all the terms an equation may need, some of them inactive, rather than adding terms to
an equation of a system which has already been solved.

## What is recomputed

Each change sets a status on the objects depending on it. Nothing is recomputed until the next use of the object,
so that several changes in a row cost a single update.

| Change | Index | Equation vector | Jacobian matrix |
|---|---|---|---|
| State update (`StateVector.set()`, `minus()`) | unchanged | values recomputed | values recomputed, incremental LU update (unless created with `invalidateValuesWhenStateUpdate` set to `false`) |
| Term activated or deactivated, same variables | unchanged | unchanged until the next state update | values recomputed, full LU update |
| Term constants changed (`notifyEquationTermConstantsChange()`) | unchanged | unchanged until the next state update | values recomputed, incremental LU update |
| Equation activated, deactivated, created or removed | columns recomputed | array rebuilt | matrix rebuilt, new LU decomposition |
| A variable is used for the first time, or not anymore | rows recomputed | unchanged | matrix rebuilt, new LU decomposition |

### Levels of Jacobian matrix update

The Jacobian matrix is updated at three levels, from the cheapest to the most expensive:

| Level | Triggered by | Matrix | LU decomposition |
|---|---|---|---|
| Incremental LU update | state update, term constants changed | same matrix and same non-zero elements, values overwritten in place | factorized again, reusing the analysis and the pivot order of the previous factorization |
| Full LU update | term activated or deactivated | same as above, but some values may have become zero or non-zero | factorized again, choosing the pivots again, but keeping the structural analysis |
| New LU decomposition | equation or variable added or removed, order of the equations changed | rebuilt from scratch, with a new size and new non-zero elements | discarded, a new decomposition (analysis, ordering and factorization) is computed at the next solve |

Reusing the pivot order is only safe when the values have not changed too much: activating or deactivating terms can
turn a good pivot into a zero or a very small value, hence the full update in that case. If an incremental update
fails, a full update is done instead.

The difference between the levels matters with a sparse matrix storage, the one used by the load flow. A sparse LU
decomposition can only be updated if the number of non-zero elements of the matrix has not changed: this is why the
matrix element of a deactivated term is kept, with a zero value, rather than removed. With a dense matrix storage, as
in the [tutorial](tutorial.md), incremental and full updates are the same: the decomposition is discarded and
computed again at the next solve.

```{note}
Activating or deactivating a term, or changing its constants, does not invalidate the values of the equation vector: they are recomputed on the
next state update. A solver changing terms between two solves usually updates the state (for example by
initializing it again) before reading $f(x)$.
```

When the rows change, the state vector does not follow: its values are indexed by the old rows. After a change of
the variables, the solver must create the state vector again, reading each value from its variable
(`getIndex().getSortedVariablesToFind()` and `variable.getRow()`). When only equations change and the variables stay
the same, as in the second part of the [tutorial](tutorial.md), the state vector can be kept.

## One Newton-Raphson iteration

The diagram below shows one Newton-Raphson iteration. The solver only reads $f(x)$, solves with the Jacobian matrix
and updates the state: the evaluation and the derivation of the equations are triggered by the state update.

```{raw} html
<likec4-view view-id="newtonIteration"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

## A structure change

The diagram below shows the deactivation of an equation. The equation system notifies the index, which notifies the
vectors and the Jacobian matrix: they will be rebuilt on their next use.

```{raw} html
<likec4-view view-id="structureChange"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

## Listeners

Three listener interfaces allow other objects to follow the changes:
- `EquationSystemListener`, registered on the equation system, receives the raw events: an equation or an equation
  array element activated or deactivated, a term added, activated or deactivated, a change of the term constants;
- `EquationSystemIndexListener`, registered on the index (`getIndex().addListener()`), receives the consequences on
  the system to solve: an equation or a variable added to or removed from the system, a term change, a change of the
  order of the equations, a change of the term constants;
- `StateVectorListener`, registered on the state vector, is notified of each state update.

The equation vector, the target vector and the Jacobian matrix are themselves listeners. They implement
`AutoCloseable`: close them (for example with a try-with-resources statement) when they are no longer used, to
unregister them from the equation system.

## Post-processing a created equation system

`EquationSystemPostProcessor` is a plug-in, found with the Java `ServiceLoader`, whose `onCreate()` method is called
with each equation system created by the AC and DC load flow, after all the equations and terms have been created.
It can be used to add, replace or deactivate terms or equations, for example to model a device that is not supported
natively.

Like for the other plug-ins of PowSyBl Open LoadFlow, the implementation is registered with the `@AutoService`
annotation:

```java
@AutoService(EquationSystemPostProcessor.class)
public class MyEquationSystemPostProcessor implements EquationSystemPostProcessor {

    @Override
    public void onCreate(EquationSystem<?, ?> equationSystem) {
        // add, replace or deactivate terms or equations
    }
}
```

A code building its own equation system has to call the post-processors itself if it wants to support them:

```java
EquationSystemPostProcessor.findAll().forEach(pp -> pp.onCreate(equationSystem));
```
