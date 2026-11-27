# Vectorized equations

An equation system made of individual equations and terms is flexible, but each term is an object which evaluates
itself, reading the state vector, one at a time. On large networks, evaluating and deriving millions of term objects
at each Newton-Raphson iteration has a cost. When an equation type is defined for every element of a kind (the active
power balance of every bus, for example), and its terms are of a few kinds evaluated the same way (the flows of every
branch), the equations and terms can be stored in a vectorized form: data are stored in arrays, and all the terms of a
kind are evaluated in a single loop.

## Equation arrays

An **equation array** (`EquationArray`) replaces the equations of one type, one per element of the type's element
type. It is created with `EquationSystem.createEquationArray(type)`, which reads the number of elements from the
element container of the equation system. Each element of the array is an equation:
- all elements are active, except the disabled elements of the container;
- an element is activated or deactivated with `setElementActive(elementNum, active)`;
- the active elements get contiguous columns: `getElementNumToColumn(elementNum)` gives the column of an element;
- `getElement(elementNum)` returns the element as an `Equation`, to which individual terms can be added (terms with a
  [constant part](concepts.md#constant-part-of-a-term) are not supported).

Once an array exists for a type, `EquationSystem.createEquation(elementNum, type)` and
`getEquation(elementNum, type)` return the element of the array.

## Equation term arrays

An **equation term array** (`EquationTermArray`) holds all the terms of one kind of an equation array. Its terms are
attached to elements of a given type, which can differ from the equation's: in a bus power balance, the terms are
branch flows. A term is added with `addTerm(equationElementNum, termElementNum)`. Within a term array, a term element
can only appear once: the flows of a branch on its two buses are therefore two term arrays, one for each side. After
all the terms have been added, `compress()` must be called.

The values and derivatives are computed by an **evaluator** (`EquationTermArray.Evaluator`), indexed by term element
number:
- `eval()` returns the values of all the terms, and `eval(termElementNum)` the value of one;
- `getDerivatives(termElementNum)` lists the variables of a term, each with a local index (`Derivative`);
- `evalDer()` returns one array of derivative values per local index: `evalDer()[localIndex][termElementNum]`;
- `isDisabled(termElementNum)` tells whether a term is initially inactive.

```{warning}
The arrays returned by `evalDer()` are kept by the equation array and read again at each update of the Jacobian
matrix. An evaluator of nonlinear terms must update the values of these same arrays when the state changes, for
example by listening to the state vector, rather than return new arrays.
```

## Example: a chain of springs

A chain of nodes, numbered from 0, is connected by springs of stiffness $k_s$, spring $s$ connecting node $s$ to node
$s + 1$. Node 0 is fixed, and a load $F$ pulls the last node. The unknowns are the positions $u_i$ of the nodes, and
the equations are the force balance of each node, except node 0 whose position is fixed:

$$
\sum_{s \text{ connected to } i} k_s \, (u_i - u_{other(s)}) = F_i
\qquad
u_0 = 0
$$

The force balances are an equation array of bus elements, the nodes, and the spring forces are two term arrays of
branch elements, the springs: one for the force on the first node of the spring, one for the force on the second.

The quantity types:

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [chain-quantities-start]
:end-before: // [chain-quantities-end]
:dedent: 4
```

The evaluator of the spring forces. The terms are linear: the derivatives are constant and computed once.

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [spring-evaluator-start]
:end-before: // [spring-evaluator-end]
:dedent: 4
```

The equation array needs an element container giving the number of nodes, and the element objects to check whether
they are disabled. In this example, a single mocked element, never disabled, is enough:

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [chain-container-start]
:end-before: // [chain-container-end]
:dedent: 4
```

Building the system. The force balance of node 0 is deactivated and replaced by an individual equation fixing its
position:

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [chain-build-start]
:end-before: // [chain-build-end]
:dedent: 8
```

The system is linear, without constant parts: the positions are obtained by a single solve, with the targets as
right-hand side. With stiffnesses 1, 2 and 4 and a load of 4, each spring carries the load and the positions are 0,
4, 6 and 7.

The Jacobian matrix is created with `invalidateValuesWhenStateUpdate` set to `false`: the system is linear, so the
matrix does not have to be updated when the positions are written to the state vector.

```{literalinclude} ../../../src/test/java/com/powsybl/openloadflow/equations/EquationSystemTutorialTest.java
:language: java
:start-after: // [chain-solve-start]
:end-before: // [chain-solve-end]
:dedent: 8
```
