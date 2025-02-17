# Advanced Programming Guide

This section describes advanced programming topics.

These topics apply only to Open LoadFlow in Java.
In particular, this section does not apply to PyPowSyBl.

PowSyBl at large is build with a modular architecture.
PowSyBl Open LoadFlow is no different and implements the same principles.

Via this modular design some PowSyBl Open LoadFlow features can be modified or enhanced using plug-ins, without
requiring to build a customized/forked version of PowSyBl Open LoadFlow.

This section details PowSyBl Open LoadFlow plug-in capabilities and how to use them.

```{toctree}
---
maxdepth: 2
hidden: true
---
lfnetwork_postprocessor.md
external_ac_solver.md
equation_system_postprocessor.md
outerloop_configuration.md
contingency_active_power_loss.md
```
