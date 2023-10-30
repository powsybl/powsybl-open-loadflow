/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl.ControlZone;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl.PilotPoint;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.extensions.OverloadManagementSystem;
import com.powsybl.openloadflow.network.impl.extensions.SubstationAutomationSystems;
import com.powsybl.openloadflow.util.DebugUtil;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import net.jafama.FastMath;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.openloadflow.util.DebugUtil.DATE_TIME_FORMAT;
import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetworkLoaderImpl implements LfNetworkLoader<Network> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetworkLoaderImpl.class);

    private static final double TARGET_V_EPSILON = 1e-2;

    private static class LoadingContext {

        private final Set<Branch<?>> branchSet = new LinkedHashSet<>();

        private final List<DanglingLine> danglingLines = new ArrayList<>();

        private final Set<ThreeWindingsTransformer> t3wtSet = new LinkedHashSet<>();

        private final Set<ShuntCompensator> shuntSet = new LinkedHashSet<>();

        private final Set<HvdcLine> hvdcLineSet = new LinkedHashSet<>();
    }

    private final Supplier<List<LfNetworkLoaderPostProcessor>> postProcessorsSupplier;

    public LfNetworkLoaderImpl() {
        this(LfNetworkLoaderPostProcessor::findAll);
    }

    public LfNetworkLoaderImpl(Supplier<List<LfNetworkLoaderPostProcessor>> postProcessorsSupplier) {
        this.postProcessorsSupplier = Objects.requireNonNull(postProcessorsSupplier);
    }

    private static void createBuses(List<Bus> buses, LfNetworkParameters parameters, LfNetwork lfNetwork, List<LfBus> lfBuses,
                                    LoadingContext loadingContext, LfNetworkLoadingReport report, List<LfNetworkLoaderPostProcessor> postProcessors) {
        for (Bus bus : buses) {
            LfBusImpl lfBus = createBus(bus, parameters, lfNetwork, loadingContext, report, postProcessors);
            postProcessors.forEach(pp -> pp.onBusAdded(bus, lfBus));
            lfNetwork.addBus(lfBus);
            lfBuses.add(lfBus);
        }
    }

    private static void createVoltageControls(List<LfBus> lfBuses, LfNetworkParameters parameters) {
        List<GeneratorVoltageControl> voltageControls = new ArrayList<>();

        // set controller -> controlled link
        for (LfBus controllerBus : lfBuses) {

            List<LfGenerator> voltageControlGenerators = new ArrayList<>(1);
            List<LfGenerator> voltageMonitoringGenerators = new ArrayList<>(1);
            for (var generator : controllerBus.getGenerators()) {
                if (generator.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE) {
                    voltageControlGenerators.add(generator);
                } else if (generator.getGeneratorControlType() == LfGenerator.GeneratorControlType.MONITORING_VOLTAGE) {
                    voltageMonitoringGenerators.add(generator);
                }
            }
            if (voltageMonitoringGenerators.size() > 1) {
                String generatorIds = voltageMonitoringGenerators.stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
                LOGGER.warn("We have several voltage monitors ({}) connected to the same bus: not supported. All switched to voltage control", generatorIds);
                voltageMonitoringGenerators.forEach(gen -> gen.setGeneratorControlType(LfGenerator.GeneratorControlType.VOLTAGE));
            }
            if (!voltageControlGenerators.isEmpty() && !voltageMonitoringGenerators.isEmpty()) {
                String generatorIds = voltageMonitoringGenerators.stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
                LOGGER.warn("We have both voltage controllers and voltage monitors ({}) connected to the same bus: voltage monitoring discarded", generatorIds);
                voltageMonitoringGenerators.forEach(gen -> gen.setGeneratorControlType(LfGenerator.GeneratorControlType.OFF));
                voltageMonitoringGenerators.clear();
            }
            voltageControlGenerators.addAll(voltageMonitoringGenerators);

            if (!voltageControlGenerators.isEmpty()) {

                LfGenerator lfGenerator0 = voltageControlGenerators.get(0);
                LfBus controlledBus = lfGenerator0.getControlledBus();
                double controllerTargetV = lfGenerator0.getTargetV();

                voltageControlGenerators.stream().skip(1).forEach(lfGenerator -> {
                    LfBus generatorControlledBus = lfGenerator.getControlledBus();

                    // check that remote control bus is the same for the generators of current controller bus which have voltage control on
                    if (checkUniqueControlledBus(controlledBus, generatorControlledBus, controllerBus)) {
                        // check that target voltage is the same for the generators of current controller bus which have voltage control on
                        checkUniqueTargetVControllerBus(lfGenerator, controllerTargetV, controllerBus, generatorControlledBus);
                    }
                });

                if (parameters.isGeneratorVoltageRemoteControl() || controlledBus == controllerBus) {
                    controlledBus.getGeneratorVoltageControl().ifPresentOrElse(
                        vc -> updateGeneratorVoltageControl(vc, controllerBus, controllerTargetV),
                        () -> createGeneratorVoltageControl(controlledBus, controllerBus, controllerTargetV, voltageControls, parameters));
                } else {
                    // if voltage remote control deactivated and remote control, set local control instead
                    LOGGER.warn("Remote voltage control is not activated. The voltage target of {} with remote control is rescaled from {} to {}",
                            controllerBus.getId(), controllerTargetV, controllerTargetV * controllerBus.getNominalV() / controlledBus.getNominalV());
                    controlledBus.getGeneratorVoltageControl().ifPresentOrElse(
                        vc -> updateGeneratorVoltageControl(vc, controllerBus, controllerTargetV), // updating only to check targetV uniqueness
                        () -> createGeneratorVoltageControl(controllerBus, controllerBus, controllerTargetV, voltageControls, parameters));
                }
            }
        }

        if (parameters.isVoltagePerReactivePowerControl()) {
            voltageControls.forEach(LfNetworkLoaderImpl::checkGeneratorsWithSlope);
        }
    }

    private static void createGeneratorVoltageControl(LfBus controlledBus, LfBus controllerBus, double controllerTargetV, List<GeneratorVoltageControl> voltageControls,
                                                      LfNetworkParameters parameters) {
        GeneratorVoltageControl voltageControl = new GeneratorVoltageControl(controlledBus, controllerTargetV);
        voltageControl.addControllerElement(controllerBus);
        controlledBus.setGeneratorVoltageControl(voltageControl);
        if (parameters.isVoltagePerReactivePowerControl()) {
            voltageControls.add(voltageControl);
        }
        if (controllerBus.getGenerators().stream().anyMatch(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.MONITORING_VOLTAGE)) {
            controllerBus.setGeneratorVoltageControlEnabled(false);
        }
    }

    private static void updateGeneratorVoltageControl(GeneratorVoltageControl voltageControl, LfBus controllerBus, double controllerTargetV) {
        voltageControl.addControllerElement(controllerBus);
        checkUniqueTargetVControlledBus(controllerTargetV, controllerBus, voltageControl);
    }

    private static void checkGeneratorsWithSlope(GeneratorVoltageControl voltageControl) {
        List<LfGenerator> generatorsWithSlope = voltageControl.getControllerElements().stream()
                .filter(LfBus::hasGeneratorsWithSlope)
                .flatMap(lfBus -> lfBus.getGeneratorsControllingVoltageWithSlope().stream())
                .collect(Collectors.toList());

        if (!generatorsWithSlope.isEmpty()) {
            if (voltageControl.isSharedControl()) {
                generatorsWithSlope.forEach(generator -> generator.getBus().removeGeneratorSlopes());
                LOGGER.warn("Non supported: shared control on bus {} with {} generator(s) controlling voltage with slope. Slope set to 0 on all those generators",
                        voltageControl.getControlledBus(), generatorsWithSlope.size());
            } else if (!voltageControl.isLocalControl()) {
                generatorsWithSlope.forEach(generator -> generator.getBus().removeGeneratorSlopes());
                LOGGER.warn("Non supported: remote control on bus {} with {} generator(s) controlling voltage with slope",
                        voltageControl.getControlledBus(), generatorsWithSlope.size());
            }
        }
    }

    private static void checkUniqueTargetVControlledBus(double controllerTargetV, LfBus controllerBus, GeneratorVoltageControl vc) {
        // check if target voltage is consistent with other already existing controller buses
        double voltageControlTargetV = vc.getTargetValue();
        double deltaTargetV = FastMath.abs(voltageControlTargetV - controllerTargetV);
        LfBus controlledBus = vc.getControlledBus();
        if (deltaTargetV * controlledBus.getNominalV() > TARGET_V_EPSILON) {
            String busesId = vc.getControllerElements().stream().map(LfBus::getId).collect(Collectors.joining(", "));
            LOGGER.error("Bus '{}' control voltage of bus '{}' which is already controlled by buses '{}' with a different target voltage: {} (kept) and {} (ignored)",
                    controllerBus.getId(), controlledBus.getId(), busesId, controllerTargetV * controlledBus.getNominalV(),
                    voltageControlTargetV * controlledBus.getNominalV());
        }
    }

    private static boolean checkUniqueControlledBus(LfBus controlledBus, LfBus controlledBusGen, LfBus controller) {
        Objects.requireNonNull(controlledBus);
        Objects.requireNonNull(controlledBusGen);
        if (controlledBus.getNum() != controlledBusGen.getNum()) {
            String generatorIds = controller.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
            LOGGER.warn("Generators [{}] are connected to the same bus '{}' but control the voltage of different buses: {} (kept) and {} (rejected)",
                    generatorIds, controller.getId(), controlledBus.getId(), controlledBusGen.getId());
            return false;
        }
        return true;
    }

    private static void checkUniqueTargetVControllerBus(LfGenerator lfGenerator, double previousTargetV, LfBus controllerBus, LfBus controlledBus) {
        double targetV = lfGenerator.getTargetV();
        if (FastMath.abs(previousTargetV - targetV) > TARGET_V_EPSILON) {
            String generatorIds = controllerBus.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
            LOGGER.error("Generators [{}] are connected to the same bus '{}' with different target voltages: {} (kept) and {} (rejected)",
                generatorIds, controllerBus.getId(), previousTargetV * controlledBus.getNominalV(), targetV * controlledBus.getNominalV());
        }
    }

    private static void createReactivePowerControls(List<LfBus> lfBuses) {
        for (LfBus controllerBus : lfBuses) {
            List<LfGenerator> generators = controllerBus.getGenerators().stream()
                    .filter(LfGenerator::hasRemoteReactivePowerControl).collect(Collectors.toList());
            if (!generators.isEmpty()) {
                Optional<GeneratorVoltageControl> voltageControl = controllerBus.getGeneratorVoltageControl();
                if (voltageControl.isPresent()) {
                    LOGGER.warn("Bus {} has both voltage and remote reactive power controls: only voltage control is kept", controllerBus.getId());
                    continue;
                }
                if (generators.size() == 1) {
                    LfGenerator lfGenerator = generators.get(0);
                    LfBranch controlledBranch = lfGenerator.getControlledBranch();
                    createRemoteReactivePowerControl(controllerBus, lfGenerator, controlledBranch);
                } else { // generators.size() > 1 (as > 0 and not equal to 1)
                    LOGGER.warn("Bus {} has more than one generator controlling reactive power remotely: not yet supported", controllerBus.getId());
                }
            }
        }
    }

    private static void createRemoteReactivePowerControl(LfBus controllerBus, LfGenerator lfGenerator, LfBranch controlledBranch) {
        if (controlledBranch == null) {
            LOGGER.warn("Controlled branch of generator '{}' is out of voltage or in a different synchronous component: remote reactive power control discarded", lfGenerator.getId());
            return;
        }
        if (!controlledBranch.isConnectedAtBothSides()) {
            LOGGER.warn("Controlled branch '{}' must be connected at both sides: remote reactive power control discarded", controlledBranch.getId());
            return;
        }
        Optional<ReactivePowerControl> optionalControl = controlledBranch.getReactivePowerControl();
        if (optionalControl.isPresent()) {
            LOGGER.warn("Branch {} is already remotely controlled by a generator: no new remote reactive control created", controlledBranch.getId());
            return;
        }
        ControlledSide side = lfGenerator.getControlledBranchSide();
        double targetQ = lfGenerator.getRemoteTargetQ();
        ReactivePowerControl control = new ReactivePowerControl(controlledBranch, side, controllerBus, targetQ);
        controllerBus.setReactivePowerControl(control);
        controllerBus.setReactivePowerControlEnabled(true);
        controlledBranch.setReactivePowerControl(control);
    }

    private static LfBusImpl createBus(Bus bus, LfNetworkParameters parameters, LfNetwork lfNetwork, LoadingContext loadingContext,
                                       LfNetworkLoadingReport report, List<LfNetworkLoaderPostProcessor> postProcessors) {
        LfBusImpl lfBus = LfBusImpl.create(bus, lfNetwork, parameters, participateToSlackDistribution(parameters, bus));

        List<ShuntCompensator> shuntCompensators = new ArrayList<>();

        bus.visitConnectedEquipments(new DefaultTopologyVisitor() {

            private void visitBranch(Branch<?> branch) {
                loadingContext.branchSet.add(branch);
            }

            @Override
            public void visitLine(Line line, Branch.Side side) {
                visitBranch(line);
            }

            @Override
            public void visitTwoWindingsTransformer(TwoWindingsTransformer transformer, Branch.Side side) {
                visitBranch(transformer);
            }

            @Override
            public void visitThreeWindingsTransformer(ThreeWindingsTransformer transformer, ThreeWindingsTransformer.Side side) {
                loadingContext.t3wtSet.add(transformer);
            }

            @Override
            public void visitGenerator(Generator generator) {
                lfBus.addGenerator(generator, parameters, report);
                postProcessors.forEach(pp -> pp.onInjectionAdded(generator, lfBus));
            }

            @Override
            public void visitLoad(Load load) {
                lfBus.addLoad(load, parameters);
                postProcessors.forEach(pp -> pp.onInjectionAdded(load, lfBus));
            }

            @Override
            public void visitShuntCompensator(ShuntCompensator sc) {
                shuntCompensators.add(sc);
                postProcessors.forEach(pp -> pp.onInjectionAdded(sc, lfBus));
                if (parameters.isShuntVoltageControl()) {
                    loadingContext.shuntSet.add(sc);
                }
            }

            @Override
            public void visitDanglingLine(DanglingLine danglingLine) {
                loadingContext.danglingLines.add(danglingLine);
                postProcessors.forEach(pp -> pp.onInjectionAdded(danglingLine, lfBus));
            }

            @Override
            public void visitStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
                lfBus.addStaticVarCompensator(staticVarCompensator, parameters, report);
                postProcessors.forEach(pp -> pp.onInjectionAdded(staticVarCompensator, lfBus));
            }

            @Override
            public void visitBattery(Battery battery) {
                lfBus.addBattery(battery, parameters, report);
                postProcessors.forEach(pp -> pp.onInjectionAdded(battery, lfBus));
            }

            @Override
            public void visitHvdcConverterStation(HvdcConverterStation<?> converterStation) {
                switch (converterStation.getHvdcType()) {
                    case VSC:
                        VscConverterStation vscConverterStation = (VscConverterStation) converterStation;
                        lfBus.addVscConverterStation(vscConverterStation, parameters, report);
                        loadingContext.hvdcLineSet.add(converterStation.getHvdcLine());
                        break;
                    case LCC:
                        lfBus.addLccConverterStation((LccConverterStation) converterStation, parameters);
                        loadingContext.hvdcLineSet.add(converterStation.getHvdcLine());
                        break;
                    default:
                        throw new IllegalStateException("Unknown HVDC converter station type: " + converterStation.getHvdcType());
                }
                postProcessors.forEach(pp -> pp.onInjectionAdded(converterStation, lfBus));
            }
        });

        if (!shuntCompensators.isEmpty()) {
            lfBus.setShuntCompensators(shuntCompensators, parameters);
        }

        return lfBus;
    }

    private static void addBranch(LfNetwork lfNetwork, LfBranch lfBranch, LfNetworkLoadingReport report) {
        boolean connectedToSameBus = lfBranch.getBus1() == lfBranch.getBus2();
        if (connectedToSameBus) {
            LOGGER.trace("Discard branch '{}' because connected to same bus at both ends", lfBranch.getId());
            report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds++;
        } else {
            if (Arrays.stream(LoadFlowModel.values()).anyMatch(lfBranch::isZeroImpedance)) {
                LOGGER.trace("Branch {} is non impedant", lfBranch.getId());
                report.nonImpedantBranches++;
            }
            lfNetwork.addBranch(lfBranch);
        }
    }

    private static void createBranches(List<LfBus> lfBuses, LfNetwork lfNetwork, LfTopoConfig topoConfig, LoadingContext loadingContext,
                                       LfNetworkLoadingReport report, LfNetworkParameters parameters,
                                       List<LfNetworkLoaderPostProcessor> postProcessors) {
        for (Branch<?> branch : loadingContext.branchSet) {
            LfBus lfBus1 = getLfBus(branch.getTerminal1(), lfNetwork, parameters.isBreakers());
            LfBus lfBus2 = getLfBus(branch.getTerminal2(), lfNetwork, parameters.isBreakers());
            LfBranchImpl lfBranch = LfBranchImpl.create(branch, lfNetwork, lfBus1, lfBus2, topoConfig, parameters);
            addBranch(lfNetwork, lfBranch, report);
            postProcessors.forEach(pp -> pp.onBranchAdded(branch, lfBranch));
        }

        Set<String> visitedDanglingLinesIds = new HashSet<>();
        for (DanglingLine danglingLine : loadingContext.danglingLines) {
            danglingLine.getTieLine().ifPresentOrElse(tieLine -> {
                if (!visitedDanglingLinesIds.contains(danglingLine.getId())) {
                    LfBus lfBus1 = getLfBus(tieLine.getDanglingLine1().getTerminal(), lfNetwork, parameters.isBreakers());
                    LfBus lfBus2 = getLfBus(tieLine.getDanglingLine2().getTerminal(), lfNetwork, parameters.isBreakers());
                    LfBranch lfBranch = LfTieLineBranch.create(tieLine, lfNetwork, lfBus1, lfBus2, parameters);
                    addBranch(lfNetwork, lfBranch, report);
                    postProcessors.forEach(pp -> pp.onBranchAdded(tieLine, lfBranch));
                    visitedDanglingLinesIds.add(tieLine.getDanglingLine1().getId());
                    visitedDanglingLinesIds.add(tieLine.getDanglingLine2().getId());
                }
            }, () -> {
                    LfDanglingLineBus lfBus2 = new LfDanglingLineBus(lfNetwork, danglingLine, parameters, report);
                    lfNetwork.addBus(lfBus2);
                    lfBuses.add(lfBus2);
                    LfBus lfBus1 = getLfBus(danglingLine.getTerminal(), lfNetwork, parameters.isBreakers());
                    LfBranch lfBranch = LfDanglingLineBranch.create(danglingLine, lfNetwork, lfBus1, lfBus2, parameters);
                    addBranch(lfNetwork, lfBranch, report);
                    postProcessors.forEach(pp -> {
                        pp.onBusAdded(danglingLine, lfBus2);
                        pp.onBranchAdded(danglingLine, lfBranch);
                    });
            });
        }

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            LfStarBus lfBus0 = new LfStarBus(lfNetwork, t3wt, parameters);
            lfNetwork.addBus(lfBus0);
            postProcessors.forEach(pp -> pp.onBusAdded(t3wt, lfBus0));
            for (ThreeWindingsTransformer.Side side : ThreeWindingsTransformer.Side.values()) {
                ThreeWindingsTransformer.Leg leg = t3wt.getLeg(side);
                LfBus lfBus = getLfBus(leg.getTerminal(), lfNetwork, parameters.isBreakers());
                LfLegBranch lfBranch = LfLegBranch.create(lfNetwork, lfBus, lfBus0, t3wt, leg,
                        topoConfig.isRetainedPtc(LfLegBranch.getId(side, t3wt.getId())),
                        topoConfig.isRetainedRtc(LfLegBranch.getId(side, t3wt.getId())),
                        parameters);
                addBranch(lfNetwork, lfBranch, report);
                postProcessors.forEach(pp -> pp.onBranchAdded(t3wt, lfBranch));
            }
        }

        if (parameters.isPhaseControl()) {
            for (Branch<?> branch : loadingContext.branchSet) {
                if (branch instanceof TwoWindingsTransformer t2wt) {
                    // Create phase controls which link controller -> controlled
                    PhaseTapChanger ptc = t2wt.getPhaseTapChanger();
                    createPhaseControl(lfNetwork, ptc, t2wt.getId(), parameters);
                }
            }
            for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
                // Create phase controls which link controller -> controlled
                for (ThreeWindingsTransformer.Side side : ThreeWindingsTransformer.Side.values()) {
                    PhaseTapChanger ptc = t3wt.getLeg(side).getPhaseTapChanger();
                    createPhaseControl(lfNetwork, ptc, LfLegBranch.getId(side, t3wt.getId()), parameters);
                }
            }
        }

        if (parameters.isHvdcAcEmulation()) {
            for (HvdcLine hvdcLine : loadingContext.hvdcLineSet) {
                HvdcAngleDroopActivePowerControl control = hvdcLine.getExtension(HvdcAngleDroopActivePowerControl.class);
                if (control != null && control.isEnabled()) {
                    LfBus lfBus1 = getLfBus(hvdcLine.getConverterStation1().getTerminal(), lfNetwork, parameters.isBreakers());
                    LfBus lfBus2 = getLfBus(hvdcLine.getConverterStation2().getTerminal(), lfNetwork, parameters.isBreakers());
                    LfVscConverterStationImpl cs1 = (LfVscConverterStationImpl) lfNetwork.getGeneratorById(hvdcLine.getConverterStation1().getId());
                    LfVscConverterStationImpl cs2 = (LfVscConverterStationImpl) lfNetwork.getGeneratorById(hvdcLine.getConverterStation2().getId());
                    if (cs1 != null && cs2 != null) {
                        LfHvdc lfHvdc = new LfHvdcImpl(hvdcLine.getId(), lfBus1, lfBus2, lfNetwork, control);
                        lfHvdc.setConverterStation1((LfVscConverterStationImpl) lfNetwork.getGeneratorById(hvdcLine.getConverterStation1().getId()));
                        lfHvdc.setConverterStation2((LfVscConverterStationImpl) lfNetwork.getGeneratorById(hvdcLine.getConverterStation2().getId()));
                        lfNetwork.addHvdc(lfHvdc);
                    } else {
                        LOGGER.warn("Hvdc line '{}' in AC emulation but converter stations are not in the same synchronous component: operated using active set point.", hvdcLine.getId());
                    }
                }
            }
        }
    }

    private static void createTransformersVoltageControls(LfNetwork lfNetwork, LfNetworkParameters parameters, LoadingContext loadingContext,
                                                          LfNetworkLoadingReport report) {
        // Create discrete voltage controls which link controller -> controlled
        for (Branch<?> branch : loadingContext.branchSet) {
            if (branch instanceof TwoWindingsTransformer t2wt) {
                RatioTapChanger rtc = t2wt.getRatioTapChanger();
                createTransformerVoltageControl(lfNetwork, rtc, t2wt.getId(), parameters, report);
            }
        }
        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            for (ThreeWindingsTransformer.Side side : ThreeWindingsTransformer.Side.values()) {
                RatioTapChanger rtc = t3wt.getLeg(side).getRatioTapChanger();
                createTransformerVoltageControl(lfNetwork, rtc, LfLegBranch.getId(side, t3wt.getId()), parameters, report);
            }
        }
    }

    private static void createSwitches(List<Switch> switches, LfNetwork lfNetwork, List<LfNetworkLoaderPostProcessor> postProcessors,
                                       LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        if (switches != null) {
            for (Switch sw : switches) {
                VoltageLevel vl = sw.getVoltageLevel();
                Bus bus1 = vl.getBusBreakerView().getBus1(sw.getId());
                Bus bus2 = vl.getBusBreakerView().getBus2(sw.getId());
                LfBus lfBus1 = lfNetwork.getBusById(bus1.getId());
                LfBus lfBus2 = lfNetwork.getBusById(bus2.getId());
                LfSwitch lfSwitch = new LfSwitch(lfNetwork, lfBus1, lfBus2, sw, parameters);
                addBranch(lfNetwork, lfSwitch, report);
                postProcessors.forEach(pp -> pp.onBranchAdded(sw, lfSwitch));
            }
        }
    }

    private static void createPhaseControl(LfNetwork lfNetwork, PhaseTapChanger ptc, String controllerBranchId,
                                           LfNetworkParameters parameters) {
        if (ptc != null && ptc.isRegulating() && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {
            String controlledBranchId = ptc.getRegulationTerminal().getConnectable().getId();
            if (ptc.getRegulationTerminal().getConnectable() instanceof ThreeWindingsTransformer twt) {
                controlledBranchId = LfLegBranch.getId(twt.getSide(ptc.getRegulationTerminal()), controlledBranchId);
            }
            LfBranch controlledBranch = lfNetwork.getBranchById(controlledBranchId);
            if (controlledBranch == null) {
                LOGGER.warn("Phase controlled branch '{}' is out of voltage or in a different synchronous component: phase control discarded", controlledBranchId);
                return;
            }
            if (controlledBranch.getBus1() == null || controlledBranch.getBus2() == null) {
                LOGGER.warn("Phase controlled branch '{}' is open: phase control discarded", controlledBranch.getId());
                return;
            }
            LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId);
            if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
                LOGGER.warn("Phase controller branch '{}' is open: phase control discarded", controllerBranch.getId());
                return;
            }
            if (ptc.getRegulationTerminal().getBusView().getBus() == null) {
                LOGGER.warn("Regulating terminal of phase controller branch '{}' is out of voltage: phase control discarded", controllerBranch.getId());
                return;
            }
            LfBus controlledBus = getLfBus(ptc.getRegulationTerminal(), lfNetwork, parameters.isBreakers());
            ControlledSide controlledSide = controlledBus == controlledBranch.getBus1() ?
                    ControlledSide.ONE : ControlledSide.TWO;
            if (controlledBranch instanceof LfLegBranch && controlledBus == controlledBranch.getBus2()) {
                throw new IllegalStateException("Leg " + controlledBranch.getId() + " has a non supported control at star bus side");
            }
            double targetValue;
            double targetDeadband;
            TransformerPhaseControl phaseControl = null;
            if (ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.CURRENT_LIMITER) {
                if (controlledBranch == controllerBranch && controlledBus != null) {
                    targetValue = ptc.getRegulationValue() / PerUnit.ib(controlledBus.getNominalV());
                    targetDeadband = ptc.getTargetDeadband() / PerUnit.ib(controlledBus.getNominalV());
                    phaseControl = new TransformerPhaseControl(controllerBranch, controlledBranch, controlledSide,
                            TransformerPhaseControl.Mode.LIMITER, targetValue, targetDeadband, TransformerPhaseControl.Unit.A);
                } else {
                    LOGGER.warn("Branch {} limits current limiter on remote branch {}: not supported yet", controllerBranch.getId(), controlledBranch.getId());
                }
            } else if (ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                targetValue = ptc.getRegulationValue() / PerUnit.SB;
                targetDeadband = ptc.getTargetDeadband() / PerUnit.SB;
                phaseControl = new TransformerPhaseControl(controllerBranch, controlledBranch, controlledSide,
                        TransformerPhaseControl.Mode.CONTROLLER, targetValue, targetDeadband, TransformerPhaseControl.Unit.MW);
            }
            controllerBranch.setPhaseControl(phaseControl);
            controlledBranch.setPhaseControl(phaseControl);
        }
    }

    private static void createTransformerVoltageControl(LfNetwork lfNetwork, RatioTapChanger rtc, String controllerBranchId,
                                                        LfNetworkParameters parameters, LfNetworkLoadingReport report) {
        if (rtc == null || !rtc.isRegulating() || !rtc.hasLoadTapChangingCapabilities()) {
            return;
        }
        LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId);
        if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
            LOGGER.trace("Voltage controller branch '{}' is open: voltage control discarded", controllerBranch.getId());
            report.transformerVoltageControlDiscardedBecauseControllerBranchIsOpen++;
            return;
        }
        LfBus controlledBus = getLfBus(rtc.getRegulationTerminal(), lfNetwork, parameters.isBreakers());
        if (controlledBus == null) {
            LOGGER.warn("Regulating terminal of voltage controller branch '{}' is out of voltage or in a different synchronous component: voltage control discarded", controllerBranch.getId());
            return;
        }

        double regulatingTerminalNominalV = rtc.getRegulationTerminal().getVoltageLevel().getNominalV();
        double targetValue = rtc.getTargetV() / regulatingTerminalNominalV;
        Double targetDeadband = rtc.getTargetDeadband() > 0 ? rtc.getTargetDeadband() / regulatingTerminalNominalV : null;

        controlledBus.getTransformerVoltageControl().ifPresentOrElse(vc -> {
            LOGGER.trace("Controlled bus '{}' already has a transformer voltage control: a shared control is created", controlledBus.getId());
            if (FastMath.abs(vc.getTargetValue() - targetValue) > TARGET_V_EPSILON) {
                LOGGER.warn("Controlled bus '{}' already has a transformer voltage control with a different target voltage: {} and {}",
                        controlledBus.getId(), vc.getTargetValue(), targetValue);
            }
            vc.addControllerElement(controllerBranch);
            controllerBranch.setVoltageControl(vc);
            if (targetDeadband != null) {
                Double oldTargetDeadband = vc.getTargetDeadband().orElse(null);
                if (oldTargetDeadband == null) {
                    vc.setTargetDeadband(targetDeadband);
                } else {
                    // merge target deadbands by taking minimum
                    vc.setTargetDeadband(Math.min(oldTargetDeadband, targetDeadband));
                }
            }
        }, () -> {
                TransformerVoltageControl voltageControl = new TransformerVoltageControl(controlledBus, targetValue, targetDeadband);
                voltageControl.addControllerElement(controllerBranch);
                controllerBranch.setVoltageControl(voltageControl);
                controlledBus.setTransformerVoltageControl(voltageControl);
            });
    }

    private static void createShuntVoltageControl(LfNetwork lfNetwork, ShuntCompensator shuntCompensator, LfNetworkParameters parameters) {
        if (!shuntCompensator.isVoltageRegulatorOn()) {
            return;
        }
        LfBus controllerBus = getLfBus(shuntCompensator.getTerminal(), lfNetwork, parameters.isBreakers());
        if (controllerBus == null) {
            LOGGER.warn("Voltage controller shunt {} is out of voltage: no voltage control created", shuntCompensator.getId());
            return;
        }
        LfShunt controllerShunt = controllerBus.getControllerShunt().orElseThrow();
        LfBus controlledBus = getLfBus(shuntCompensator.getRegulatingTerminal(), lfNetwork, parameters.isBreakers());
        if (controlledBus == null) {
            LOGGER.warn("Regulating terminal of voltage controller shunt {} is out of voltage: no voltage control created", shuntCompensator.getId());
            controllerShunt.setVoltageControlCapability(false);
            return;
        }
        if (controllerShunt.getVoltageControl().isPresent()) {
            // if a controller shunt is already in a shunt voltage control, the number of equations will not equal the
            // number of variables. We have only one B variable for more than one bus target V equations.
            LOGGER.error("Controller shunt {} is already in a shunt voltage control. The second controlled bus {} is ignored", controllerShunt.getId(), controlledBus.getId());
            return;
        }

        double regulatingTerminalNominalV = shuntCompensator.getRegulatingTerminal().getVoltageLevel().getNominalV();
        double targetValue = shuntCompensator.getTargetV() / regulatingTerminalNominalV;
        Double targetDeadband = shuntCompensator.getTargetDeadband() > 0 ? shuntCompensator.getTargetDeadband() / regulatingTerminalNominalV : null;

        controlledBus.getShuntVoltageControl().ifPresentOrElse(voltageControl -> {
            LOGGER.trace("Controlled bus {} has already a shunt voltage control: a shared control is created", controlledBus.getId());
            if (FastMath.abs(voltageControl.getTargetValue() - targetValue) > TARGET_V_EPSILON) {
                LOGGER.warn("Controlled bus {} already has a shunt voltage control with a different target voltage: {} and {}",
                        controlledBus.getId(), voltageControl.getTargetValue(), targetValue);
            }
            if (!voltageControl.getControllerElements().contains(controllerShunt)) {
                voltageControl.addControllerElement(controllerShunt);
                controllerShunt.setVoltageControl(voltageControl);
                controlledBus.setShuntVoltageControl(voltageControl);
                if (targetDeadband != null) {
                    Double oldTargetDeadband = voltageControl.getTargetDeadband().orElse(null);
                    if (oldTargetDeadband == null) {
                        voltageControl.setTargetDeadband(targetDeadband);
                    } else {
                        // merge target deadbands by taking minimum
                        voltageControl.setTargetDeadband(Math.min(oldTargetDeadband, targetDeadband));
                    }
                }
            }
        }, () -> {
                // we create a new shunt voltage control.
                ShuntVoltageControl voltageControl = new ShuntVoltageControl(controlledBus, targetValue, targetDeadband);
                voltageControl.addControllerElement(controllerShunt);
                controllerShunt.setVoltageControl(voltageControl);
                controlledBus.setShuntVoltageControl(voltageControl);
            });
    }

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork, boolean breakers) {
        Bus bus = Networks.getBus(terminal, breakers);
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private LfNetwork create(int numCC, int numSC, Network network, List<Bus> buses, List<Switch> switches, LfTopoConfig topoConfig, LfNetworkParameters parameters, Reporter reporter) {
        LfNetwork lfNetwork = new LfNetwork(numCC, numSC, parameters.getSlackBusSelector(), parameters.getMaxSlackBusCount(),
                parameters.getConnectivityFactory(), reporter);

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();
        List<LfNetworkLoaderPostProcessor> postProcessors = postProcessorsSupplier.get().stream()
                .filter(pp -> pp.getLoadingPolicy() == LfNetworkLoaderPostProcessor.LoadingPolicy.ALWAYS
                        || pp.getLoadingPolicy() == LfNetworkLoaderPostProcessor.LoadingPolicy.SELECTION && parameters.getLoaderPostProcessorSelection().contains(pp.getName()))
                .collect(Collectors.toList());

        List<LfBus> lfBuses = new ArrayList<>();
        createBuses(buses, parameters, lfNetwork, lfBuses, loadingContext, report, postProcessors);
        createBranches(lfBuses, lfNetwork, topoConfig, loadingContext, report, parameters, postProcessors);

        if (parameters.getLoadFlowModel() == LoadFlowModel.AC) {
            createVoltageControls(lfBuses, parameters);
            if (parameters.isReactivePowerRemoteControl()) {
                createReactivePowerControls(lfBuses);
            }
            if (parameters.isTransformerVoltageControl()) {
                // Discrete voltage controls need to be created after voltage controls (to test if both generator and transformer voltage control are on)
                createTransformersVoltageControls(lfNetwork, parameters, loadingContext, report);
            }
            if (parameters.isShuntVoltageControl()) {
                for (ShuntCompensator shunt : loadingContext.shuntSet) {
                    createShuntVoltageControl(lfNetwork, shunt, parameters);
                }
            }
        }

        if (parameters.isBreakers()) {
            createSwitches(switches, lfNetwork, postProcessors, parameters, report);
        }

        // secondary voltage controls
        createSecondaryVoltageControls(network, parameters, lfNetwork);

        // voltage angle limits
        createVoltageAngleLimits(network, lfNetwork, parameters);

        if (parameters.isSimulateAutomationSystems()) {
            createAutomationSystems(network, lfNetwork);
        }

        if (report.generatorsDiscardedFromVoltageControlBecauseNotStarted > 0) {
            Reports.reportGeneratorsDiscardedFromVoltageControlBecauseNotStarted(reporter, report.generatorsDiscardedFromVoltageControlBecauseNotStarted);
            LOGGER.warn("Network {}: {} generators have been discarded from voltage control because not started",
                    lfNetwork, report.generatorsDiscardedFromVoltageControlBecauseNotStarted);
        }
        if (report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall > 0) {
            Reports.reportGeneratorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall(reporter, report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall);
            LOGGER.warn("Network {}: {} generators have been discarded from voltage control because of a too small reactive range",
                    lfNetwork, report.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of a targetP equals 0",
                    lfNetwork, report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of a targetP > maxP",
                    lfNetwork, report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of maxP not plausible",
                    lfNetwork, report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of maxP equals to minP",
                    lfNetwork, report.generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP);
        }
        if (report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds > 0) {
            LOGGER.warn("Network {}: {} branches have been discarded because connected to same bus at both ends",
                    lfNetwork, report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds);
        }
        if (report.linesWithDifferentNominalVoltageAtBothEnds > 0) {
            LOGGER.warn("Network {}: {} lines have a different nominal voltage at both ends: a ratio has been added",
                    lfNetwork, report.linesWithDifferentNominalVoltageAtBothEnds);
        }
        if (report.nonImpedantBranches > 0) {
            LOGGER.warn("Network {}: {} branches are non impedant", lfNetwork, report.nonImpedantBranches);
        }

        if (report.generatorsWithInconsistentTargetVoltage > 0) {
            LOGGER.warn("Network {}: {} generators have an inconsistent target voltage and have been discarded from voltage control",
                    lfNetwork, report.generatorsWithInconsistentTargetVoltage);
        }

        if (report.generatorsWithZeroRemoteVoltageControlReactivePowerKey > 0) {
            LOGGER.warn("Network {}: {} generators have a zero remote voltage control reactive power key",
                    lfNetwork, report.generatorsWithZeroRemoteVoltageControlReactivePowerKey);
        }

        if (report.transformerVoltageControlDiscardedBecauseControllerBranchIsOpen > 0) {
            LOGGER.warn("Network {}: {} transformer voltage controls have been discarded because controller branch is open",
                    lfNetwork, report.transformerVoltageControlDiscardedBecauseControllerBranchIsOpen);
        }

        if (parameters.getDebugDir() != null) {
            Path debugDir = DebugUtil.getDebugDir(parameters.getDebugDir());
            String dateStr = DateTime.now().toString(DATE_TIME_FORMAT);
            lfNetwork.writeJson(debugDir.resolve("lfnetwork-" + dateStr + ".json"));
            lfNetwork.writeGraphViz(debugDir.resolve("lfnetwork-" + dateStr + ".dot"), parameters.getLoadFlowModel());
        }

        return lfNetwork;
    }

    private static void checkControlZonesAreDisjoints(LfNetwork lfNetwork) {
        Map<GeneratorVoltageControl, MutableInt> generatorVoltageControlCount = new HashMap<>();
        for (LfSecondaryVoltageControl lfSvc : lfNetwork.getSecondaryVoltageControls()) {
            for (GeneratorVoltageControl generatorVoltageControl : lfSvc.getGeneratorVoltageControls()) {
                generatorVoltageControlCount.computeIfAbsent(generatorVoltageControl, k -> new MutableInt())
                        .increment();
            }
        }
        for (var e : generatorVoltageControlCount.entrySet()) {
            if (e.getValue().intValue() > 1) {
                throw new PowsyblException("Generator voltage control of controlled bus '" + e.getKey().getControlledBus().getId() + "' is present in more that one control zone");
            }
        }
    }

    private static Set<GeneratorVoltageControl> findControlZoneGeneratorVoltageControl(Network network, LfNetworkParameters parameters, LfNetwork lfNetwork, ControlZone controlZone) {
        return controlZone.getControlUnits().stream()
                .filter(SecondaryVoltageControl.ControlUnit::isParticipate)
                .flatMap(controlUnit -> Networks.getEquipmentRegulatingTerminal(network, controlUnit.getId()).stream())
                .flatMap(regulatingTerminal -> {
                    Connectable<?> connectable = regulatingTerminal.getConnectable();
                    if (connectable.getType() != IdentifiableType.GENERATOR && !HvdcConverterStations.isVsc(connectable)) {
                        throw new PowsyblException("Control unit '" + connectable.getId() + "' of zone '"
                                + controlZone.getName() + "' is expected to be either a generator or a VSC converter station");
                    }
                    return Optional.ofNullable(getLfBus(regulatingTerminal, lfNetwork, parameters.isBreakers())).stream();
                })
                .filter(LfBus::isGeneratorVoltageControlled) // might happen to be false, if generator has been discarded from voltage control because of inconsistency (like small reactive limit range)
                .flatMap(controlledBus -> controlledBus.getGeneratorVoltageControl().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void createSecondaryVoltageControls(Network network, LfNetworkParameters parameters, LfNetwork lfNetwork) {
        if (!parameters.isSecondaryVoltageControl()) {
            return;
        }
        SecondaryVoltageControl control = network.getExtension(SecondaryVoltageControl.class);
        if (control == null) {
            return;
        }
        for (ControlZone controlZone : control.getControlZones()) {
            PilotPoint pilotPoint = controlZone.getPilotPoint();
            // only keep control zone if its pilot bus is in this LfNetwork
            findPilotBus(network, parameters.isBreakers(), pilotPoint.getBusbarSectionsOrBusesIds()).ifPresentOrElse(pilotBus -> {
                LfBus lfPilotBus = lfNetwork.getBusById(pilotBus.getId());
                if (lfPilotBus != null) { // could be in another LfNetwork (another component)
                    double targetV = pilotPoint.getTargetV() / lfPilotBus.getNominalV();
                    // filter missing control units and find corresponding primary voltage control, controlled bus
                    Set<GeneratorVoltageControl> generatorVoltageControls = findControlZoneGeneratorVoltageControl(network, parameters, lfNetwork, controlZone);
                    LOGGER.debug("{} control units of control zone '{}' have been mapped to {} generator voltage control (controlled buses are: {})",
                            controlZone.getControlUnits().size(), controlZone.getName(), generatorVoltageControls.size(),
                            generatorVoltageControls.stream().map(VoltageControl::getControlledBus).map(LfElement::getId).toList());
                    if (!generatorVoltageControls.isEmpty()) {
                        var lfSvc = new LfSecondaryVoltageControl(controlZone.getName(), lfPilotBus, targetV, generatorVoltageControls);
                        lfNetwork.addSecondaryVoltageControl(lfSvc);
                    }
                }
            }, () -> LOGGER.warn("None of the pilot buses of control zone '{}' are valid", controlZone.getName()));
        }

        checkControlZonesAreDisjoints(lfNetwork);

        LOGGER.info("Network {}: {} secondary control zones have been created ({})", lfNetwork, lfNetwork.getSecondaryVoltageControls().size(),
                lfNetwork.getSecondaryVoltageControls().stream().map(LfSecondaryVoltageControl::getZoneName).toList());
    }

    private static Optional<Bus> findPilotBus(Network network, boolean breaker, List<String> busbarSectionsOrBusesId) {
        for (String busbarSectionOrBusId : busbarSectionsOrBusesId) {
            // node/breaker case
            BusbarSection bbs = network.getBusbarSection(busbarSectionOrBusId);
            if (bbs != null) {
                return Optional.ofNullable(Networks.getBus(bbs.getTerminal(), breaker));
            }
            // bus/breaker case
            Bus configuredBus = network.getBusBreakerView().getBus(busbarSectionOrBusId);
            if (configuredBus != null) {
                return breaker ? Optional.of(configuredBus)
                        : Optional.ofNullable(configuredBus.getVoltageLevel().getBusView().getMergedBus(configuredBus.getId()));
            }
        }
        return Optional.empty();
    }

    private static void createVoltageAngleLimits(Network network, LfNetwork lfNetwork, LfNetworkParameters parameters) {
        network.getVoltageAngleLimits().forEach(voltageAngleLimit -> {
            LfBus from = getLfBus(voltageAngleLimit.getTerminalFrom(), lfNetwork, parameters.isBreakers());
            LfBus to = getLfBus(voltageAngleLimit.getTerminalTo(), lfNetwork, parameters.isBreakers());
            if (from != null && to != null) {
                lfNetwork.addVoltageAngleLimit(new LfNetwork.LfVoltageAngleLimit(voltageAngleLimit.getId(), from, to,
                        Math.toRadians(voltageAngleLimit.getHighLimit().orElse(Double.NaN)), Math.toRadians(voltageAngleLimit.getLowLimit().orElse(Double.NaN))));
            }
        });
    }

    private static void createOverloadManagementSystem(LfNetwork lfNetwork, OverloadManagementSystem system) {
        if (system.isEnabled()) {
            LfBranch lfLineToMonitor = lfNetwork.getBranchById(system.getMonitoredLineId());
            LfSwitch lfSwitchToOperate = (LfSwitch) lfNetwork.getBranchById(system.getSwitchIdToOperate());
            if (lfLineToMonitor != null && lfSwitchToOperate != null) {
                LfBus bus = lfLineToMonitor.getBus1() != null ? lfLineToMonitor.getBus1() : lfLineToMonitor.getBus2();
                double threshold = system.getThreshold() / PerUnit.ib(bus.getNominalV());
                lfNetwork.addOverloadManagementSystem(new LfOverloadManagementSystem(lfLineToMonitor, threshold, lfSwitchToOperate, system.isSwitchOpen()));
            } else {
                LOGGER.warn("Invalid overload management system: line to monitor is '{}', switch to operate is '{}'",
                        system.getMonitoredLineId(), system.getSwitchIdToOperate());
            }
        }
    }

    private void createAutomationSystems(Network network, LfNetwork lfNetwork) {
        for (Substation substation : network.getSubstations()) {
            SubstationAutomationSystems systems = substation.getExtension(SubstationAutomationSystems.class);
            if (systems != null) {
                for (OverloadManagementSystem system : systems.getOverloadManagementSystems()) {
                    createOverloadManagementSystem(lfNetwork, system);
                }
            }
        }
    }

    @Override
    public List<LfNetwork> load(Network network, LfTopoConfig topoConfig, LfNetworkParameters parameters, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);

        if (!network.getValidationLevel().equals(ValidationLevel.STEADY_STATE_HYPOTHESIS)) {
            throw new PowsyblException("Only STEADY STATE HYPOTHESIS validation level of the network is supported");
        }

        Stopwatch stopwatch = Stopwatch.createStarted();

        Map<Pair<Integer, Integer>, List<Bus>> busesByCc = new TreeMap<>();
        Iterable<Bus> buses = Networks.getBuses(network, parameters.isBreakers());
        for (Bus bus : buses) {
            Component cc = bus.getConnectedComponent();
            Component sc = bus.getSynchronousComponent();
            if (cc != null && sc != null) {
                busesByCc.computeIfAbsent(Pair.of(cc.getNum(), sc.getNum()), k -> new ArrayList<>()).add(bus);
            }
        }

        Map<Pair<Integer, Integer>, List<Switch>> switchesByCc = new HashMap<>();
        if (parameters.isBreakers()) {
            for (VoltageLevel vl : network.getVoltageLevels()) {
                for (Switch sw : vl.getBusBreakerView().getSwitches()) {
                    if (!sw.isOpen()) { // only create closed switches as in security analysis we can only open switches
                        Bus bus1 = vl.getBusBreakerView().getBus1(sw.getId());
                        Component cc = bus1.getConnectedComponent();
                        Component sc = bus1.getSynchronousComponent();
                        if (cc != null && sc != null) {
                            switchesByCc.computeIfAbsent(Pair.of(cc.getNum(), sc.getNum()), k -> new ArrayList<>()).add(sw);
                        }
                    }
                }
            }
        }

        Stream<Map.Entry<Pair<Integer, Integer>, List<Bus>>> filteredBusesByCcStream = parameters.isComputeMainConnectedComponentOnly()
            ? busesByCc.entrySet().stream().filter(e -> e.getKey().getLeft() == ComponentConstants.MAIN_NUM)
            : busesByCc.entrySet().stream();

        List<LfNetwork> lfNetworks = filteredBusesByCcStream
                .map(e -> {
                    var networkKey = e.getKey();
                    int numCc = networkKey.getLeft();
                    int numSc = networkKey.getRight();
                    List<Bus> lfBuses = e.getValue();
                    return create(numCc, numSc, network, lfBuses, switchesByCc.get(networkKey), topoConfig,
                            parameters, Reports.createLfNetworkReporter(reporter, numCc, numSc));
                })
                .collect(Collectors.toList());

        stopwatch.stop();

        LOGGER.debug(PERFORMANCE_MARKER, "LF networks created in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return lfNetworks;
    }

    static boolean participateToSlackDistribution(LfNetworkParameters parameters, Bus b) {
        return parameters.getCountriesToBalance().isEmpty()
               || b.getVoltageLevel().getSubstation().flatMap(Substation::getCountry)
                   .map(country -> parameters.getCountriesToBalance().contains(country))
                   .orElse(false);
    }
}
