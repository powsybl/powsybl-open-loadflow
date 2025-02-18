# External AC Solvers

PowSyBl Open LoadFlow provides out-of-the box two AC Solvers:
- Newton-Raphson
- Newton-Krylov

Other AC solvers can be plugged into PowSyBl Open LoadFlow with the following interfaces that you would need to implement:
- `AcSolver`: the solver itself
- `AcSolverParameters`: any additional parameter that you need specifically for your solver
- `AcSolverFactory`: Responsible for creating `AcSolver` instances and `AcSolverParameters`.
Provide your own implementation of `AcSolverFactory` and make it available to the Java ServiceLoader.
The `getName()` method should provide the plugin name - which can then be used in the
`acSolverType` [Load Flow parameter](../loadflow/parameters.md#specific-parameters)

PowSyBl Open LoadFlow uses the same plugin mechanism internally. For more details you may have a look at:
- `NewtonRaphson` / `NewtonRaphsonParameters` / `NewtonRaphsonFactory`
- `NewtonKrylov` / `NewtonKrylovParameters` / `NewtonKrylovFactory`
