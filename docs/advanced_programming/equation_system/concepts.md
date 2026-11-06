# Concepts

This page describes the concepts of the equation system framework and how they relate to the system $f(x) = t$. The
Java type of each concept is given in parentheses.

## Elements

Every variable, equation and equation term is attached to an **element**, identified by an element type and an
element number. The element types are the ones of the network model (bus, branch, shunt compensator, HVDC line...):
in a load flow, the voltage of bus number 3 is a variable of element (bus, 3), and its active power balance an
equation of the same element.

The framework itself does not need the elements to exist: for a system that is not about a network, pick any element
type and number the objects of the problem. In the [tutorial](tutorial.md), the unknown point $(x, y)$ is element 0
of type bus.

The element objects are only needed in a few cases. The equation system is created with an element container
(`LfElementContainer`) which gives the number of elements of each type and the element objects. It is used to size
[vectorized equations](vectorized.md), to know which elements are disabled, and to name the rows and columns
(`EquationSystem.getRowNames()` and `getColumnNames()`). When none of these is used, a container returning no
elements is enough.

## Quantity types

Variables and equations are typed by two enums implementing `Quantity`, one for the variable types and one for the
equation types. Each value gives:
- a symbol, used to print the system: variable `v` of element 3 is printed `v3`;
- the element type it applies to.

For example, the AC load flow uses voltage magnitude and angle variable types, and active and reactive power balance
equation types. The two enums are the type parameters `V` and `E` of all the other classes of the framework.

## Variables

A **variable** (`Variable`) is one component of $x$. It is identified by its type and element number, and is created
on first request by `EquationSystem.getVariable(elementNum, type)`: two requests with the same identifier return the
same object.

