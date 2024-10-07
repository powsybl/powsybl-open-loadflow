# Modeling and equations

(work in progress)

## Active power slack distribution and Operator Strategies Active Power relative actions
When slack distribution is enabled in the load flow options, then slack distribution is performed in all network 
simulations of the security analysis, that is:
- in the N ("pre-contingency") simulation, resulting in the creation of the pre-contingency state.
- in the N-1 or N-k ("post-contingency") simulation, performed by applying contingencies on the pre-contingency state,
resulting in the creation of the post-contingency state.
- in the Operator Strategies ("curative") simulation, performed by applying actions on the post-contingency state,
resulting in the creation of the curative state.

Actions defined in Operators Strategies and involving active power changes (e.g. Load Actions, Generator Actions) can be defined as relative.
PowSyBl Open Load Flow considers that the change is relative to the post-contingency state.

For example:
- slack distribution is performed on generators,
- `g1` generator participates in slack distribution,
- An Operator Strategy is defined, where in the occurrence of the `ctg1` contingency `g1` active power output is increased by +20 MW. 

Then the following would occur:
- In the N "pre-contingency" state simulation, the generator `g1` solves to a new $initialTargetP(g1, N) = targetP(g1) + distributedSlack(g1, N)$, where:
  - $targetP(g1)$ is `g1` active power setpoint.
  - $distributedSlack(g1, N)$ is `g1` contribution to distributed slack for the balancing of the N state.
- In the N-1 "post-contingency" state simulation of contingency `ctg1`, the generator `g1` solves to a new $initialTargetP(g1, ctg1) = initialTargetP(g1, N) + distributedSlack(g1, cgt1)$, where:
  - $initialTargetP(g1, N)$ is `g1` solved value from above.
  - $distributedSlack(g1, cgt1)$ is `g1` contribution to distributed slack for the balancing of the `cgt1` "post-contingency" state.
- Finally, in the "curative" state of contingency `ctg1`, the generator `g1` will solve to $initialTargetP(g1, ctg1) + 20 MW + distributedSlack(g1, curative1)$,
where $distributedSlack(g1, curative1)$ is `g1` contribution to distributed slack for the balancing of the "curative" state.

## Security analysis on multi components networks

A temporary implementation for multi components analysis has been done to provide a minimal support of this feature.
The approach is to execute a security analysis on each separated component and merge the results after all computations are done.
However limitations remain until the core security analysis API evolves:

- Convergence status of secondary components are not reported in precontingency status 
- Convergence status of contingencies that affect multiple components are not reported (only the first run component's status)
- Actions that change the topology of the network by connecting components not initially connected (using TerminalConnectionAction) are not supported
