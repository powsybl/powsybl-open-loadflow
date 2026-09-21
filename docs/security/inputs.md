# Inputs

The Open Load Flow security analysis is based on the [PowSyBl Core security analysis](inv:powsyblcore:*:*#simulation/security/index). Not all the inputs are supported, please read the page for precision on what is supported by this implementation.

## Supported contingencies

Only the following contingencies are currently implemented in Open load flow:

- `BusContingency`
- `BusbarSectionContingency`
- `SwitchContingency`
- `LineContingency`
- `TwoWindingsTransformerContingency`
- `TieLineContingency`
- `BoundaryLineContingency`
- `GeneratorContingency`
- `LoadContingency`
- `ShuntCompensatorContingency`
- `StaticVarCompensatorContingency`
- `BusbarSectionContingency`
- `BatteryContingency`
- `ThreeWindingsTransformerContingency`
- `HvdcLineContingency`

See more details on [Powsybl Core security contingencies](inv:powsyblcore:*:*#security-contingencies) documentation.

## Supported remedial actions

Only the following remedial actions are currently implemented in Open load flow:

- `LoadAction`
- `SwitchAction`
- `TerminalsConnectionAction` (Only supporting actions on branches and three windings transformers terminals)
- `PhaseTapChangerTapPositionAction`
- `RatioTapChangerTapPositionAction`
- `ShuntCompensatorPositionAction`
- `GeneratorAction`
- `HvdcAction`
- `AreaInterchangeTargetAction`

Note: Some limitations in the use of these actions exist, please read the documentation about the [security analysis specific parameters](parameters).

See more details on [Powsybl Core security remedial actions](inv:powsyblcore:*:*#security-remedial-actions) documentation.

## Supported limit reductions

Only following limit reduction definition is supported:
- Current limit type only (`LimitType.CURRENT` as limit type)
- Always applied to all contingencies and in the pre-contingency state (`ContingencyContextType.ALL`)
- Not Monitoring only (`monitoringOnly=false`), it affects the reported violations and the conditions for applying remedial actions
- Maximum two duration criteria can be provided, and they should be of the same type
- No identifiable criterion class among the criteria
- The limit reduction is applied on all the operational limits groups, so restriction to apply it on a specified list of operational limits groups will be rejected.

So if you want to use limit reductions you can follow this kind of example:
```
double value = 0.95;
List<NetworkElementCriterion> networkElementCriteria = List.of(
    new LineCriterion(new TwoCountriesCriterion(List.of(Country.FR), List.of(Country.BE)), null),
    new TwoWindingsTransformerCriterion(new SingleCountryCriterion(List.of(Country.BE)), null)
);
List<LimitDurationCriterion> durationCriteria = List.of(new EqualityTemporaryDurationCriterion(10), new EqualityTemporaryDurationCriterion(60));
List<String> operationalLimitsGroupIdsSelection = Collections.emptyList();

// Through the builder
    
LimitReduction limitReduction = LimitReduction.builder(LimitType.CURRENT, value)
    .withNetworkElementCriteria(durationCriteria)
    .withLimitDurationCriteria(operationalLimitsGroupIdsSelection)
    .withOperationalLimitsGroupIdSelection(Collections.emptyList())
    .build();

// Or through the constructor

LimitReduction limitReduction = new LimitReduction(
    LimitType.CURRENT,
    value,
    false,
    ContingencyContext.all(),
    networkElementCriteria,
    durationCriteria,
    operationalLimitsGroupIdsSelection
);
```

See more details on [Powsybl Core security limit reductions](inv:powsyblcore:*:*#security-limit-reductions) documentation.
