# Tutorial: solving a small nonlinear system

This tutorial builds and solves a small nonlinear system with the equation system framework, then computes a
sensitivity and changes the structure of the system. The complete code is the test class
`EquationSystemTutorialTest` of PowSyBl Open LoadFlow, from which the snippets below are extracted. It can be run with:

```bash
./mvnw test -Dtest=EquationSystemTutorialTest
```

## The problem

We look for the intersection of a circle of radius 2 and a hyperbola:

$$
\begin{cases}
x^2 + y^2 = 4 \\
x \, y = 1
\end{cases}
$$

The system has four solutions; starting from $(2, 0.5)$, Newton-Raphson converges to
$x = \sqrt{2 + \sqrt{3}} \approx 1.932$, $y = 1 / x \approx 0.518$. The Jacobian matrix is:

$$
J = \begin{pmatrix} 2x & 2y \\ y & x \end{pmatrix}
$$

## Step 1: quantity types

The unknowns are the two coordinates of a point, and there are two equations, plus a third one used in
[step 7](#step-7-changing-the-structure). As explained in [concepts](concepts.md#elements), variables and equations
are attached to elements: the point is element 0, of type bus.

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [quantities-start]
:end-before: // [quantities-end]
:dedent: 4
```

## Step 2: equation terms

The circle equation is the sum of two terms $x^2$ and $y^2$, and the hyperbola equation is a single term $x y$. Two
term classes are needed: a square and a product. Both read the variable values from the state vector `sv`, at the
variable rows.

The square term also implements `calculateSensi`, used in [step 6](#step-6-sensitivity):

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [square-term-start]
:end-before: // [square-term-end]
:dedent: 4
```

The product term depends on two variables, and returns the derivative with respect to the one requested:

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [product-term-start]
:end-before: // [product-term-end]
:dedent: 4
```

## Step 3: building the system

The equation system needs an element container. This system uses neither equation arrays nor row and column names,
so a container without elements is enough:

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [container-start]
:end-before: // [container-end]
:dedent: 4
```

The variables are requested from the equation system, then the equations are created and their terms added:

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [build-start]
:end-before: // [build-end]
:dedent: 8
```

`equationSystem.writeToString()` now returns:

```text
circle0 = square(x0) + square(y0)
hyperbola0 = product(x0, y0)
```

## Step 4: initial state

Querying the index assigns the rows of the variables: the state vector is created with one value per row, each value
being written at the row of its variable.

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [state-start]
:end-before: // [state-end]
:dedent: 8
```

## Step 5: solving with Newton-Raphson

The targets are indexed by equation column. Since columns can change with the structure of the system, the target
array is built from the active equations and a target per equation type:

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [targets-start]
:end-before: // [targets-end]
:dedent: 4
```

The Newton-Raphson loop computes the mismatch $f(x) - t$, solves $J \, \Delta x = f(x) - t$ and updates the state.
The Jacobian matrix is stored transposed (see [concepts](concepts.md#jacobian-matrix)), hence `solveTransposed`.
The loop never asks for the equations to be evaluated or derived: updating the state invalidates the equation vector
and the Jacobian matrix, which are recomputed when used.

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [newton-start]
:end-before: // [newton-end]
:dedent: 4
```

The equation vector and the Jacobian matrix are created on the equation system, here with a dense matrix storage,
and closed after use. The solve converges in 4 iterations.

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [solve-start]
:end-before: // [solve-end]
:dedent: 8
```

## Step 6: sensitivity

How does the solution move when the circle target $r^2$ changes? Differentiating $f(x) = t$ gives
$J \, dx = dt$: the variation of the state for a unit increase of the circle target is obtained by solving with the
Jacobian matrix at the solution, with a right-hand side equal to 1 at the column of the circle equation. Several
right-hand sides can be solved at once, one per column of a `DenseMatrix`.

The variation of any term is then given by its `calculateSensi` method: here, the variation of $x^2$, which is
$2 x \, dx$.

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [sensi-start]
:end-before: // [sensi-end]
:dedent: 12
```

## Step 7: changing the structure

The hyperbola equation is now replaced by $x = 1.5$: it is deactivated, and a new equation is created with the term
returned by `Variable.createTerm()`. The new solution is $x = 1.5$, $y = \sqrt{4 - 1.5^2} \approx 1.323$.

The structure change is handled by the framework: the index assigns the columns again, and the equation vector and
the Jacobian matrix are rebuilt on their next use. Since the columns may have changed, the targets are built again.
The variables are the same, so their rows and the state vector are kept, and the solve starts from the previous
solution.

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [restructure-start]
:end-before: // [restructure-end]
:dedent: 12
```

## Going further

- The [vectorized equations](vectorized.md) page solves a linear system with equation arrays.
- The [lifecycle](lifecycle.md) page details what each change of the system triggers.