A variable has a **row**, its index in the state vector, assigned by the [index](#index). The row is $-1$ as long as
no active equation uses the variable. Rows must therefore always be read from the variable (`getRow()`), never cached
by the caller.

## Equation terms

An **equation term** (`EquationTerm`) is a function of some variables. It is the building block of the equations,
and the place where the actual mathematics is written. A term:
- lists the variables it depends on (`getVariables()`);
- computes its value from the state vector (`eval()`);
- computes its partial derivative with respect to one of its variables (`der(variable)`);
- can separate a constant part (`hasRhs()`, `rhs()`, see [below](#constant-part-of-a-term));
- can compute its variation for a given variation of the state (`calculateSensi(dx, column)`), which is
  $\sum_v \frac{\partial term}{\partial v} \, dx_v$, used for sensitivity analysis;
- can be activated and deactivated (`setActive()`): an inactive term counts as zero;
- is attached to an element (`getElementType()`, `getElementNum()`), usually the one of its main variable or the
  network element it models (a branch for a branch flow).

To write a new term, extend `AbstractEquationTerm`, or `AbstractNamedEquationTerm` which prints the term as
`name(variables)`. The state vector is injected when the term is added to an equation, and is available in the
protected field `sv`: the value of variable `v` is `sv.get(v.getRow())`. See the `SquareTerm` and `ProductTerm`
classes of the [tutorial](tutorial.md) for complete examples.

Some terms are provided:
- `Variable.createTerm()` creates a term whose value is the variable itself;
- `term.multiply(scalar)` multiplies a term by a constant, or by a value computed at each evaluation when given a
  `DoubleSupplier`, and `term.minus()` changes its sign.

### Constant part of a term

A term can have a part which does not depend on the state. For example, the term $k (u - c)$, where $u$ is a variable
and $k$, $c$ constants, has the constant part $-k c$. Such a term returns `true` from `hasRhs()`, and its constant part
from `rhs()`, while `eval()` still returns the whole value $k (u - c)$.

The constant part is moved to the right-hand side: the equation vector holds the values without their constant part
($k u$), and the target vector the targets minus the constant parts ($t + k c$). The mismatch is unchanged, but for a
linear system the equation vector is then exactly $A x$, so that $x$ is obtained by a single solve with the target
vector as right-hand side. The DC load flow relies on this for the phase shifts of the transformers.

## Equations

An **equation** (`Equation`) is one component $f_i$ of $f$: the sum of its active terms. It is identified by its type
and element number, and is created by `EquationSystem.createEquation(elementNum, type)`, which returns the existing
equation if there is one. Terms are added with `addTerm()`; a term can belong to one equation only.

An equation can be activated and deactivated (`setActive()`): only active equations are part of the system to solve.
An active equation has a **column**, its index in the equation and target vectors, assigned by the
[index](#index).

The equation system can be printed with `EquationSystem.writeToString()`, one active equation per line:

```text
circle0 = square(x0) + square(y0)
hyperbola0 = product(x0, y0)
```

## Equation system

The **equation system** (`EquationSystem`) creates and owns the variables, the equations and the
[equation arrays](vectorized.md). It also gives access to:
- the equations and terms attached to an element (`getEquations(elementType, elementNum)`,
  `getEquationTerms(elementType, elementNum)`), used to update the system when an element changes;
- the state vector (`getStateVector()`) and the index (`getIndex()`);
- listeners notified of every change of the equations and terms (see [lifecycle](lifecycle.md)).

## Index

The **index** (`EquationSystemIndex`) maps the equation system to vectors and matrices:
- the active equations, sorted by element number then type, are numbered from 0: this is their column;
- the variables used by at least one active term of an active equation, sorted by element number then type, are
  numbered from 0: this is their row.

The index is updated lazily, on the first query after a change (`getColumnCount()`, `getRowCount()`,
`getSortedSingleEquationsToSolve()`, `getSortedVariablesToFind()`...). The system to solve must be square: the
Jacobian matrix checks that the number of rows equals the number of columns.

## Vectors

The **state vector** (`StateVector`) holds $x$, indexed by row. It is a plain array that the solver creates with
`getIndex().getRowCount()` values, fills with initial values and sets with `set()`. Each update (`set()`, `minus()`)
notifies the objects depending on the state.

The **equation vector** (`EquationVector`) holds $f(x)$, indexed by column, without the constant parts of the terms.
It is recomputed lazily, on the first `getArray()` after a state update.

The **target vector** holds $t$, indexed by column, minus the constant parts of the terms. The provided implementation
(`TargetVector`) reads the targets from the network elements, through an initializer, and is refreshed when the
network changes. Outside of a network, a plain array indexed by column is enough: build it from the active equations
(`getIndex().getSortedSingleEquationsToSolve()` and `equation.getColumn()`).

## Jacobian matrix

The **Jacobian matrix** (`JacobianMatrix`) holds the partial derivatives of the active equations, computed from the
terms (`der()`), and its LU decomposition. It is created with a `MatrixFactory`, which selects a dense or a sparse
storage. Like the equation vector, it is updated lazily on the first use after a change.

By default, the derivatives are recomputed after each state update. For a linear system, the derivatives do not
depend on the state: create the matrix with `invalidateValuesWhenStateUpdate` set to `false` (third constructor
argument) to keep the matrix and its LU decomposition when the state changes. The DC load flow does so.

```{warning}
The matrix is stored transposed: the rows are the variables and the columns are the equations, so that the element
at row $j$ and column $i$ is $\partial f_i / \partial x_j$. To solve $J \, \Delta x = b$, call
`solveTransposed(b)`: `b` is indexed by column (equation) and is replaced by $\Delta x$, indexed by row (variable).
`solve(b)` solves the transposed system $J^T y = b$, which is useful to compute the sensitivities of one function
to many targets.
```

## A small example

Consider two variables $a$ (element 0) and $b$ (element 1), and two equations:
- equation $E_0$ of element 0: $a^2 + a b = 3$, with the two terms $a^2$ and $a b$;
- equation $E_1$ of element 1: $b = 1$, with the single term $b$.

Equations are sorted by element number, so $E_0$ is column 0 and $E_1$ column 1. Variables are sorted the same way,
so $a$ is row 0 and $b$ row 1. Term $a b$ depends on both variables: it contributes to the derivative of $E_0$ with
respect to $a$ and to $b$. The stored matrix is:

|              | $E_0$ (column 0) | $E_1$ (column 1) |
|--------------|------------------|------------------|
| $a$ (row 0)  | $2a + b$         | $0$              |
| $b$ (row 1)  | $a$              | $1$              |

If $E_1$ is deactivated, $b$ is still used by $E_0$ and keeps its row, but the system has two variables for one
equation and can no longer be solved. Deactivating instead the term $a b$ in $E_0$ keeps the system square: $b$ is
still used by $E_1$, and the matrix element at row $b$ and column $E_0$ is kept, with a zero value.
