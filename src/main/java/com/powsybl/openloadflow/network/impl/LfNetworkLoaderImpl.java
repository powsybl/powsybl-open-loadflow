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
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import net.jafama.FastMath;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private static void createVoltageControls(LfNetwork lfNetwork, List<LfBus> lfBuses, boolean voltageRemoteControl, boolean breakers) {
        // set controller -> controlled link
        for (LfBus controllerBus : lfBuses) {

            LfBus controlledBus = null;
            double controllerTargetV = Double.NaN;

            for (LfGenerator lfGenerator : controllerBus.getGenerators()) {
                LfBus generatorControlledBus = lfNetwork.getBusById(lfGenerator.getControlledBusId(breakers));

                // check that remote control bus is the same for all generators of current controller bus
                checkUniqueControlledBus(controlledBus, generatorControlledBus, controllerBus);

                // check target voltage
                checkUniqueTargetVControllerBus(lfGenerator, controllerTargetV, controllerBus, generatorControlledBus);
                controllerTargetV = lfGenerator.getTargetV(); // in per-unit system

                if (lfGenerator.hasVoltageControl()) {
                    controlledBus = generatorControlledBus;
                }
            }

            if (controlledBus != null) {

                if (!voltageRemoteControl && controlledBus != controllerBus) {
                    // if voltage remote control deactivated and remote control, set local control instead
                    LOGGER.warn("Remote voltage control is not activated. The voltage target of {} with remote control is rescaled from {} to {}",
                        controllerBus.getId(), controllerTargetV, controllerTargetV * controllerBus.getNominalV() / controlledBus.getNominalV());
                    controlledBus = controllerBus;
                }

                VoltageControl voltageControl = controlledBus.getVoltageControl().orElse(new VoltageControl(controlledBus, controllerTargetV));
                voltageControl.addControllerBus(controllerBus);

                controlledBus.setVoltageControl(voltageControl); // is set even if already present, for simplicity sake
                checkUniqueTargetVControlledBus(controllerTargetV, controllerBus, voltageControl); // check even if voltage control just created, for simplicity sake
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
                    controllerBus.getId(), controlledBus.getId(), busesId,
                    voltageControlTargetV * controlledBus.getNominalV(), controllerTargetV * controlledBus.getNominalV());
        }
    }

    private static void checkUniqueControlledBus(LfBus controlledBus, LfBus controlledBusGen, LfBus controller) {
        Objects.requireNonNull(controlledBusGen);
        if (controlledBus != null && controlledBus.getNum() != controlledBusGen.getNum()) {
            String generatorIds = controller.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
            throw new PowsyblException("Generators [" + generatorIds
                + "] connected to bus '" + controller.getId() + "' must control the voltage of the same bus");
        }
    }

    private static void checkUniqueTargetVControllerBus(LfGenerator lfGenerator, double previousTargetV, LfBus controllerBus, LfBus controlledBus) {
        double targetV = lfGenerator.getTargetV();
        if (!Double.isNaN(previousTargetV) && FastMath.abs(previousTargetV - targetV) > TARGET_V_EPSILON) {
            String generatorIds = controllerBus.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
            throw new PowsyblException("Generators [" + generatorIds + "] are connected to the same bus '" + controllerBus.getId()
                + "' with a different target voltages: " + targetV * controlledBus.getNominalV() + " and " + previousTargetV * controlledBus.getNominalV());
        }
    }

    private static Bus getBus(Terminal terminal, boolean breakers) {
        return breakers ? terminal.getBusBreakerView().getBus() : terminal.getBusView().getBus();
    }

    private static LfBusImpl createBus(Bus bus, LfNetworkParameters parameters, LfNetwork lfNetwork, LoadingContext loadingContext,
                                       LfNetworkLoadingReport report) {
        LfBusImpl lfBus = LfBusImpl.create(bus, lfNetwork);

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
                lfBus.addGenerator(generator, report, parameters.getPlausibleActivePowerLimit());
                if (generator.isVoltageRegulatorOn()) {
                    report.voltageControllerCount++;
                }
            }

            @Override
            public void visitLoad(Load load) {
                lfBus.addLoad(load);
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
                lfBus.addStaticVarCompensator(staticVarCompensator, report);
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
                        lfBus.addVscConverterStation(vscConverterStation, report);
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

        for (Branch<?> branch : loadingContext.branchSet) {
            if (branch instanceof TwoWindingsTransformer) {
                // Create phase controls which link controller -> controlled
                TwoWindingsTransformer t2wt = (TwoWindingsTransformer) branch;
                PhaseTapChanger ptc = t2wt.getPhaseTapChanger();
                createPhaseControl(lfNetwork, ptc, t2wt.getId(), "", parameters.isBreakers());
            }
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

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            // Create phase controls which link controller -> controlled
            List<ThreeWindingsTransformer.Leg> legs = t3wt.getLegs();
            for (int legNumber = 0; legNumber < legs.size(); legNumber++) {
                PhaseTapChanger ptc = legs.get(legNumber).getPhaseTapChanger();
                createPhaseControl(lfNetwork, ptc, t3wt.getId(), "_leg_" + (legNumber + 1), parameters.isBreakers());
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

    private static void fixDiscreteVoltageControls(LfNetwork lfNetwork, boolean minImpedance) {
        // If min impedance is set, there is no zero-impedance branch
        if (!minImpedance) {
            // Merge the discrete voltage control in each zero impedance connected set
            List<Set<LfBus>> connectedSets = new ConnectivityInspector<>(lfNetwork.createZeroImpedanceSubGraph()).connectedSets();
            connectedSets.forEach(LfNetworkLoaderImpl::fixDiscreteVoltageControlsOnConnectedComponent);
        }
    }

    private static void fixDiscreteVoltageControlsOnConnectedComponent(Set<LfBus> zeroImpedanceConnectedSet) {
        // Get the list of discrete controlled buses in the zero impedance connected set
        List<LfBus> discreteControlledBuses = zeroImpedanceConnectedSet.stream().filter(LfBus::isDiscreteVoltageControlled).collect(Collectors.toList());
        if (discreteControlledBuses.isEmpty()) {
            return;
        }

        // The first controlled bus is kept and removed from the list
        LfBus firstControlledBus = discreteControlledBuses.remove(0);

        // First resolve problem of discrete voltage controls
        if (!discreteControlledBuses.isEmpty()) {
            // We have several discrete controls whose controlled bus are in the same non-impedant connected set
            // To solve that we keep only one discrete voltage control, the other ones are removed
            // and the corresponding controllers are added to the discrete control kept
            LOGGER.info("Zero impedance connected set with several discrete voltage controls: discrete controls merged");
            DiscreteVoltageControl firstDvc = firstControlledBus.getDiscreteVoltageControl();
            discreteControlledBuses.stream()
                .flatMap(c -> c.getDiscreteVoltageControl().getControllers().stream())
                .forEach(controller -> {
                    firstDvc.addController(controller);
                    controller.setDiscreteVoltageControl(firstDvc);
                });
            discreteControlledBuses.forEach(lfBus -> lfBus.setDiscreteVoltageControl(null));
        }

        // Then resolve problem of mixed shared controls, that is if there are any generator/svc voltage control together with discrete voltage control(s)
        // Check if there is one bus with remote voltage control or local voltage control
        boolean hasControlledBus = zeroImpedanceConnectedSet.stream().anyMatch(LfBus::isVoltageControlled);
        if (hasControlledBus) {
            // If any generator/svc voltage controls, remove the merged discrete voltage control
            // TODO: deal with mixed shared controls instead of removing all discrete voltage controls
            LOGGER.warn("Zero impedance connected set with voltage control and discrete voltage control: only generator control is kept");
            // First remove it from controllers
            firstControlledBus.getDiscreteVoltageControl().getControllers().forEach(c -> c.setDiscreteVoltageControl(null));
            // Then remove it from the controlled lfBus
            firstControlledBus.setDiscreteVoltageControl(null);
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
        if (rtc != null && rtc.isRegulating() && rtc.hasLoadTapChangingCapabilities()) {
            LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId);
            if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
                LOGGER.warn("Voltage controller branch {} is open: no voltage control created", controllerBranch.getId());
                return;
            }
            Terminal regulationTerminal = rtc.getRegulationTerminal();
            if (regulationTerminal.getBusView().getBus() == null) {
                LOGGER.warn("Regulating terminal of voltage controller branch {} is out of voltage: no voltage control created", controllerBranch.getId());
                return;
            }
            LfBus controlledBus = getLfBus(rtc.getRegulationTerminal(), lfNetwork, breakers);
            if (controlledBus != null) {
                if (controlledBus.isVoltageControlled()) {
                    LOGGER.warn("Controlled bus {} has both generator and transformer voltage control on: only generator control is kept", controlledBus.getId());
                } else if (controlledBus.isDiscreteVoltageControlled()) {
                    LOGGER.trace("Controlled bus {} already has a transformer voltage control: a shared control is created", controlledBus.getId());
                    controlledBus.getDiscreteVoltageControl().addController(controllerBranch);
                    controllerBranch.setDiscreteVoltageControl(controlledBus.getDiscreteVoltageControl());
                } else {
                    double regulatingTerminalNominalV = regulationTerminal.getVoltageLevel().getNominalV();
                    DiscreteVoltageControl voltageControl = new DiscreteVoltageControl(controlledBus,
                            DiscreteVoltageControl.Mode.VOLTAGE, rtc.getTargetV() / regulatingTerminalNominalV);
                    voltageControl.addController(controllerBranch);
                    controllerBranch.setDiscreteVoltageControl(voltageControl);
                    controlledBus.setDiscreteVoltageControl(voltageControl);
                }
            }
        }
    }

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork, boolean breakers) {
        Bus bus = getBus(terminal, breakers);
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private static LfNetwork create(MutableInt num, List<Bus> buses, List<Switch> switches, LfNetworkParameters parameters) {
        LfNetwork lfNetwork = new LfNetwork(num.getValue(), parameters.getSlackBusSelector());
        num.increment();

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();

        List<LfBus> lfBuses = new ArrayList<>();
        createBuses(buses, parameters, lfNetwork, lfBuses, loadingContext, report);
        createBranches(lfBuses, lfNetwork, loadingContext, report, parameters);
        createVoltageControls(lfNetwork, lfBuses, parameters.isGeneratorVoltageRemoteControl(), parameters.isBreakers());

        // Discrete voltage controls need to be created after voltage controls (to test if both generator and transformer voltage control are on)
        createDiscreteVoltageControls(lfNetwork, parameters.isBreakers(), loadingContext);

        createSwitches(switches, lfNetwork);

        // Fixing discrete voltage controls need to be done after creating switches, as the zero-impedance graph is changed with switches
        fixDiscreteVoltageControls(lfNetwork, parameters.isMinImpedance());

        if (report.generatorsDiscardedFromVoltageControlBecauseNotStarted > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from voltage control because not started",
                    lfNetwork.getNum(), report.generatorsDiscardedFromVoltageControlBecauseNotStarted);
        }
        if (report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from voltage control because of a too small max reactive range",
                    lfNetwork.getNum(), report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of a targetP equals 0",
                    lfNetwork.getNum(), report.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of a targetP > maxP",
                    lfNetwork.getNum(), report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of maxP not plausible",
                    lfNetwork.getNum(), report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of maxP equals to minP",
                    lfNetwork.getNum(), report.generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP);
        }
        if (report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds > 0) {
            LOGGER.warn("Network {}: {} branches have been discarded because connected to same bus at both ends",
                    lfNetwork.getNum(), report.branchesDiscardedBecauseConnectedToSameBusAtBothEnds);
        }
        if (report.linesWithDifferentNominalVoltageAtBothEnds > 0) {
            LOGGER.warn("Network {}: {} lines have a different nominal voltage at both ends: a ratio has been added",
                    lfNetwork.getNum(), report.linesWithDifferentNominalVoltageAtBothEnds);
        }
        if (report.nonImpedantBranches > 0) {
            LOGGER.warn("Network {}: {} branches are non impedant", lfNetwork.getNum(), report.nonImpedantBranches);
        }

        if (report.voltageControllerCount == 0) {
            LOGGER.error("Discard network {} because there is no equipment to control voltage", lfNetwork.getNum());
            return null;
        }

        return lfNetwork;
    }

    @Override
    public Optional<List<LfNetwork>> load(Object network, LfNetworkParameters parameters) {
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

            MutableInt num = new MutableInt(0);
            List<LfNetwork> lfNetworks = busesByCc.entrySet().stream()
                    .filter(e -> e.getKey().getLeft() == ComponentConstants.MAIN_NUM)
                    .map(e -> create(num, e.getValue(), switchesByCc.get(e.getKey()), parameters))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            stopwatch.stop();
            LOGGER.debug(PERFORMANCE_MARKER, "LF networks created in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return Optional.of(lfNetworks);
        }

        return Optional.empty();
    }
}
