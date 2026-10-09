# Outer loops detailed description

## Active Power Distribution

Active power distribution aims at reducing active power mismatch at the slack bus to get a balanced network.

![Active power distribution](active-power-distribution.svg)

The way the active power distribution is performed is simulated by answering these three questions:
- **Where is the imbalance ?**  This is determined by the slack bus choice (see [parameter `slackBusSelectionMode`](../loadflow/parameters.md#slackbusselectionmode)). The imbalance can also be shared
by multiple slack buses (see [parameter `maxSlackBusCount`](../loadflow/parameters.md#maxslackbuscount))
- **Who participates to reduce the imbalance ?** These can be either generators or loads depending on the balance type and the network description (active power control extension
can be used to specify which elements participate to the active power distribution)
- **How the imbalance is distributed ?** Some of the elements can contribute more than other (depending on their technology). Several ways of describing participation coefficient can be configured or specified (see [parameter `balanceType`](inv:powsyblcore:*:*:#param-lf-balance-type))

## Area Interchange Control

Area Interchange Control consists in having the Load Flow finding a solution where area interchanges are solved to match the input target interchange values. It is supported for both AC and DC Load Flow computations.

The area interchange control feature is optional, can be activated via the [parameter `areaInterchangeControl`](../loadflow/parameters.md#areainterchangecontrol)
and is performed by an outer loop.

Area Interchange Control is performed using an outer loop, similar in principle to the traditional `SlackDistribution` outer loop.
However unlike the `SlackDistribution` outer loop which distributes imbalance over the entire synchronous component (island),
the Area Interchange Control outer loop performs an active power distribution over areas
(filtered on areas having their type matching the configured [parameter `areaInterchangeControlAreaType`](../loadflow/parameters.md#areainterchangecontrolareatype)),
in order to have all areas' active power interchanges matching their target interchanges.

The Area Interchange Control outer loop can handle networks where part (or even all) of the buses are not in an area.
For networks that have no areas at all, the behaviour is the same as with the distributed slack outer loop - in such case
internally the Area Interchange Control outer loop just triggers the Slack Distribution outer loop logic.

Just like other outer loops, the Area Interchange Control outer loop checks whether area imbalance must be distributed:
* If no, the outer loop is stable
* If yes, the outer loop is unstable and a new Newton-Raphson is triggered

### Area Interchange Control - algorithm description

The active power is distributed separately on injections (as configured in the [parameter `balanceType`](inv:powsyblcore:*:*:#param-lf-balance-type)) of each area
to compensate the area "total mismatch" that is given by:

$$
Area Total Mismatch = Interchange - Interchange Target + Slack Injection
$$

Where:<br>
* "Interchange" is the sum of the power flows at the boundaries of the area (load sign convention i.e. counted positive for imports).<br>
* "Interchange Target" is the interchange target parameter of the area.<br>
* "Slack Injection" is the active power mismatch of the slack bus(es) present in the area (see [Slack bus mismatch attribution](#slack-bus-mismatch-attribution)).

The outer loop iterates until the absolute value of this mismatch is below the configured [parameter `areaInterchangePMaxMismatch`](../loadflow/parameters.md#areainterchangepmaxmismatch) for all areas.

When it is the case, "interchange only" mismatch is computed for all areas:

$$
Interchange Mismatch = Interchange - Interchange Target
$$

If the absolute value of this mismatch is below the [parameter `areaInterchangePMaxMismatch`](../loadflow/parameters.md#areainterchangepmaxmismatch) for all areas and the absolute value of slack bus active power mismatch is below the [parameter `slackBusPMaxMismatch`](../loadflow/parameters.md#slackbuspmaxmismatch), then the outer loop declares a stable status, meaning that the interchanges are correct and the slack bus active power is distributed.

If not, the remaining slack bus mismatch is first distributed over the buses that have no area.

If some slack bus mismatch still remains, it is distributed over all the areas (see [Remaining slack bus mismatch distribution](#remaining-slack-bus-mismatch-distribution)).

### Areas validation
There are some cases where areas are considered invalid and will not be considered for the area interchange control:
- Areas without interchange target
- Areas without boundaries
- Areas that have boundaries in multiple synchronous/connected components. If all the boundaries are in the same component but some buses are in different components, only the part in the component of the boundaries will be considered.

In such cases the involved areas are not considered in the Area Interchange Control outer loop, however other valid areas will still be considered.

### Interchange flow calculation

In IIDM each area defines the boundary points to be considered in the interchange. IIDM supports two ways of modeling area boundaries:
- either via an equipment terminal,
- or via a BoundaryLine boundary.

In the BoundaryLine case, the flow at the boundary side is considered as it should be, for both unpaired BoundaryLines and BoundaryLines paired in a TieLine.

### Slack bus mismatch attribution
Depending on the location of the slack bus(es), the role of distributing the active power mismatch will be attributed based on the following logic:
- If the slack bus is part of an area: the slack power is attributed to the area (see "total mismatch" calculation in [Algorithm description](#area-interchange-control---algorithm-description)).
  Indeed, in this case the slack injection can be seen as an interchange to 'the void' which must be resolved.
- Slack bus has no area:
    - Connected to other bus(es) without area: treated as the slack mismatch of the buses without area
    - Connected to only buses that have an area:
        - All connected branches are boundaries of those areas: Not attributed to anyone, the mismatch will already be present in the interchange mismatch
        - Some connected branches are not declared as boundaries of the areas: Amount of mismatch to distribute is split equally among the areas (added to their "total mismatch")

### Remaining slack bus mismatch distribution
This section covers the case where the "total mismatch" of all areas is in [-`areaInterchangePMaxMismatch`;`areaInterchangePMaxMismatch`], but some slack bus active power mismatch remains (even after trying to distribute on buses with no area).
This remaining slack bus active power mismatch will be distributed by all areas, each one will get a share of this mismatch to distribute.

This distribution will affect each area's interchange and will not necessarily make it closer to its target.
The distribution factor of each area will be computed in a way that minimizes chances of having the area increase its interchange mismatch up to more than [`areaInterchangePMaxMismatch`](../loadflow/parameters.md#areainterchangepmaxmismatch) in absolute value.<br>
So the factor is proportional to the "margin" of active power that the area can distribute while keeping $-areaInterchangePMaxMismatch < Area Total Mismatch < areaInterchangePMaxMismatch$.<br>

It is computed like this:<br>
$Factor = sign(Slack Bus Mismatch) * Area Total Mismatch + areaInterchangePMaxMismatch $<br>
Then factors are normalized to have sum of factors equal to 1.

The distribution is iterative (inside the same outer loop iteration).
Each area distributes its share, if some areas cannot fully distribute it, they are excluded from this distribution and the remaining slack is distributed among other areas at next iteration.
The distribution iterates until all the mismatch has been distributed and fails if all areas cannot distribute anymore but some mismatch remains.

### Zero impedance boundary branches
The following applies when the [`lowImpedanceBranchMode`](../loadflow/parameters.md#lowimpedancebranchmode) is set to `REPLACE_BY_ZERO_IMPEDANCE_LINE`.
Currently, computations involving zero-impedance branches used as boundary branches are not supported.
However, it is still possible to submit network models that include zero-impedance boundary branches.<br>
If a terminal of a zero-impedance branch is designated as a boundary, Open LoadFlow will internally assign the branch
an impedance value equal to the [`lowImpedanceThreshold`](../loadflow/parameters.md#lowimpedancethreshold) parameter.

## Incremental Phase Shifter Control

The `IncrementalPhaseControl` outer loop adjusts phase tap changers step by step, following the
[incremental outer loops principle](#incremental-outer-loops-principle). It is used when
[parameter `phaseShifterControlMode`](../loadflow/parameters.md#phaseshiftercontrolmode) is `INCREMENTAL`, and in DC whenever
[parameter `phaseShifterRegulationOn`](inv:powsyblcore:*:*:#param-lf-phase-shifter-regulation-on) is enabled.

Phase shifters controlling an active power flow (active power control mode) are moved to the tap whose phase shift is the
closest to the estimated one, without limitation of the number of taps changed in a single outer loop iteration.
This applies to both AC and DC load flows. The [combined influence](#combined-influence-of-controllers) of these phase shifters
is taken into account, depending on [parameter `incrementalControlInteractionScope`](../loadflow/parameters.md#incrementalcontrolinteractionscope).

Phase shifters limiting the current of their own branch (current limiter mode, AC only) are only adjusted when the current
is above the limit: the tap is then shifted until the estimated current is below the limit, the limit being a one-sided
constraint. These phase shifters are not part of the [combined influence](#combined-influence-of-controllers) prediction.

## Incremental Shunt Voltage Control

The `IncrementalShuntVoltageControl` outer loop adjusts shunt compensator sections step by step, following the
[incremental outer loops principle](#incremental-outer-loops-principle). It is used when
[parameter `shuntVoltageControlMode`](../loadflow/parameters.md#shuntvoltagecontrolmode) is `INCREMENTAL_VOLTAGE_CONTROL`.
The [combined influence](#combined-influence-of-controllers) of the shunt compensators is taken into account, depending on [parameter `incrementalControlInteractionScope`](../loadflow/parameters.md#incrementalcontrolinteractionscope).

The shunt compensators controlling the same bus are adjusted in successive passes within the same outer loop iteration:
at each pass, each shunt compensator, from the one with the largest susceptance range to the smallest, can change by one section.
Passes are repeated until the bus voltage is predicted within its deadband or no shunt compensator can improve it anymore.
The number of sections a shunt compensator can change in a single outer loop iteration is limited by
[parameter `incrementalShuntControlOuterLoopMaxSectionShift`](../loadflow/parameters.md#incrementalshuntcontrolouterloopmaxsectionshift).

## Incremental Transformer Voltage Control

The `IncrementalTransformerVoltageControl` outer loop adjusts ratio tap changers controlling a bus voltage step by step,
following the [incremental outer loops principle](#incremental-outer-loops-principle). It is used when
[parameter `transformerVoltageControlMode`](../loadflow/parameters.md#transformervoltagecontrolmode) is `INCREMENTAL_VOLTAGE_CONTROL`.
The [combined influence](#combined-influence-of-controllers) of the transformers is taken into account, depending on [parameter `incrementalControlInteractionScope`](../loadflow/parameters.md#incrementalcontrolinteractionscope).

When a single transformer controls a bus, it can change up to
[parameter `incrementalTransformerRatioTapControlOuterLoopMaxTapShift`](../loadflow/parameters.md#incrementaltransformerratiotapcontrolouterloopmaxtapshift)
taps in a single outer loop iteration.

When several transformers control the same bus, they are adjusted in successive passes within the same outer loop iteration:
at each pass, each transformer can change by one tap. Passes are repeated until the bus voltage is predicted within its
deadband or no transformer can improve it anymore, so that the tap changes are distributed among all the transformers.
The number of taps a transformer can change in a single outer loop iteration is also limited by
[parameter `incrementalTransformerRatioTapControlOuterLoopMaxTapShift`](../loadflow/parameters.md#incrementaltransformerratiotapcontrolouterloopmaxtapshift).

Transformers whose ratio has almost no influence on the controlled voltage (sensitivity of the voltage to the ratio lower than 0.05 per unit)
are not adjusted. This status is kept for the following iterations, and reset when another outer loop has run in between
(for instance after a generator has reached a reactive limit).

## Incremental Transformer Reactive Power Control

The `IncrementalTransformerReactivePowerControl` outer loop adjusts ratio tap changers controlling a branch reactive power flow
step by step, following the [incremental outer loops principle](#incremental-outer-loops-principle). It is used when
[parameter `transformerReactivePowerControl`](../loadflow/parameters.md#transformerreactivepowercontrol) is enabled.
The [combined influence](#combined-influence-of-controllers) of the transformers is not taken into account: each transformer
corrects its own mismatch independently of the others.

Only one transformer can control the reactive power of a branch: if several transformers control the same branch, only
the first one is kept, the others being ignored (they keep their initial tap position).
The controlling transformer can change up to
[parameter `incrementalTransformerRatioTapControlOuterLoopMaxTapShift`](../loadflow/parameters.md#incrementaltransformerratiotapcontrolouterloopmaxtapshift)
taps in a single outer loop iteration, to bring the reactive power flow at the controlled side of the branch within its deadband.
If a generator also controls the reactive power flow of the same branch, the generator target is used.

## Incremental outer loops

Incremental outer loops adjust discrete controllers (phase tap changer positions, shunt compensator sections, ratio tap changer positions)
step by step, the controllers always staying at one of their discrete positions.

| Outer loop                                                                             | Controllers        | Controlled value            | AC / DC | Maximum change per outer loop iteration and controller                                                                                                  | Combined influence |
|----------------------------------------------------------------------------------------|--------------------|-----------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|
| [Phase shifter control](#incremental-phase-shifter-control), active power control mode | Phase tap changers | Branch active power flow    | AC, DC  | Not limited                                                                                                                                             | Yes                |
| [Phase shifter control](#incremental-phase-shifter-control), current limiter mode      | Phase tap changers | Current of their own branch | AC      | Not limited                                                                                                                                             | No                 |
| [Shunt voltage control](#incremental-shunt-voltage-control)                            | Shunt compensators | Bus voltage                 | AC      | [`incrementalShuntControlOuterLoopMaxSectionShift`](../loadflow/parameters.md#incrementalshuntcontrolouterloopmaxsectionshift) sections                 | Yes                |
| [Transformer voltage control](#incremental-transformer-voltage-control)                | Ratio tap changers | Bus voltage                 | AC      | [`incrementalTransformerRatioTapControlOuterLoopMaxTapShift`](../loadflow/parameters.md#incrementaltransformerratiotapcontrolouterloopmaxtapshift) taps | Yes                |
| [Transformer reactive power control](#incremental-transformer-reactive-power-control)  | Ratio tap changers | Branch reactive power flow  | AC      | [`incrementalTransformerRatioTapControlOuterLoopMaxTapShift`](../loadflow/parameters.md#incrementaltransformerratiotapcontrolouterloopmaxtapshift) taps | No                 |

### Incremental outer loops principle

The controlled values are not part of the equation system: the Newton-Raphson is solved with the controllers at their current position.
Then, at each outer loop iteration:
1. The controlled elements outside of their target deadband are identified.
2. The sensitivities of the controlled values to the controller values (susceptance, ratio or phase shift) are computed
   from the Jacobian matrix of the last Newton-Raphson resolution.
3. From the mismatch to the target and the sensitivity, the required change of each controller value is estimated, and
   the controller is moved towards a discrete position whose value is close to the estimated one. How moves are done
   exactly (closest position, one step at a time when several controllers control the same element, limitation of
   the number of steps...) depends on the outer loop, see each outer loop description.
4. If at least one controller has moved, the outer loop is unstable and a new Newton-Raphson is triggered.

The outer loop is stable when all controlled values are within their deadband, or when no move can improve them anymore
(for instance because controllers are at their limits).

A controlled value is considered within its deadband when its distance to the target is lower than half of the target deadband.
When no deadband is given, a minimal one is used: 0.1 kV for voltage controls, 0.1 MVar for reactive power controls and
1 MW for active power controls.

To avoid oscillations, the number of direction changes of each controller is limited: after 3 direction changes, a controller
can only move again in the direction of its last move.

### Combined influence of controllers

When several controlled elements are close to each other (shunts on nearby buses, transformers feeding buses tied by a short line,
phase shifters in series...), moving a controller also changes the values controlled by the others.
If each controller corrected its own mismatch independently of the others, they would together overshoot their targets,
then possibly oscillate until the maximum number of direction changes is reached, and end far from their targets.

To avoid this, when [parameter `incrementalControlInteractionScope`](../loadflow/parameters.md#incrementalcontrolinteractionscope)
is `ALL_CONTROLLED_ELEMENTS` (the default), the incremental phase shifter control (active power control mode), shunt voltage control
and transformer voltage control outer loops predict, within an outer loop iteration, the effect of the decided moves on all the
controlled values of the outer loop:
- The mismatches of all the controlled elements (whether within their deadband or not) are initialized from the last Newton-Raphson resolution.
- Moves are decided one after the other. A move is evaluated on all the controlled elements, using the sensitivities of the
  moved controller to each of them, and is accepted only if it reduces the following objective, i.e. the sum of the
  squared mismatches exceeding the deadbands:

  $$
  Objective = \sum_{i} \max\left(0, \left|Target_i - Value_i\right| - \frac{Deadband_i}{2}\right)^2
  $$

  where $i$ goes over all the controlled elements of the outer loop and $Value_i$ is the predicted controlled value.
  Otherwise, the controller is set back to its previous position.
- Each accepted move updates the predicted mismatches, so that the following controllers only correct what remains.
- Controlled elements are handled by decreasing mismatch exceeding their deadband: the most deviating elements are adjusted first.

As the prediction relies on sensitivities, it is a linearization: the remaining error, if any, is corrected at the next outer loop iteration.

For example, consider two phase shifters in series, each controlling its own active power flow, with 1° phase shift steps
changing the flow by about 14 MW per step. The flow is initially 50 MW and the target is 76 MW (deadband 4 MW).
Each phase shifter alone needs 2 steps to reach the target:
- Without accounting for the combined influence, both phase shifters move by 2 steps, so the flow overshoots to about 105 MW.
  They then move back and forth until the maximum number of direction changes is reached, ending at about 105 MW.
- Accounting for it, once the first phase shifter has moved by 2 steps, the flow is predicted within its deadband and the
  second one does not move: the flow is about 78 MW after a single outer loop iteration.

As a move is only accepted if it reduces the mismatches of all the controlled elements, a controller may not reach its own
target when this would worsen another controlled element, for instance when nearby controlled elements have conflicting targets:
the controlled values then end at a compromise, possibly outside of their deadbands.
When [parameter `incrementalControlInteractionScope`](../loadflow/parameters.md#incrementalcontrolinteractionscope) is
`SAME_CONTROLLED_ELEMENT`, a move is only evaluated on its own controlled element, and only the moves of the other controllers
of the same controlled element are accounted for: each controller tries to reach its own target, at the risk of the overshoots
and oscillations described above.
