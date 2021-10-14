/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import net.jafama.FastMath;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
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

        private final Set<Branch> branchSet = new LinkedHashSet<>();

        private final List<DanglingLine> danglingLines = new ArrayList<>();

        private final Set<ThreeWindingsTransformer> t3wtSet = new LinkedHashSet<>();
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

    private static void createVoltageControls(LfNetwork lfNetwork, List<LfBus> lfBuses, boolean voltageRemoteControl, boolean voltagePerReactivePowerControl) {
        List<VoltageControl> voltageControls = new ArrayList<>();

        // set controller -> controlled link
        for (LfBus controllerBus : lfBuses) {

            List<LfGenerator> voltageControlGenerators = controllerBus.getGenerators().stream().filter(LfGenerator::hasVoltageControl).collect(Collectors.toList());
            if (!voltageControlGenerators.isEmpty()) {

                LfGenerator lfGenerator0 = voltageControlGenerators.get(0);
                LfBus controlledBus = lfGenerator0.getControlledBus(lfNetwork);
                double controllerTargetV = lfGenerator0.getTargetV();

                voltageControlGenerators.stream().skip(1).forEach(lfGenerator -> {
                    LfBus generatorControlledBus = lfGenerator.getControlledBus(lfNetwork);

                    // check that remote control bus is the same for the generators of current controller bus which have voltage control on
                    checkUniqueControlledBus(controlledBus, generatorControlledBus, controllerBus);

                    // check that target voltage is the same for the generators of current controller bus which have voltage control on
                    checkUniqueTargetVControllerBus(lfGenerator, controllerTargetV, controllerBus, generatorControlledBus);
                });

                if (voltageRemoteControl || controlledBus == controllerBus) {
                    controlledBus.getVoltageControl().ifPresentOrElse(
                        vc -> updateVoltageControl(vc, controllerBus, controllerTargetV),
                        () -> createVoltageControl(controlledBus, controllerBus, controllerTargetV, voltageControls, voltagePerReactivePowerControl));
                } else {
                    // if voltage remote control deactivated and remote control, set local control instead
                    LOGGER.warn("Remote voltage control is not activated. The voltage target of {} with remote control is rescaled from {} to {}",
                            controllerBus.getId(), controllerTargetV, controllerTargetV * controllerBus.getNominalV() / controlledBus.getNominalV());
                    controlledBus.getVoltageControl().ifPresentOrElse(
                        vc -> updateVoltageControl(vc, controllerBus, controllerTargetV), // updating only to check targetV uniqueness
                        () -> createVoltageControl(controllerBus, controllerBus, controllerTargetV, voltageControls, voltagePerReactivePowerControl));
                }
            }
        }

        if (voltagePerReactivePowerControl) {
            voltageControls.forEach(LfNetworkLoaderImpl::checkGeneratorsWithSlope);
        }
    }

    private static void createVoltageControl(LfBus controlledBus, LfBus controllerBus, double controllerTargetV, List<VoltageControl> voltageControls, boolean voltagePerReactivePowerControl) {
        VoltageControl voltageControl = new VoltageControl(controlledBus, controllerTargetV);
        voltageControl.addControllerBus(controllerBus);
        controlledBus.setVoltageControl(voltageControl);
        if (voltagePerReactivePowerControl) {
            voltageControls.add(voltageControl);
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

    private static void checkUniqueControlledBus(LfBus controlledBus, LfBus controlledBusGen, LfBus controller) {
        Objects.requireNonNull(controlledBus);
        Objects.requireNonNull(controlledBusGen);
        if (controlledBus.getNum() != controlledBusGen.getNum()) {
            String generatorIds = controller.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
            throw new PowsyblException("Generators [" + generatorIds
                + "] connected to bus '" + controller.getId() + "' must control the voltage of the same bus");
        }
    }

    private static void checkUniqueTargetVControllerBus(LfGenerator lfGenerator, double previousTargetV, LfBus controllerBus, LfBus controlledBus) {
        double targetV = lfGenerator.getTargetV();
        if (FastMath.abs(previousTargetV - targetV) > TARGET_V_EPSILON) {
            String generatorIds = controllerBus.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
            LOGGER.error("Generators [{}] are connected to the same bus '{}' with different target voltages: {} (kept) and {} (rejected)",
                generatorIds, controllerBus.getId(), targetV * controlledBus.getNominalV(), previousTargetV * controlledBus.getNominalV());
        }
    }

    private static void createRemoteReactivePowerControl(LfBranch controlledBranch, ReactivePowerControl.ControlledSide side, LfBus controllerBus,
                                                         double targetQ) {
        ReactivePowerControl control = new ReactivePowerControl(controlledBranch, side, controllerBus, targetQ);
        controllerBus.setReactivePowerControl(control);
        controlledBranch.setReactivePowerControl(control);
    }

    private static void createReactivePowerControls(LfNetwork lfNetwork, List<LfBus> lfBuses) {
        for (LfBus controllerBus : lfBuses) {
            List<LfGenerator> generators = controllerBus.getGenerators().stream()
                    .filter(LfGenerator::hasReactivePowerControl).collect(Collectors.toList());
            if (!generators.isEmpty()) {
                Optional<VoltageControl> voltageControl = controllerBus.getVoltageControl();
                if (voltageControl.isPresent()) {
                    LOGGER.warn("Bus {} has both voltage and remote reactive power controls: only voltage control is kept", controllerBus.getId());
                    continue;
                }
                if (generators.size() == 1) {
                    LfGenerator lfGenerator = generators.get(0);
                    LfBranch controlledBranch = lfGenerator.getControlledBranch(lfNetwork);
                    Optional<ReactivePowerControl> control = controlledBranch.getReactivePowerControl();
                    if (control.isPresent()) {
                        LOGGER.warn("Branch {} is remotely controlled by a generator: no new remote reactive control created", controlledBranch.getId());
                    } else {
                        createRemoteReactivePowerControl(lfGenerator.getControlledBranch(lfNetwork), lfGenerator.getControlledBranchSide(), controllerBus, lfGenerator.getRemoteTargetQ());
                    }
                } else { // generators.size() > 1 (as > 0 and not equal to 1)
                    LOGGER.warn("Bus {} has more than one generator controlling reactive power remotely: not yet supported", controllerBus.getId());
                }
            }
        }
    }

    private static Bus getBus(Terminal terminal, boolean breakers) {
        return breakers ? terminal.getBusBreakerView().getBus() : terminal.getBusView().getBus();
    }

    private static LfBusImpl createBus(Bus bus, LfNetworkParameters parameters, LfNetwork lfNetwork, LoadingContext loadingContext,
                                       LfNetworkLoadingReport report, List<LfNetworkLoaderPostProcessor> postProcessors) {
        LfBusImpl lfBus = LfBusImpl.create(bus, lfNetwork, participateToSlackDistribution(parameters, bus));

        bus.visitConnectedEquipments(new DefaultTopologyVisitor() {

            private void visitBranch(Branch branch) {
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
                lfBus.addGenerator(generator, parameters.isBreakers(), report, parameters.getPlausibleActivePowerLimit());
                if (generator.isVoltageRegulatorOn()) {
                    report.voltageControllerCount++;
                }
                postProcessors.forEach(pp -> pp.onInjectionAdded(generator, lfBus));
            }

            @Override
            public void visitLoad(Load load) {
                lfBus.addLoad(load, parameters.isDistributedOnConformLoad());
                postProcessors.forEach(pp -> pp.onInjectionAdded(load, lfBus));
            }

            @Override
            public void visitShuntCompensator(ShuntCompensator sc) {
                lfBus.addShuntCompensator(sc);
                postProcessors.forEach(pp -> pp.onInjectionAdded(sc, lfBus));
            }

            @Override
            public void visitDanglingLine(DanglingLine danglingLine) {
                loadingContext.danglingLines.add(danglingLine);
                DanglingLine.Generation generation = danglingLine.getGeneration();
                if (generation != null && generation.isVoltageRegulationOn()) {
                    report.voltageControllerCount++;
                }
                postProcessors.forEach(pp -> pp.onInjectionAdded(danglingLine, lfBus));
            }

            @Override
            public void visitStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
                lfBus.addStaticVarCompensator(staticVarCompensator, parameters.isVoltagePerReactivePowerControl(), parameters.isBreakers(), report);
                if (staticVarCompensator.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE) {
                    report.voltageControllerCount++;
                }
                postProcessors.forEach(pp -> pp.onInjectionAdded(staticVarCompensator, lfBus));
            }

            @Override
            public void visitBattery(Battery battery) {
                lfBus.addBattery(battery);
                postProcessors.forEach(pp -> pp.onInjectionAdded(battery, lfBus));
            }

            @Override
            public void visitHvdcConverterStation(HvdcConverterStation<?> converterStation) {
                switch (converterStation.getHvdcType()) {
                    case VSC:
                        VscConverterStation vscConverterStation = (VscConverterStation) converterStation;
                        lfBus.addVscConverterStation(vscConverterStation, parameters.isBreakers(), report);
                        if (vscConverterStation.isVoltageRegulatorOn()) {
                            report.voltageControllerCount++;
                        }
                        break;
                    case LCC:
                        lfBus.addLccConverterStation((LccConverterStation) converterStation);
                        break;
                    default:
                        throw new IllegalStateException("Unknown HVDC converter station type: " + converterStation.getHvdcType());
                }
                postProcessors.forEach(pp -> pp.onInjectionAdded(converterStation, lfBus));
            }
        });

        return lfBus;
    }

    private static void addBranch(LfNetwork lfNetwork, LfBranch lfBranch, LfNetworkLoadingReport report) {
        boolean connectedToSameBus = lfBranch.getBus1() == lfBranch.getBus2();
        if (connectedToSameBus) {
            LOGGER.trace("Discard branch '{}' because connected to same bus at both ends", lfBranch.getId());
            report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds++;
        } else {
            if (lfBranch.getPiModel().getZ() == 0) {
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
            LfBranchImpl lfBranch = LfBranchImpl.create(branch, lfNetwork, lfBus1, lfBus2, parameters.isTwtSplitShuntAdmittance(), parameters.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(), report);
            addBranch(lfNetwork, lfBranch, report);
            postProcessors.forEach(pp -> pp.onBranchAdded(branch, lfBranch));
        }

        for (DanglingLine danglingLine : loadingContext.danglingLines) {
            LfDanglingLineBus lfBus2 = new LfDanglingLineBus(lfNetwork, danglingLine, report);
            lfNetwork.addBus(lfBus2);
            lfBuses.add(lfBus2);
            LfBus lfBus1 = getLfBus(danglingLine.getTerminal(), lfNetwork, parameters.isBreakers());
            LfBranch lfBranch = LfDanglingLineBranch.create(danglingLine, lfNetwork, lfBus1, lfBus2);
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
            LfLegBranch lfBranch1 = LfLegBranch.create(lfNetwork, lfBus1, lfBus0, t3wt, t3wt.getLeg1(), parameters.isTwtSplitShuntAdmittance());
            LfLegBranch lfBranch2 = LfLegBranch.create(lfNetwork, lfBus2, lfBus0, t3wt, t3wt.getLeg2(), parameters.isTwtSplitShuntAdmittance());
            LfLegBranch lfBranch3 = LfLegBranch.create(lfNetwork, lfBus3, lfBus0, t3wt, t3wt.getLeg3(), parameters.isTwtSplitShuntAdmittance());
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
                    createPhaseControl(lfNetwork, ptc, branch.getTerminal1(), t2wt.getId(), "", parameters.isBreakers());
                }
            }
            for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
                // Create phase controls which link controller -> controlled
                List<ThreeWindingsTransformer.Leg> legs = t3wt.getLegs();
                for (int legNumber = 0; legNumber < legs.size(); legNumber++) {
                    PhaseTapChanger ptc = legs.get(legNumber).getPhaseTapChanger();
                    createPhaseControl(lfNetwork, ptc, legs.get(legNumber).getTerminal(), t3wt.getId(), "_leg_" + (legNumber + 1), parameters.isBreakers());
                }
            }
        }
    }

    private static void createDiscreteVoltageControls(LfNetwork lfNetwork, boolean breakers, LoadingContext loadingContext) {
        // Create discrete voltage controls which link controller -> controlled
        for (Branch<?> branch : loadingContext.branchSet) {
            if (branch instanceof TwoWindingsTransformer) {
                RatioTapChanger rtc = ((TwoWindingsTransformer) branch).getRatioTapChanger();
                createDiscreteVoltageControl(lfNetwork, rtc, branch.getTerminal1(), branch.getId(), breakers);
            }
        }
        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            List<ThreeWindingsTransformer.Leg> legs = t3wt.getLegs();
            for (int legNumber = 0; legNumber < legs.size(); legNumber++) {
                RatioTapChanger rtc = legs.get(legNumber).getRatioTapChanger();
                createDiscreteVoltageControl(lfNetwork, rtc, legs.get(legNumber).getTerminal(), t3wt.getId() + "_leg_" + (legNumber + 1), breakers);
            }
        }
    }

    private static void createSwitches(List<Switch> switches, LfNetwork lfNetwork, List<LfNetworkLoaderPostProcessor> postProcessors) {
        if (switches != null) {
            for (Switch sw : switches) {
                VoltageLevel vl = sw.getVoltageLevel();
                Bus bus1 = vl.getBusBreakerView().getBus1(sw.getId());
                Bus bus2 = vl.getBusBreakerView().getBus2(sw.getId());
                LfBus lfBus1 = lfNetwork.getBusById(bus1.getId());
                LfBus lfBus2 = lfNetwork.getBusById(bus2.getId());
                LfSwitch lfSwitch = new LfSwitch(lfNetwork, lfBus1, lfBus2, sw);
                lfNetwork.addBranch(lfSwitch);
                postProcessors.forEach(pp -> pp.onBranchAdded(sw, lfSwitch));
            }
        }
    }

    private static void fixAllVoltageControls(LfNetwork lfNetwork, boolean minImpedance, boolean transformerVoltageControl) {
        // If min impedance is set, there is no zero-impedance branch
        if (!minImpedance) {
            // Merge the discrete voltage control in each zero impedance connected set
            List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(lfNetwork.createZeroImpedanceSubGraph()).connectedSets();
            connectedSets.forEach(set -> mergeVoltageControls(set, transformerVoltageControl));
        }
    }

    private static void mergeVoltageControls(Set<LfBus> zeroImpedanceConnectedSet, boolean transformerVoltageControl) {
        // Get the list of voltage controls from controlled buses in the zero impedance connected set
        // Note that the list order is not deterministic as jgrapht connected set computation is using a basic HashSet
        List<VoltageControl> voltageControls = zeroImpedanceConnectedSet.stream().filter(LfBus::isVoltageControlled)
                .map(LfBus::getVoltageControl).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        // Get the list of discrete voltage controls from controlled buses in the zero impedance connected set
        // Note that the list order is not deterministic as jgrapht connected set computation is using a basic HashSet
        List<DiscreteVoltageControl> discreteVoltageControls = !transformerVoltageControl ? Collections.emptyList() :
            zeroImpedanceConnectedSet.stream().filter(LfBus::isDiscreteVoltageControlled)
                .map(LfBus::getDiscreteVoltageControl).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        if (voltageControls.isEmpty() && discreteVoltageControls.size() <= 1) {
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
                checkVcUniqueTargetV(voltageControls);

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
            if (!discreteVoltageControls.isEmpty()) {
                LOGGER.info("Zero impedance connected set with several discrete voltage controls and a voltage control: discrete controls deleted");
                discreteVoltageControls.stream()
                        .flatMap(dvc -> dvc.getControllers().stream())
                        .forEach(controller -> controller.setDiscreteVoltageControl(null));
                discreteVoltageControls.forEach(dvc -> dvc.getControlled().setDiscreteVoltageControl(null));
            }
        } else {
            // We have at least 2 discrete controls whose controlled bus are in the same non-impedant connected set
            // To solve that we keep only one discrete voltage control, the other ones are removed
            // and the corresponding controllers are added to the discrete control kept
            LOGGER.info("Zero impedance connected set with several discrete voltage controls: discrete controls merged");

            // Sort discrete voltage controls to have a merged discrete voltage control with a deterministic controlled bus,
            // a deterministic target value and controller branches in a deterministic order
            discreteVoltageControls.sort(Comparator.comparing(DiscreteVoltageControl::getTargetValue).thenComparing(vc -> vc.getControlled().getId()));
            checkDvcUniqueTargetV(discreteVoltageControls);

            // Merge the controllers into the kept voltage control
            DiscreteVoltageControl keptDiscreteVoltageControl = discreteVoltageControls.remove(discreteVoltageControls.size() - 1);
            discreteVoltageControls.forEach(dvc -> dvc.getControlled().setDiscreteVoltageControl(null));
            discreteVoltageControls.stream()
                .flatMap(dvc -> dvc.getControllers().stream())
                .forEach(controller -> {
                    keptDiscreteVoltageControl.addController(controller);
                    controller.setDiscreteVoltageControl(keptDiscreteVoltageControl);
                });
        }
    }

    private static void checkVcUniqueTargetV(List<VoltageControl> voltageControls) {
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

    private static void checkDvcUniqueTargetV(List<DiscreteVoltageControl> discreteVoltageControls) {
        // To check uniqueness we take the target value which will be kept as reference.
        // The kept target value is the highest, it corresponds to the last discrete voltage control in the ordered list.
        DiscreteVoltageControl dvcRef = discreteVoltageControls.get(discreteVoltageControls.size() - 1);
        boolean uniqueTargetV = discreteVoltageControls.stream().noneMatch(dvc -> FastMath.abs(dvc.getTargetValue() - dvcRef.getTargetValue()) > TARGET_V_EPSILON);
        if (!uniqueTargetV) {
            LOGGER.error("Inconsistent transformer voltage controls: buses {} are in the same non-impedant connected set and are controlled with different target voltages ({}). Only target voltage {} is kept",
                discreteVoltageControls.stream().map(DiscreteVoltageControl::getControlled).collect(Collectors.toList()),
                discreteVoltageControls.stream().map(DiscreteVoltageControl::getTargetValue).collect(Collectors.toList()),
                dvcRef.getTargetValue());
        }
    }

    private static void createPhaseControl(LfNetwork lfNetwork, PhaseTapChanger ptc, Terminal terminal, String controllerBranchId, String legId, boolean breakers) {
        if (ptc != null && ptc.isRegulating() && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {
            String controlledBranchId = ptc.getRegulationTerminal().getConnectable().getId();
            if (controlledBranchId.equals(controllerBranchId)) {
                // Local control: each leg is controlling its phase
                controlledBranchId += legId;
            }
            LfBranch controlledBranch = lfNetwork.getBranchById(controlledBranchId);
            if (controlledBranch == null) {
                LOGGER.warn("Phase controlled branch {} is out of voltage or in a different synchronous component: phase control discarded", controlledBranchId);
                return;
            }
            if (controlledBranch.getBus1() == null || controlledBranch.getBus2() == null) {
                LOGGER.warn("Phase controlled branch {} is open: phase control discarded", controlledBranch.getId());
                return;
            }
            LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId + legId);
            if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
                LOGGER.warn("Phase controller branch {} is open: phase control discarded", controllerBranch.getId());
                return;
            }
            if (ptc.getRegulationTerminal().getBusView().getBus() == null) {
                LOGGER.warn("Regulating terminal of phase controller branch {} is out of voltage: phase control discarded", controllerBranch.getId());
                return;
            }
            LfBus controlledBus = getLfBus(ptc.getRegulationTerminal(), lfNetwork, breakers);
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

    private static void createDiscreteVoltageControl(LfNetwork lfNetwork, RatioTapChanger rtc, Terminal terminal, String controllerBranchId, boolean breakers) {
        if (rtc == null || !rtc.isRegulating() || !rtc.hasLoadTapChangingCapabilities()) {
            return;
        }
        LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId);
        if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
            LOGGER.warn("Voltage controller branch {} is open: voltage control discarded", controllerBranch.getId());
            return;
        }
        LfBus controlledBus = getLfBus(rtc.getRegulationTerminal(), lfNetwork, breakers);
        if (controlledBus == null) {
            LOGGER.warn("Regulating terminal of voltage controller branch {} is out of voltage or in a different synchronous component: voltage control discarded", controllerBranch.getId());
            return;
        }
        if (controlledBus.isVoltageControlled()) {
            LOGGER.warn("Controlled bus {} has both generator and transformer voltage control on: only generator control is kept", controlledBus.getId());
            return;
        }

        Optional<DiscreteVoltageControl> candidateDiscreteVoltageControl = controlledBus.getDiscreteVoltageControl()
            .filter(dvc -> controlledBus.isDiscreteVoltageControlled());
        if (candidateDiscreteVoltageControl.isPresent()) {
            LOGGER.trace("Controlled bus {} already has a transformer voltage control: a shared control is created", controlledBus.getId());
            candidateDiscreteVoltageControl.get().addController(controllerBranch);
            controllerBranch.setDiscreteVoltageControl(candidateDiscreteVoltageControl.get());
        } else {
            double regulatingTerminalNominalV = rtc.getRegulationTerminal().getVoltageLevel().getNominalV();
            DiscreteVoltageControl discreteVoltageControl = new DiscreteVoltageControl(controlledBus,
                DiscreteVoltageControl.Mode.VOLTAGE, rtc.getTargetV() / regulatingTerminalNominalV);
            discreteVoltageControl.addController(controllerBranch);
            controllerBranch.setDiscreteVoltageControl(discreteVoltageControl);
            controlledBus.setDiscreteVoltageControl(discreteVoltageControl);
        }
    }

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork, boolean breakers) {
        Bus bus = getBus(terminal, breakers);
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private static LfNetwork create(int numCC, int numSC, List<Bus> buses, List<Switch> switches, LfNetworkParameters parameters, Reporter reporter) {
        // NameSlackBusSelector is only available for the {CC0,SC0} network so far
        SlackBusSelector selector = (numCC != 0 || numSC != 0) && parameters.getSlackBusSelector() instanceof NameSlackBusSelector ?
            new MostMeshedSlackBusSelector() : parameters.getSlackBusSelector();

        LfNetwork lfNetwork = new LfNetwork(numCC, numSC, selector);

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();
        List<LfNetworkLoaderPostProcessor> postProcessors = Lists.newArrayList(ServiceLoader.load(LfNetworkLoaderPostProcessor.class, LfNetworkLoaderImpl.class.getClassLoader()).iterator());

        List<LfBus> lfBuses = new ArrayList<>();
        createBuses(buses, parameters, lfNetwork, lfBuses, loadingContext, report, postProcessors);
        createBranches(lfBuses, lfNetwork, loadingContext, report, parameters, postProcessors);
        createVoltageControls(lfNetwork, lfBuses, parameters.isGeneratorVoltageRemoteControl(), parameters.isVoltagePerReactivePowerControl());

        if (parameters.isReactivePowerRemoteControl()) {
            createReactivePowerControls(lfNetwork, lfBuses);
        }

        if (parameters.isTransformerVoltageControl()) {
            // Discrete voltage controls need to be created after voltage controls (to test if both generator and transformer voltage control are on)
            createDiscreteVoltageControls(lfNetwork, parameters.isBreakers(), loadingContext);
        }

        if (parameters.isBreakers()) {
            createSwitches(switches, lfNetwork, postProcessors);
        }

        // Fixing voltage controls need to be done after creating switches, as the zero-impedance graph is changed with switches
        fixAllVoltageControls(lfNetwork, parameters.isMinImpedance(), parameters.isTransformerVoltageControl());

        if (!parameters.isMinImpedance()) {
            // create zero impedance equations only on minimum spanning forest calculated from zero impedance sub graph
            Graph<LfBus, LfBranch> zeroImpedanceSubGraph = lfNetwork.createZeroImpedanceSubGraph();
            if (!zeroImpedanceSubGraph.vertexSet().isEmpty()) {
                SpanningTreeAlgorithm.SpanningTree<LfBranch> spanningTree = new KruskalMinimumSpanningTree<>(zeroImpedanceSubGraph).getSpanningTree();
                for (LfBranch branch : spanningTree.getEdges()) {
                    branch.setSpanningTreeEdge(true);
                }
            }
        }

        if (report.generatorsDiscardedFromVoltageControlBecauseNotStarted > 0) {
            reporter.report(Report.builder()
                .withKey("notStartedGenerators")
                .withDefaultMessage("${nbGenImpacted} generators have been discarded from voltage control because not started")
                .withValue("nbGenImpacted", report.generatorsDiscardedFromVoltageControlBecauseNotStarted)
                .build());
            LOGGER.warn("Network {}: {} generators have been discarded from voltage control because not started",
                    lfNetwork, report.generatorsDiscardedFromVoltageControlBecauseNotStarted);
        }
        if (report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall > 0) {
            reporter.report(Report.builder()
                .withKey("smallReactiveRangeGenerators")
                .withDefaultMessage("${nbGenImpacted} generators have been discarded from voltage control because of a too small max reactive range")
                .withValue("nbGenImpacted", report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall)
                .build());
            LOGGER.warn("Network {}: {} generators have been discarded from voltage control because of a too small max reactive range",
                    lfNetwork, report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of a targetP equals 0",
                    lfNetwork, report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of a targetP > maxP",
                    lfNetwork, report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP);
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

        if (report.voltageControllerCount == 0) {
            LOGGER.error("Discard network {} because there is no equipment to control voltage", lfNetwork);
            lfNetwork.setValid(false);
        }

        return lfNetwork;
    }

    @Override
    public List<LfNetwork> load(Network network, LfNetworkParameters parameters, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);

        Stopwatch stopwatch = Stopwatch.createStarted();

        Map<Pair<Integer, Integer>, List<Bus>> busesByCc = new TreeMap<>();
        Iterable<Bus> buses = parameters.isBreakers() ? network.getBusBreakerView().getBuses()
                                                      : network.getBusView().getBuses();
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
                .map(e -> create(e.getKey().getLeft(), e.getKey().getRight(), e.getValue(), switchesByCc.get(e.getKey()), parameters,
                    reporter.createSubReporter("createLfNetwork", "Create network ${networkNum}", "networkNum", e.getKey().getLeft())))
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
