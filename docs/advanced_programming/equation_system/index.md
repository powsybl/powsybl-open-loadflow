# Equation system

All the calculations of PowSyBl Open LoadFlow (AC and DC load flow, sensitivity analysis, security analysis...) solve
systems of equations built with the same generic framework. This section describes that framework: it can be used to
write new equation terms, to write a new solver, or to build and solve a system of equations which has nothing to do
with load flow.

```{toctree}
---
maxdepth: 1
---
concepts.md
lifecycle.md
vectorized.md
tutorial.md
```

## The problem being solved

The framework represents a square system of equations:

$$
f(x) = t
$$

where $x \in \mathbb{R}^n$ is the vector of unknowns (the *state*), $t \in \mathbb{R}^n$ is the vector of targets and
$f : \mathbb{R}^n \rightarrow \mathbb{R}^n$ is a function, each component $f_i$ being the left-hand side of one
equation.

When $f$ is nonlinear, the system is solved with the Newton-Raphson method. Starting from an initial state $x_0$, each
iteration solves a linear system involving the Jacobian matrix $J = \partial f / \partial x$:

$$
J(x_k) \, \Delta x_k = f(x_k) - t
\qquad
x_{k+1} = x_k - \Delta x_k
$$

until the mismatch $f(x_k) - t$ is small enough. When $f$ is linear, $f(x) = A x + c$, a single linear solve
$A x = t - c$ is enough.

The framework does not implement the solving method itself: it provides what such a method needs, which is the
evaluation of $f(x)$, of $J(x)$ and the factorization of $J(x)$, for a system whose structure can change between two
solves.

## Main concepts

The diagram below shows the concepts of the framework and how a solver uses them. They are detailed in
[concepts](concepts.md).

```{raw} html
<likec4-view view-id="equationConcepts"></likec4-view>
```

```{only} latex
The interactive diagram of this section is available in the online documentation.
```

In short:
- an **equation term** is a function of some **variables**, which knows how to compute its value and its partial
  derivatives;
- an **equation** is a sum of terms: it is one component $f_i$ of $f$;
- the **equation system** creates and owns the variables and the equations;
- the **index** assigns a column to each active equation and a row to each variable used by an active equation;
- the **state vector** holds $x$, the **equation vector** $f(x)$, the **target vector** $t$, and the
  **Jacobian matrix** $J(x)$ with its LU decomposition.

The equations can be activated and deactivated, and the terms too: the index, the vectors and the Jacobian matrix
update themselves accordingly, doing as little work as possible. This is what allows, for example, switching a
generator from voltage control to reactive power limit, or simulating a contingency, without rebuilding the system.
This is described in [lifecycle](lifecycle.md).

For performance, equations of the same type defined for every element of a kind (every bus, every branch...) can be
stored in a vectorized form, described in [vectorized equations](vectorized.md).

Finally, the [tutorial](tutorial.md) builds and solves a small nonlinear system step by step, computes a sensitivity
and changes the structure of the system.
