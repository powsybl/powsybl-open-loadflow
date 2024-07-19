# Modelling and equations

(work in progress)

## Active power slack distribution and Operator Strategies Active Power relative actions
When slack distribution is enabled in the load flow options, then slack distribution is performed in all network 
simulations of the security analysis, that is:
- in the N ("pre-contingency") simulation, resulting in the creation of the pre-contingency state
- in the N-1 or N-k ("post-contingency") simulation, performed by applying contingencies on the pre-contingency state,
resulting in the creation of the post-contingency state
- in the Operator Strategies ("curative") simulation, performed by applying actions on the post-contingency state,
resulting in the creation of the curative state

Actions defined in Operators Strategies and involving active power changes (e.g. Load Actions, Generator Actions) can be defined as relative.
PowSyBl OpenLoadFlow considers that the change is relative to the post-contingency state.
