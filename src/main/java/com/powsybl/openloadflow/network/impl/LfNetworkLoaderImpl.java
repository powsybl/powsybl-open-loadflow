/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import net.jafama.FastMath;
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
@AutoService(LfNetworkLoader.class)
public class LfNetworkLoaderImpl implements LfNetworkLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetworkLoaderImpl.class);

    private static final double TARGET_V_EPSILON = 1e-2;

    private static class LoadingContext {

        private final Set<Branch> branchSet = new LinkedHashSet<>();

        private final List<DanglingLine> danglingLines = new ArrayList<>();

        private final Set<ThreeWindingsTransformer> t3wtSet = new LinkedHashSet<>();
    }

    private static void createBuses(List<Bus> buses, LfNetworkParameters parameters, LfNetwork lfNetwork, List<LfBus> lfBuses,
                                    LoadingContext loadingContext, LfNetworkLoadingReport report) {
        for (Bus bus : buses) {
            LfBusImpl lfBus = createBus(bus, parameters, lfNetwork, loadingContext, report);
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

    private static Bus getBus(Terminal terminal, boolean breakers) {
        return breakers ? terminal.getBusBreakerView().getBus() : terminal.getBusView().getBus();
    }

    private static LfBusImpl createBus(Bus bus, LfNetworkParameters parameters, LfNetwork lfNetwork, LoadingContext loadingContext,
                                       LfNetworkLoadingReport report) {
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
            }

            @Override
            public void visitLoad(Load load) {
                lfBus.addLoad(load, parameters.isDistributedOnConformLoad());
            }

            @Override
            public void visitShuntCompensator(ShuntCompensator sc) {
                lfBus.addShuntCompensator(sc);
            }

            @Override
            public void visitDanglingLine(DanglingLine danglingLine) {
                loadingContext.danglingLines.add(danglingLine);
                DanglingLine.Generation generation = danglingLine.getGeneration();
                if (generation != null && generation.isVoltageRegulationOn()) {
                    report.voltageControllerCount++;
                }
            }

            @Override
            public void visitStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
                lfBus.addStaticVarCompensator(staticVarCompensator, parameters.isVoltagePerReactivePowerControl(), parameters.isBreakers(), report);
                if (staticVarCompensator.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE) {
                    report.voltageControllerCount++;
                }
            }

            @Override
            public void visitBattery(Battery battery) {
                lfBus.addBattery(battery);
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

    private static void createBranches(List<LfBus> lfBuses, LfNetwork lfNetwork, LoadingContext loadingContext, LfNetworkLoadingReport report, LfNetworkParameters parameters) {
        for (Branch<?> branch : loadingContext.branchSet) {
            LfBus lfBus1 = getLfBus(branch.getTerminal1(), lfNetwork, parameters.isBreakers());
            LfBus lfBus2 = getLfBus(branch.getTerminal2(), lfNetwork, parameters.isBreakers());
            addBranch(lfNetwork, LfBranchImpl.create(branch, lfNetwork, lfBus1, lfBus2, parameters.isTwtSplitShuntAdmittance(), parameters.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(), report), report);
        }

        for (DanglingLine danglingLine : loadingContext.danglingLines) {
            LfDanglingLineBus lfBus2 = new LfDanglingLineBus(lfNetwork, danglingLine, report);
            lfNetwork.addBus(lfBus2);
            lfBuses.add(lfBus2);
            LfBus lfBus1 = getLfBus(danglingLine.getTerminal(), lfNetwork, parameters.isBreakers());
            addBranch(lfNetwork, LfDanglingLineBranch.create(danglingLine, lfNetwork, lfBus1, lfBus2), report);
        }

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            LfStarBus lfBus0 = new LfStarBus(lfNetwork, t3wt);
            lfNetwork.addBus(lfBus0);
            LfBus lfBus1 = getLfBus(t3wt.getLeg1().getTerminal(), lfNetwork, parameters.isBreakers());
            LfBus lfBus2 = getLfBus(t3wt.getLeg2().getTerminal(), lfNetwork, parameters.isBreakers());
            LfBus lfBus3 = getLfBus(t3wt.getLeg3().getTerminal(), lfNetwork, parameters.isBreakers());
            addBranch(lfNetwork, LfLegBranch.create(lfNetwork, lfBus1, lfBus0, t3wt, t3wt.getLeg1(), parameters.isTwtSplitShuntAdmittance()), report);
            addBranch(lfNetwork, LfLegBranch.create(lfNetwork, lfBus2, lfBus0, t3wt, t3wt.getLeg2(), parameters.isTwtSplitShuntAdmittance()), report);
            addBranch(lfNetwork, LfLegBranch.create(lfNetwork, lfBus3, lfBus0, t3wt, t3wt.getLeg3(), parameters.isTwtSplitShuntAdmittance()), report);
        }

        if (parameters.isPhaseControl()) {
            for (Branch<?> branch : loadingContext.branchSet) {
                if (branch instanceof TwoWindingsTransformer) {
                    // Create phase controls which link controller -> controlled
                    TwoWindingsTransformer t2wt = (TwoWindingsTransformer) branch;
                    PhaseTapChanger ptc = t2wt.getPhaseTapChanger();
                    createPhaseControl(lfNetwork, ptc, t2wt.getId(), "", parameters.isBreakers());
                }
            }
            for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
                // Create phase controls which link controller -> controlled
                List<ThreeWindingsTransformer.Leg> legs = t3wt.getLegs();
                for (int legNumber = 0; legNumber < legs.size(); legNumber++) {
                    PhaseTapChanger ptc = legs.get(legNumber).getPhaseTapChanger();
                    createPhaseControl(lfNetwork, ptc, t3wt.getId(), "_leg_" + (legNumber + 1), parameters.isBreakers());
                }
            }
        }
    }

    private static void createDiscreteVoltageControls(LfNetwork lfNetwork, boolean breakers, LoadingContext loadingContext) {
        // Create discrete voltage controls which link controller -> controlled
        for (Branch<?> branch : loadingContext.branchSet) {
            if (branch instanceof TwoWindingsTransformer) {
                RatioTapChanger rtc = ((TwoWindingsTransformer) branch).getRatioTapChanger();
                createDiscreteVoltageControl(lfNetwork, rtc, branch.getId(), breakers);
            }
        }
        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            List<ThreeWindingsTransformer.Leg> legs = t3wt.getLegs();
            for (int legNumber = 0; legNumber < legs.size(); legNumber++) {
                RatioTapChanger rtc = legs.get(legNumber).getRatioTapChanger();
                createDiscreteVoltageControl(lfNetwork, rtc, t3wt.getId() + "_leg_" + (legNumber + 1), breakers);
            }
        }
    }

    private static void createSwitches(List<Switch> switches, LfNetwork lfNetwork) {
        if (switches != null) {
            for (Switch sw : switches) {
                VoltageLevel vl = sw.getVoltageLevel();
                Bus bus1 = vl.getBusBreakerView().getBus1(sw.getId());
                Bus bus2 = vl.getBusBreakerView().getBus2(sw.getId());
                LfBus lfBus1 = lfNetwork.getBusById(bus1.getId());
                LfBus lfBus2 = lfNetwork.getBusById(bus2.getId());
                lfNetwork.addBranch(new LfSwitch(lfNetwork, lfBus1, lfBus2, sw));
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
        List<VoltageControl> voltageControls = zeroImpedanceConnectedSet.stream().filter(LfBus::isVoltageControlled)
                .map(LfBus::getVoltageControl).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        // Get the list of discrete voltage controls from controlled buses in the zero impedance connected set
        List<DiscreteVoltageControl> discreteVoltageControls = !transformerVoltageControl ? Collections.emptyList() :
            zeroImpedanceConnectedSet.stream().filter(LfBus::isDiscreteVoltageControlled)
                .map(LfBus::getDiscreteVoltageControl).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        if (voltageControls.isEmpty() && discreteVoltageControls.isEmpty()) {
            return;
        }

        if (!voltageControls.isEmpty()) {
            // The first voltage control is kept and removed from the list
            VoltageControl firstVoltageControl = voltageControls.remove(0);

            // First resolve problem of voltage controls (generator, static var compensator, etc.)
            // We have several controls whose controlled bus are in the same non-impedant connected set
            // To solve that we keep only one voltage control (and its target value), the other ones are removed
            // and the corresponding controllers are added to the control kept
            LOGGER.info("Zero impedance connected set with several voltage controls: controls are merged");
            voltageControls.stream()
                    .flatMap(vc -> vc.getControllerBuses().stream())
                    .forEach(controller -> {
                        firstVoltageControl.addControllerBus(controller);
                        controller.setVoltageControl(firstVoltageControl);
                    });
            voltageControls.stream()
                    .filter(vc -> !firstVoltageControl.getControllerBuses().contains(vc.getControlledBus()))
                    .forEach(vc -> vc.getControlledBus().removeVoltageControl());

            // Second, we have to remove all the discrete voltage controls if present.
            if (!discreteVoltageControls.isEmpty()) {
                LOGGER.info("Zero impedance connected set with several discrete voltage controls and a voltage control: discrete controls deleted");
                discreteVoltageControls.stream()
                        .flatMap(dvc -> dvc.getControllers().stream())
                        .forEach(controller -> controller.setDiscreteVoltageControl(null));
                discreteVoltageControls.forEach(dvc -> dvc.getControlled().setDiscreteVoltageControl(null));
            }
        } else {
            if (!discreteVoltageControls.isEmpty()) {
                // The first discrete voltage control is kept and removed from the list
                DiscreteVoltageControl firstDiscreteVoltageControl = discreteVoltageControls.remove(0);
                // We have several discrete controls whose controlled bus are in the same non-impedant connected set
                // To solve that we keep only one discrete voltage control, the other ones are removed
                // and the corresponding controllers are added to the discrete control kept
                LOGGER.info("Zero impedance connected set with several discrete voltage controls: discrete controls merged");
                discreteVoltageControls.stream()
                        .flatMap(dvc -> dvc.getControllers().stream())
                        .forEach(controller -> {
                            firstDiscreteVoltageControl.addController(controller);
                            controller.setDiscreteVoltageControl(firstDiscreteVoltageControl);
                        });
                discreteVoltageControls.forEach(dvc -> dvc.getControlled().setDiscreteVoltageControl(null));
            }
        }
    }

    private static void createPhaseControl(LfNetwork lfNetwork, PhaseTapChanger ptc, String controllerBranchId, String legId, boolean breakers) {
        if (ptc != null && ptc.isRegulating() && ptc.getRegulationMode() != PhaseTapChanger.RegulationMode.FIXED_TAP) {
            String controlledBranchId = ptc.getRegulationTerminal().getConnectable().getId();
            if (controlledBranchId.equals(controllerBranchId)) {
                // Local control: each leg is controlling its phase
                controlledBranchId += legId;
            }
            LfBranch controlledBranch = lfNetwork.getBranchById(controlledBranchId);
            if (controlledBranch == null) {
                LOGGER.warn("Phase controlled branch {} is null: no phase control created", controlledBranchId);
                return;
            }
            if (controlledBranch.getBus1() == null || controlledBranch.getBus2() == null) {
                LOGGER.warn("Phase controlled branch {} is open: no phase control created", controlledBranch.getId());
                return;
            }
            LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId + legId);
            if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
                LOGGER.warn("Phase controller branch {} is open: no phase control created", controllerBranch.getId());
                return;
            }
            if (ptc.getRegulationTerminal().getBusView().getBus() == null) {
                LOGGER.warn("Regulating terminal of phase controller branch {} is out of voltage: no phase control created", controllerBranch.getId());
                return;
            }
            LfBus controlledBus = getLfBus(ptc.getRegulationTerminal(), lfNetwork, breakers);
            DiscretePhaseControl.ControlledSide controlledSide = controlledBus == controlledBranch.getBus1() ?
                    DiscretePhaseControl.ControlledSide.ONE : DiscretePhaseControl.ControlledSide.TWO;
            if (controlledBranch instanceof LfLegBranch && controlledBus == controlledBranch.getBus2()) {
                throw new IllegalStateException("Leg " + controlledBranch.getId() + " has a non supported control at star bus side");
            }
            double targetValue = ptc.getRegulationValue() / PerUnit.SB;
            double targetDeadband = ptc.getTargetDeadband() / PerUnit.SB;
            DiscretePhaseControl phaseControl = null;
            if (ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.CURRENT_LIMITER) {
                phaseControl = new DiscretePhaseControl(controllerBranch, controlledBranch, controlledSide,
                        DiscretePhaseControl.Mode.LIMITER, targetValue, targetDeadband, DiscretePhaseControl.Unit.A);
            } else if (ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                phaseControl = new DiscretePhaseControl(controllerBranch, controlledBranch, controlledSide,
                        DiscretePhaseControl.Mode.CONTROLLER, targetValue, targetDeadband, DiscretePhaseControl.Unit.MW);
            }
            controllerBranch.setDiscretePhaseControl(phaseControl);
            controlledBranch.setDiscretePhaseControl(phaseControl);
        }
    }

    private static void createDiscreteVoltageControl(LfNetwork lfNetwork, RatioTapChanger rtc, String controllerBranchId, boolean breakers) {
        if (rtc == null || !rtc.isRegulating() || !rtc.hasLoadTapChangingCapabilities()) {
            return;
        }
        LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId);
        if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
            LOGGER.warn("Voltage controller branch {} is open: no voltage control created", controllerBranch.getId());
            return;
        }
        LfBus controlledBus = getLfBus(rtc.getRegulationTerminal(), lfNetwork, breakers);
        if (controlledBus == null) {
            LOGGER.warn("Regulating terminal of voltage controller branch {} is out of voltage: no voltage control created", controllerBranch.getId());
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

        List<LfBus> lfBuses = new ArrayList<>();
        createBuses(buses, parameters, lfNetwork, lfBuses, loadingContext, report);
        createBranches(lfBuses, lfNetwork, loadingContext, report, parameters);
        createVoltageControls(lfNetwork, lfBuses, parameters.isGeneratorVoltageRemoteControl(), parameters.isVoltagePerReactivePowerControl());

        if (parameters.isTransformerVoltageControl()) {
            // Discrete voltage controls need to be created after voltage controls (to test if both generator and transformer voltage control are on)
            createDiscreteVoltageControls(lfNetwork, parameters.isBreakers(), loadingContext);
        }

        if (parameters.isBreakers()) {
            createSwitches(switches, lfNetwork);
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
    public Optional<List<LfNetwork>> load(Object network, LfNetworkParameters parameters, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);

        if (network instanceof Network) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<Pair<Integer, Integer>, List<Bus>> busesByCc = new TreeMap<>();
            Iterable<Bus> buses = parameters.isBreakers() ? ((Network) network).getBusBreakerView().getBuses()
                                                          : ((Network) network).getBusView().getBuses();
            for (Bus bus : buses) {
                Component cc = bus.getConnectedComponent();
                Component sc = bus.getSynchronousComponent();
                if (cc != null && sc != null) {
                    busesByCc.computeIfAbsent(Pair.of(cc.getNum(), sc.getNum()), k -> new ArrayList<>()).add(bus);
                }
            }

            Map<Pair<Integer, Integer>, List<Switch>> switchesByCc = new HashMap<>();
            if (parameters.isBreakers()) {
                for (VoltageLevel vl : ((Network) network).getVoltageLevels()) {
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

            return Optional.of(lfNetworks);
        }

        return Optional.empty();
    }

    static boolean participateToSlackDistribution(LfNetworkParameters parameters, Bus b) {
        return parameters.getCountriesToBalance().isEmpty()
               || b.getVoltageLevel().getSubstation().getCountry()
                   .map(country -> parameters.getCountriesToBalance().contains(country))
                   .orElse(false);
    }
}
