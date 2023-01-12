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
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.openloadflow.util.Reports;
import net.jafama.FastMath;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        List<VoltageControl> voltageControls = new ArrayList<>();

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
                    controlledBus.getVoltageControl().ifPresentOrElse(
                        vc -> updateVoltageControl(vc, controllerBus, controllerTargetV),
                        () -> createVoltageControl(controlledBus, controllerBus, controllerTargetV, voltageControls, parameters));
                } else {
                    // if voltage remote control deactivated and remote control, set local control instead
                    LOGGER.warn("Remote voltage control is not activated. The voltage target of {} with remote control is rescaled from {} to {}",
                            controllerBus.getId(), controllerTargetV, controllerTargetV * controllerBus.getNominalV() / controlledBus.getNominalV());
                    controlledBus.getVoltageControl().ifPresentOrElse(
                        vc -> updateVoltageControl(vc, controllerBus, controllerTargetV), // updating only to check targetV uniqueness
                        () -> createVoltageControl(controllerBus, controllerBus, controllerTargetV, voltageControls, parameters));
                }
            }
        }

        if (parameters.isVoltagePerReactivePowerControl()) {
            voltageControls.forEach(LfNetworkLoaderImpl::checkGeneratorsWithSlope);
        }
    }

    private static void createVoltageControl(LfBus controlledBus, LfBus controllerBus, double controllerTargetV, List<VoltageControl> voltageControls,
                                             LfNetworkParameters parameters) {
        VoltageControl voltageControl = new VoltageControl(controlledBus, controllerTargetV);
        voltageControl.addControllerBus(controllerBus);
        controlledBus.setVoltageControl(voltageControl);
        if (parameters.isVoltagePerReactivePowerControl()) {
            voltageControls.add(voltageControl);
        }
        if (controllerBus.getGenerators().stream().anyMatch(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.MONITORING_VOLTAGE)) {
            controllerBus.setVoltageControlEnabled(false);
        }
    }

    private static void updateVoltageControl(VoltageControl voltageControl, LfBus controllerBus, double controllerTargetV) {
        voltageControl.addControllerBus(controllerBus);
        checkUniqueTargetVControlledBus(controllerTargetV, controllerBus, voltageControl);
    }

    private static void checkGeneratorsWithSlope(VoltageControl voltageControl) {
        List<LfGenerator> generatorsWithSlope = voltageControl.getControllerBuses().stream()
                .filter(LfBus::hasGeneratorsWithSlope)
                .flatMap(lfBus -> lfBus.getGeneratorsControllingVoltageWithSlope().stream())
                .collect(Collectors.toList());

        if (!generatorsWithSlope.isEmpty()) {
            if (voltageControl.isSharedControl()) {
                generatorsWithSlope.forEach(generator -> generator.getBus().removeGeneratorSlopes());
                LOGGER.warn("Non supported: shared control on bus {} with {} generator(s) controlling voltage with slope. Slope set to 0 on all those generators",
                        voltageControl.getControlledBus(), generatorsWithSlope.size());
            } else if (!voltageControl.isVoltageControlLocal()) {
                generatorsWithSlope.forEach(generator -> generator.getBus().removeGeneratorSlopes());
                LOGGER.warn("Non supported: remote control on bus {} with {} generator(s) controlling voltage with slope",
                        voltageControl.getControlledBus(), generatorsWithSlope.size());
            }
        }
    }

    private static void checkUniqueTargetVControlledBus(double controllerTargetV, LfBus controllerBus, VoltageControl vc) {
        // check if target voltage is consistent with other already existing controller buses
        double voltageControlTargetV = vc.getTargetValue();
        double deltaTargetV = FastMath.abs(voltageControlTargetV - controllerTargetV);
        LfBus controlledBus = vc.getControlledBus();
        if (deltaTargetV * controlledBus.getNominalV() > TARGET_V_EPSILON) {
            String busesId = vc.getControllerBuses().stream().map(LfBus::getId).collect(Collectors.joining(", "));
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

    private static void createRemoteReactivePowerControl(LfBranch controlledBranch, ReactivePowerControl.ControlledSide side, LfBus controllerBus,
                                                         double targetQ) {
        ReactivePowerControl control = new ReactivePowerControl(controlledBranch, side, controllerBus, targetQ);
        controllerBus.setReactivePowerControl(control);
        controlledBranch.setReactivePowerControl(control);
    }

    private static void createReactivePowerControls(List<LfBus> lfBuses) {
        for (LfBus controllerBus : lfBuses) {
            List<LfGenerator> generators = controllerBus.getGenerators().stream()
                    .filter(LfGenerator::hasRemoteReactivePowerControl).collect(Collectors.toList());
            if (!generators.isEmpty()) {
                Optional<VoltageControl> voltageControl = controllerBus.getVoltageControl();
                if (voltageControl.isPresent()) {
                    LOGGER.warn("Bus {} has both voltage and remote reactive power controls: only voltage control is kept", controllerBus.getId());
                    continue;
                }
                if (generators.size() == 1) {
                    LfGenerator lfGenerator = generators.get(0);
                    LfBranch controlledBranch = lfGenerator.getControlledBranch();
                    Optional<ReactivePowerControl> control = controlledBranch.getReactivePowerControl();
                    if (control.isPresent()) {
                        LOGGER.warn("Branch {} is remotely controlled by a generator: no new remote reactive control created", controlledBranch.getId());
                    } else {
                        createRemoteReactivePowerControl(lfGenerator.getControlledBranch(), lfGenerator.getControlledBranchSide(), controllerBus, lfGenerator.getRemoteTargetQ());
                    }
                } else { // generators.size() > 1 (as > 0 and not equal to 1)
                    LOGGER.warn("Bus {} has more than one generator controlling reactive power remotely: not yet supported", controllerBus.getId());
                }
            }
        }
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
                lfBus.addLoad(load);
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
                        lfBus.addLccConverterStation((LccConverterStation) converterStation);
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
            if (lfBranch.isZeroImpedance(true) || lfBranch.isZeroImpedance(false)) {
                LOGGER.trace("Branch {} is non impedant", lfBranch.getId());
                report.nonImpedantBranches++;
            }
            lfNetwork.addBranch(lfBranch);
        }
    }

    private static void createBranches(List<LfBus> lfBuses, LfNetwork lfNetwork, LoadingContext loadingContext, LfNetworkLoadingReport report,
                                       LfNetworkParameters parameters, List<LfNetworkLoaderPostProcessor> postProcessors) {
        for (Branch<?> branch : loadingContext.branchSet) {
            LfBus lfBus1 = getLfBus(branch.getTerminal1(), lfNetwork, parameters.isBreakers());
            LfBus lfBus2 = getLfBus(branch.getTerminal2(), lfNetwork, parameters.isBreakers());
            LfBranchImpl lfBranch = LfBranchImpl.create(branch, lfNetwork, lfBus1, lfBus2, parameters);
            addBranch(lfNetwork, lfBranch, report);
            postProcessors.forEach(pp -> pp.onBranchAdded(branch, lfBranch));
        }

        for (DanglingLine danglingLine : loadingContext.danglingLines) {
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
        }

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            LfStarBus lfBus0 = new LfStarBus(lfNetwork, t3wt);
            lfNetwork.addBus(lfBus0);
            LfBus lfBus1 = getLfBus(t3wt.getLeg1().getTerminal(), lfNetwork, parameters.isBreakers());
            LfBus lfBus2 = getLfBus(t3wt.getLeg2().getTerminal(), lfNetwork, parameters.isBreakers());
            LfBus lfBus3 = getLfBus(t3wt.getLeg3().getTerminal(), lfNetwork, parameters.isBreakers());
            LfLegBranch lfBranch1 = LfLegBranch.create(lfNetwork, lfBus1, lfBus0, t3wt, t3wt.getLeg1(), parameters);
            LfLegBranch lfBranch2 = LfLegBranch.create(lfNetwork, lfBus2, lfBus0, t3wt, t3wt.getLeg2(), parameters);
            LfLegBranch lfBranch3 = LfLegBranch.create(lfNetwork, lfBus3, lfBus0, t3wt, t3wt.getLeg3(), parameters);
            addBranch(lfNetwork, lfBranch1, report);
            addBranch(lfNetwork, lfBranch2, report);
            addBranch(lfNetwork, lfBranch3, report);
            postProcessors.forEach(pp -> {
                pp.onBusAdded(t3wt, lfBus0);
                pp.onBranchAdded(t3wt, lfBranch1);
                pp.onBranchAdded(t3wt, lfBranch2);
                pp.onBranchAdded(t3wt, lfBranch3);
            });
        }

        if (parameters.isPhaseControl()) {
            for (Branch<?> branch : loadingContext.branchSet) {
                if (branch instanceof TwoWindingsTransformer) {
                    // Create phase controls which link controller -> controlled
                    TwoWindingsTransformer t2wt = (TwoWindingsTransformer) branch;
                    PhaseTapChanger ptc = t2wt.getPhaseTapChanger();
                    createPhaseControl(lfNetwork, ptc, t2wt.getId(), "", parameters);
                }
            }
            for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
                // Create phase controls which link controller -> controlled
                List<ThreeWindingsTransformer.Leg> legs = t3wt.getLegs();
                for (int legNumber = 0; legNumber < legs.size(); legNumber++) {
                    PhaseTapChanger ptc = legs.get(legNumber).getPhaseTapChanger();
                    createPhaseControl(lfNetwork, ptc, t3wt.getId(), "_leg_" + (legNumber + 1), parameters);
                }
            }
        }

        if (parameters.isHvdcAcEmulation()) {
            for (HvdcLine hvdcLine : loadingContext.hvdcLineSet) {
                HvdcAngleDroopActivePowerControl control = hvdcLine.getExtension(HvdcAngleDroopActivePowerControl.class);
                if (control != null && control.isEnabled()) {
                    LfBus lfBus1 = getLfBus(hvdcLine.getConverterStation1().getTerminal(), lfNetwork, parameters.isBreakers());
                    LfBus lfBus2 = getLfBus(hvdcLine.getConverterStation2().getTerminal(), lfNetwork, parameters.isBreakers());
                    LfHvdc lfHvdc = new LfHvdcImpl(hvdcLine.getId(), lfBus1, lfBus2, lfNetwork, control);
                    LfVscConverterStationImpl cs1 = (LfVscConverterStationImpl) lfNetwork.getGeneratorById(hvdcLine.getConverterStation1().getId());
                    LfVscConverterStationImpl cs2 = (LfVscConverterStationImpl) lfNetwork.getGeneratorById(hvdcLine.getConverterStation2().getId());
                    if (cs1 != null && cs2 != null) {
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

    private static void createTransformersVoltageControls(LfNetwork lfNetwork, LfNetworkParameters parameters, LoadingContext loadingContext) {
        // Create discrete voltage controls which link controller -> controlled
        for (Branch<?> branch : loadingContext.branchSet) {
            if (branch instanceof TwoWindingsTransformer) {
                RatioTapChanger rtc = ((TwoWindingsTransformer) branch).getRatioTapChanger();
                createTransformerVoltageControl(lfNetwork, rtc, branch.getId(), parameters);
            }
        }
        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            List<ThreeWindingsTransformer.Leg> legs = t3wt.getLegs();
            for (int legNumber = 0; legNumber < legs.size(); legNumber++) {
                RatioTapChanger rtc = legs.get(legNumber).getRatioTapChanger();
                createTransformerVoltageControl(lfNetwork, rtc, t3wt.getId() + "_leg_" + (legNumber + 1), parameters);
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

    private static void fixAllVoltageControls(LfNetwork lfNetwork, LfNetworkParameters parameters) {
        // If min impedance is set, there is no zero-impedance branch
        if (!parameters.isDc() && !parameters.isMinImpedance()) {
            // Merge the discrete voltage control in each zero impedance connected set
            List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(lfNetwork.getZeroImpedanceNetwork(false).getSubGraph()).connectedSets();
            connectedSets.forEach(connectedSet -> mergeVoltageControls(connectedSet, parameters));
        }
    }

    private static void mergeVoltageControls(Set<LfBus> zeroImpedanceConnectedSet, LfNetworkParameters parameters) {
        // Get the list of voltage controls from controlled buses in the zero impedance connected set
        // Note that the list order is not deterministic as jgrapht connected set computation is using a basic HashSet
        List<VoltageControl> voltageControls = zeroImpedanceConnectedSet.stream().filter(LfBus::isVoltageControlled)
                .map(LfBus::getVoltageControl).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        // Get the list of discrete voltage controls from controlled buses in the zero impedance connected set
        // Note that the list order is not deterministic as jgrapht connected set computation is using a basic HashSet
        List<TransformerVoltageControl> transformerVoltageControls = !parameters.isTransformerVoltageControl() ? Collections.emptyList() :
            zeroImpedanceConnectedSet.stream().filter(LfBus::isTransformerVoltageControlled)
                .map(LfBus::getTransformerVoltageControl).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        if (voltageControls.isEmpty() && transformerVoltageControls.size() <= 1) {
            return;
        }

        if (!voltageControls.isEmpty()) {

            // First, resolve problem of voltage controls (generator, static var compensator, etc.)
            // We have several controls whose controlled bus are in the same non-impedant connected set
            // To solve that we keep only one voltage control (and its target value), the other ones are removed
            // and the corresponding controllers are added to the control kept
            if (voltageControls.size() > 1) {
                LOGGER.info("Zero impedance connected set with several voltage controls: controls are merged");

                // Sort voltage controls to have a merged voltage control with a deterministic controlled bus,
                // a deterministic target value and controller buses in a deterministic order
                voltageControls.sort(Comparator.comparing(VoltageControl::getTargetValue).thenComparing(vc -> vc.getControlledBus().getId()));
                checkVoltageControlUniqueTargetV(voltageControls);

                // Merge the controllers into the kept voltage control
                VoltageControl keptVoltageControl = voltageControls.remove(voltageControls.size() - 1);
                voltageControls.forEach(vc -> vc.getControlledBus().removeVoltageControl());
                voltageControls.stream()
                    .flatMap(vc -> vc.getControllerBuses().stream())
                    .forEach(controller -> {
                        keptVoltageControl.addControllerBus(controller);
                        controller.setVoltageControl(keptVoltageControl);
                    });
            }

            // Second, we have to remove all the discrete voltage controls if present.
            if (!transformerVoltageControls.isEmpty()) {
                LOGGER.info("Zero impedance connected set with several discrete voltage controls and a voltage control: discrete controls deleted");
                transformerVoltageControls.stream()
                        .flatMap(dvc -> dvc.getControllers().stream())
                        .forEach(controller -> controller.setVoltageControl(null));
                transformerVoltageControls.forEach(dvc -> dvc.getControlled().setTransformerVoltageControl(null));
            }
        } else {
            // We have at least 2 discrete controls whose controlled bus are in the same non-impedant connected set
            // To solve that we keep only one discrete voltage control, the other ones are removed
            // and the corresponding controllers are added to the discrete control kept
            LOGGER.info("Zero impedance connected set with several discrete voltage controls: discrete controls merged");

            // Sort discrete voltage controls to have a merged discrete voltage control with a deterministic controlled bus,
            // a deterministic target value and controller branches in a deterministic order
            transformerVoltageControls.sort(Comparator.comparing(TransformerVoltageControl::getTargetValue).thenComparing(vc -> vc.getControlled().getId()));
            checkTransformerVoltageControlUniqueTargetV(transformerVoltageControls);

            // Merge the controllers into the kept voltage control
            TransformerVoltageControl keptTransformerVoltageControl = transformerVoltageControls.remove(transformerVoltageControls.size() - 1);
            transformerVoltageControls.forEach(dvc -> dvc.getControlled().setTransformerVoltageControl(null));
            transformerVoltageControls.stream()
                .flatMap(tvc -> tvc.getControllers().stream())
                .forEach(controller -> {
                    keptTransformerVoltageControl.addController(controller);
                    controller.setVoltageControl(keptTransformerVoltageControl);
                });
        }
    }

    private static void checkVoltageControlUniqueTargetV(List<VoltageControl> voltageControls) {
        // To check uniqueness we take the target value which will be kept as reference.
        // The kept target value is the highest, it corresponds to the last voltage control in the ordered list.
        VoltageControl vcRef = voltageControls.get(voltageControls.size() - 1);
        boolean uniqueTargetV = voltageControls.stream().noneMatch(vc -> FastMath.abs(vc.getTargetValue() - vcRef.getTargetValue()) > TARGET_V_EPSILON);
        if (!uniqueTargetV) {
            LOGGER.error("Inconsistent voltage controls: buses {} are in the same non-impedant connected set and are controlled with different target voltages ({}). Only target voltage {} is kept",
                voltageControls.stream().map(VoltageControl::getControlledBus).collect(Collectors.toList()),
                voltageControls.stream().map(VoltageControl::getTargetValue).collect(Collectors.toList()),
                vcRef.getTargetValue());
        }
    }

    private static void checkTransformerVoltageControlUniqueTargetV(List<TransformerVoltageControl> transformerVoltageControls) {
        // To check uniqueness we take the target value which will be kept as reference.
        // The kept target value is the highest, it corresponds to the last discrete voltage control in the ordered list.
        TransformerVoltageControl tvcRef = transformerVoltageControls.get(transformerVoltageControls.size() - 1);
        boolean uniqueTargetV = transformerVoltageControls.stream().noneMatch(dvc -> FastMath.abs(dvc.getTargetValue() - tvcRef.getTargetValue()) > TARGET_V_EPSILON);
        if (!uniqueTargetV) {
            LOGGER.error("Inconsistent transformer voltage controls: buses {} are in the same non-impedant connected set and are controlled with different target voltages ({}). Only target voltage {} is kept",
                    transformerVoltageControls.stream().map(TransformerVoltageControl::getControlled).collect(Collectors.toList()),
                    transformerVoltageControls.stream().map(TransformerVoltageControl::getTargetValue).collect(Collectors.toList()),
                    tvcRef.getTargetValue());
        }
    }

    private static void createPhaseControl(LfNetwork lfNetwork, PhaseTapChanger ptc, String controllerBranchId, String legId,
                                           LfNetworkParameters parameters) {
        if (ptc != null && ptc.isRegulating() && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {
            String controlledBranchId = ptc.getRegulationTerminal().getConnectable().getId();
            if (controlledBranchId.equals(controllerBranchId)) {
                // Local control: each leg is controlling its phase
                controlledBranchId += legId;
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
            LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId + legId);
            if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
                LOGGER.warn("Phase controller branch '{}' is open: phase control discarded", controllerBranch.getId());
                return;
            }
            if (ptc.getRegulationTerminal().getBusView().getBus() == null) {
                LOGGER.warn("Regulating terminal of phase controller branch '{}' is out of voltage: phase control discarded", controllerBranch.getId());
                return;
            }
            LfBus controlledBus = getLfBus(ptc.getRegulationTerminal(), lfNetwork, parameters.isBreakers());
            DiscretePhaseControl.ControlledSide controlledSide = controlledBus == controlledBranch.getBus1() ?
                    DiscretePhaseControl.ControlledSide.ONE : DiscretePhaseControl.ControlledSide.TWO;
            if (controlledBranch instanceof LfLegBranch && controlledBus == controlledBranch.getBus2()) {
                throw new IllegalStateException("Leg " + controlledBranch.getId() + " has a non supported control at star bus side");
            }
            double targetValue;
            double targetDeadband;
            DiscretePhaseControl phaseControl = null;
            if (ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.CURRENT_LIMITER) {
                if (controlledBranch == controllerBranch && controlledBus != null) {
                    targetValue = ptc.getRegulationValue() / PerUnit.ib(controlledBus.getNominalV());
                    targetDeadband = ptc.getTargetDeadband() / PerUnit.ib(controlledBus.getNominalV());
                    phaseControl = new DiscretePhaseControl(controllerBranch, controlledBranch, controlledSide,
                            DiscretePhaseControl.Mode.LIMITER, targetValue, targetDeadband, DiscretePhaseControl.Unit.A);
                } else {
                    LOGGER.warn("Branch {} limits current limiter on remote branch {}: not supported yet", controllerBranch.getId(), controlledBranch.getId());
                }
            } else if (ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                targetValue = ptc.getRegulationValue() / PerUnit.SB;
                targetDeadband = ptc.getTargetDeadband() / PerUnit.SB;
                phaseControl = new DiscretePhaseControl(controllerBranch, controlledBranch, controlledSide,
                        DiscretePhaseControl.Mode.CONTROLLER, targetValue, targetDeadband, DiscretePhaseControl.Unit.MW);
            }
            controllerBranch.setDiscretePhaseControl(phaseControl);
            controlledBranch.setDiscretePhaseControl(phaseControl);
        }
    }

    private static void createTransformerVoltageControl(LfNetwork lfNetwork, RatioTapChanger rtc, String controllerBranchId,
                                                        LfNetworkParameters parameters) {
        if (rtc == null || !rtc.isRegulating() || !rtc.hasLoadTapChangingCapabilities()) {
            return;
        }
        LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId);
        if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
            LOGGER.warn("Voltage controller branch '{}' is open: voltage control discarded", controllerBranch.getId());
            return;
        }
        LfBus controlledBus = getLfBus(rtc.getRegulationTerminal(), lfNetwork, parameters.isBreakers());
        if (controlledBus == null) {
            LOGGER.warn("Regulating terminal of voltage controller branch '{}' is out of voltage or in a different synchronous component: voltage control discarded", controllerBranch.getId());
            return;
        }
        if (controlledBus.isVoltageControlled()) {
            LOGGER.warn("Controlled bus '{}' has both generator and transformer voltage control on: only generator control is kept", controlledBus.getId());
            return;
        }

        double regulatingTerminalNominalV = rtc.getRegulationTerminal().getVoltageLevel().getNominalV();
        double targetValue = rtc.getTargetV() / regulatingTerminalNominalV;
        double deadbandValue = rtc.getTargetDeadband() / regulatingTerminalNominalV;

        controlledBus.getTransformerVoltageControl().ifPresentOrElse(vc -> {
            LOGGER.trace("Controlled bus '{}' already has a transformer voltage control: a shared control is created", controlledBus.getId());
            if (FastMath.abs(vc.getTargetValue() - targetValue) > TARGET_V_EPSILON) {
                LOGGER.warn("Controlled bus '{}' already has a transformer voltage control with a different target voltage: {} and {}",
                        controlledBus.getId(), vc.getTargetValue(), targetValue);
            }
            vc.addController(controllerBranch);
            controllerBranch.setVoltageControl(vc);
            if (deadbandValue > 0) {
                controllerBranch.setTransformerVoltageControlTargetDeadband(deadbandValue);
            }
        }, () -> {
                TransformerVoltageControl voltageControl = new TransformerVoltageControl(controlledBus, targetValue);
                voltageControl.addController(controllerBranch);
                controllerBranch.setVoltageControl(voltageControl);
                if (deadbandValue > 0) {
                    controllerBranch.setTransformerVoltageControlTargetDeadband(deadbandValue);
                }
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
        if (controlledBus.isVoltageControlled()) {
            LOGGER.warn("Controlled bus {} has both generator and shunt voltage control on: only generator control is kept", controlledBus.getId());
            controllerShunt.setVoltageControlCapability(false);
            return;
        }
        Optional<TransformerVoltageControl> tvc = controlledBus.getTransformerVoltageControl();
        if (tvc.isPresent()) {
            LOGGER.error("Controlled bus {} has already a transformer voltage control: only transformer control is kept", controlledBus.getId());
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
        double deadbandValue = shuntCompensator.getTargetDeadband() / regulatingTerminalNominalV;

        controlledBus.getShuntVoltageControl().ifPresentOrElse(voltageControl -> {
            LOGGER.trace("Controlled bus {} has already a shunt voltage control: a shared control is created", controlledBus.getId());
            if (FastMath.abs(voltageControl.getTargetValue() - targetValue) > TARGET_V_EPSILON) {
                LOGGER.warn("Controlled bus {} already has a transformer voltage control with a different target voltage: {} and {}",
                        controlledBus.getId(), voltageControl.getTargetValue(), targetValue);
            }
            if (!voltageControl.getControllers().contains(controllerShunt)) {
                voltageControl.addController(controllerShunt);
                controllerShunt.setVoltageControl(voltageControl);
                if (deadbandValue > 0) {
                    controllerShunt.setShuntVoltageControlTargetDeadband(deadbandValue);
                }
                controlledBus.setShuntVoltageControl(voltageControl);
            }
        }, () -> {
                // we create a new shunt voltage control.
                ShuntVoltageControl voltageControl = new ShuntVoltageControl(controlledBus, targetValue);
                voltageControl.addController(controllerShunt);
                controllerShunt.setVoltageControl(voltageControl);
                if (deadbandValue > 0) {
                    controllerShunt.setShuntVoltageControlTargetDeadband(deadbandValue);
                }
                controlledBus.setShuntVoltageControl(voltageControl);
            });
    }

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork, boolean breakers) {
        Bus bus = Networks.getBus(terminal, breakers);
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private LfNetwork create(int numCC, int numSC, List<Bus> buses, List<Switch> switches, LfNetworkParameters parameters, Reporter reporter) {
        LfNetwork lfNetwork = new LfNetwork(numCC, numSC, parameters.getSlackBusSelector(), parameters.getMaxSlackBusCount(),
                parameters.getConnectivityFactory(), reporter);

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();
        List<LfNetworkLoaderPostProcessor> postProcessors = postProcessorsSupplier.get().stream()
                .filter(pp -> pp.getLoadingPolicy() == LfNetworkLoaderPostProcessor.LoadingPolicy.ALWAYS
                        || (pp.getLoadingPolicy() == LfNetworkLoaderPostProcessor.LoadingPolicy.SELECTION && parameters.getLoaderPostProcessorSelection().contains(pp.getName())))
                .collect(Collectors.toList());

        List<LfBus> lfBuses = new ArrayList<>();
        createBuses(buses, parameters, lfNetwork, lfBuses, loadingContext, report, postProcessors);
        createBranches(lfBuses, lfNetwork, loadingContext, report, parameters, postProcessors);

        if (!parameters.isDc()) {
            createVoltageControls(lfBuses, parameters);
            if (parameters.isReactivePowerRemoteControl()) {
                createReactivePowerControls(lfBuses);
            }
            if (parameters.isTransformerVoltageControl()) {
                // Discrete voltage controls need to be created after voltage controls (to test if both generator and transformer voltage control are on)
                createTransformersVoltageControls(lfNetwork, parameters, loadingContext);
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

        // Fixing voltage controls need to be done after creating switches, as the zero-impedance graph is changed with switches
        fixAllVoltageControls(lfNetwork, parameters);

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

        return lfNetwork;
    }

    @Override
    public List<LfNetwork> load(Network network, LfNetworkParameters parameters, Reporter reporter) {
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
                    return create(numCc, numSc, lfBuses, switchesByCc.get(networkKey), parameters,
                            Reports.createLfNetworkReporter(reporter, numCc, numSc));
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
