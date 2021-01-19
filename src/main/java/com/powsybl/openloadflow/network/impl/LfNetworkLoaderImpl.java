/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.Pseudograph;
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

    private static class LoadingContext {

        private final Set<Branch> branchSet = new LinkedHashSet<>();

        private final List<DanglingLine> danglingLines = new ArrayList<>();

        private final Set<ThreeWindingsTransformer> t3wtSet = new LinkedHashSet<>();
    }

    private static void createBuses(List<Bus> buses, boolean voltageRemoteControl, boolean breakers, LfNetwork lfNetwork,
                                    LoadingContext loadingContext, LfNetworkLoadingReport report) {
        Map<LfBusImpl, String> controllerBusToControlledBusId = new LinkedHashMap<>();

        for (Bus bus : buses) {
            LfBusImpl lfBus = createBus(bus, voltageRemoteControl, breakers, loadingContext, report, controllerBusToControlledBusId);
            lfNetwork.addBus(lfBus);
        }

        // set controller -> controlled link
        for (Map.Entry<LfBusImpl, String> e : controllerBusToControlledBusId.entrySet()) {
            LfBusImpl controllerBus = e.getKey();
            String controlledBusId = e.getValue();
            LfBus controlledBus = lfNetwork.getBusById(controlledBusId);
            controllerBus.setControlledBus((LfBusImpl) controlledBus);
        }
    }

    private static Bus getBus(Terminal terminal, boolean breakers) {
        return breakers ? terminal.getBusBreakerView().getBus() : terminal.getBusView().getBus();
    }

    private static LfBusImpl createBus(Bus bus, boolean voltageRemoteControl, boolean breakers, LoadingContext loadingContext,
                                       LfNetworkLoadingReport report, Map<LfBusImpl, String> controllerBusToControlledBusId) {
        LfBusImpl lfBus = LfBusImpl.create(bus);

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

            private double checkVoltageRemoteControl(Injection injection, Terminal regulatingTerminal, double previousTargetV) {
                double scaleV = 1;
                Bus controlledBus = getBus(regulatingTerminal, breakers);
                Bus connectedBus = getBus(injection.getTerminal(), breakers);
                if (controlledBus == null || connectedBus == null) {
                    return scaleV;
                }
                String controlledBusId = controlledBus.getId();
                String connectedBusId = connectedBus.getId();
                if (!Objects.equals(controlledBusId, connectedBusId)) {
                    if (voltageRemoteControl) {
                        // controller to controlled bus link will be set later because controlled bus might not have
                        // been yet created
                        controllerBusToControlledBusId.put(lfBus, controlledBusId);
                    } else {
                        double remoteNominalV = regulatingTerminal.getVoltageLevel().getNominalV();
                        double localNominalV = injection.getTerminal().getVoltageLevel().getNominalV();
                        scaleV = localNominalV / remoteNominalV;
                        LOGGER.warn("Remote voltage control is not activated. The voltage target of " +
                                        "{} ({}) with remote control is rescaled from {} to {}",
                                injection.getId(), injection.getType(), previousTargetV, previousTargetV * scaleV);
                    }
                }
                return scaleV;
            }

            @Override
            public void visitGenerator(Generator generator) {
                double scaleV = checkVoltageRemoteControl(generator, generator.getRegulatingTerminal(), generator.getTargetV());
                lfBus.addGenerator(generator, scaleV, report);
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
                double scaleV = checkVoltageRemoteControl(staticVarCompensator, staticVarCompensator.getRegulatingTerminal(),
                        staticVarCompensator.getVoltageSetPoint());
                lfBus.addStaticVarCompensator(staticVarCompensator, scaleV, report);
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

    private static void createBranches(LfNetwork lfNetwork, LoadingContext loadingContext, LfNetworkLoadingReport report,
                                       boolean twtSplitShuntAdmittance, boolean breakers) {
        for (Branch<?> branch : loadingContext.branchSet) {
            LfBus lfBus1 = getLfBus(branch.getTerminal1(), lfNetwork, breakers);
            LfBus lfBus2 = getLfBus(branch.getTerminal2(), lfNetwork, breakers);
            addBranch(lfNetwork, LfBranchImpl.create(branch, lfBus1, lfBus2, twtSplitShuntAdmittance), report);
        }

        for (Branch<?> branch : loadingContext.branchSet) {
            if (branch instanceof TwoWindingsTransformer) {
                // Create phase controls which link controller -> controlled
                TwoWindingsTransformer t2wt = (TwoWindingsTransformer) branch;
                PhaseTapChanger ptc = t2wt.getPhaseTapChanger();
                createPhaseControl(lfNetwork, ptc, t2wt.getId(), "");
                RatioTapChanger rtc = t2wt.getRatioTapChanger();
                createVoltageControl(lfNetwork, rtc, t2wt.getId(), "");
            }
        }

        for (DanglingLine danglingLine : loadingContext.danglingLines) {
            LfDanglingLineBus lfBus2 = new LfDanglingLineBus(danglingLine);
            lfNetwork.addBus(lfBus2);
            LfBus lfBus1 = getLfBus(danglingLine.getTerminal(), lfNetwork, breakers);
            addBranch(lfNetwork, LfDanglingLineBranch.create(danglingLine, lfBus1, lfBus2), report);
        }

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            LfStarBus lfBus0 = new LfStarBus(t3wt);
            lfNetwork.addBus(lfBus0);
            LfBus lfBus1 = getLfBus(t3wt.getLeg1().getTerminal(), lfNetwork, breakers);
            LfBus lfBus2 = getLfBus(t3wt.getLeg2().getTerminal(), lfNetwork, breakers);
            LfBus lfBus3 = getLfBus(t3wt.getLeg3().getTerminal(), lfNetwork, breakers);
            addBranch(lfNetwork, LfLegBranch.create(lfBus1, lfBus0, t3wt, t3wt.getLeg1(), twtSplitShuntAdmittance), report);
            addBranch(lfNetwork, LfLegBranch.create(lfBus2, lfBus0, t3wt, t3wt.getLeg2(), twtSplitShuntAdmittance), report);
            addBranch(lfNetwork, LfLegBranch.create(lfBus3, lfBus0, t3wt, t3wt.getLeg3(), twtSplitShuntAdmittance), report);
        }

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            // Create phase controls which link controller -> controlled
            int legNumber = 1;
            for (ThreeWindingsTransformer.Leg leg : Arrays.asList(t3wt.getLeg1(), t3wt.getLeg2(), t3wt.getLeg3())) {
                PhaseTapChanger ptc = leg.getPhaseTapChanger();
                createPhaseControl(lfNetwork, ptc, t3wt.getId(), "_leg_" + legNumber);

                RatioTapChanger rtc = leg.getRatioTapChanger();
                createVoltageControl(lfNetwork, rtc, t3wt.getId(), "_leg_" + legNumber);
                legNumber++;
            }
        }

        List<Set<LfBus>> connectedSets = getNonImpedantConnectedSets(lfNetwork);
        connectedSets.stream()
            .filter(set -> set.size() > 2) // Two LfBuses with one voltage control is never a problem
            .forEach(set -> set.forEach(b -> removeConflictuousDiscreteVoltageControlled(b, set)));
    }

    private static List<Set<LfBus>> getNonImpedantConnectedSets(LfNetwork network) {
        List<LfBranch> nonImpedantBranches = network.getBranches().stream()
            .filter(LfNetworkLoaderImpl::isNonImpedantBranch)
            .filter(b -> b.getBus1() != null && b.getBus2() != null)
            .collect(Collectors.toList());

        Graph<LfBus, LfBranch> nonImpedantSubGraph = new Pseudograph<>(LfBranch.class);
        for (LfBranch branch : nonImpedantBranches) {
            nonImpedantSubGraph.addVertex(branch.getBus1());
            nonImpedantSubGraph.addVertex(branch.getBus2());
            nonImpedantSubGraph.addEdge(branch.getBus1(), branch.getBus2(), branch);
        }

        return new ConnectivityInspector<>(nonImpedantSubGraph).connectedSets();
    }

    private static boolean isNonImpedantBranch(LfBranch branch) {
        PiModel piModel = branch.getPiModel();
        return piModel.getZ() < DcEquationSystem.LOW_IMPEDANCE_THRESHOLD;
    }

    private static void removeConflictuousDiscreteVoltageControlled(LfBus lfBus, Set<LfBus> nonImpedantSet) {
        if (lfBus.isDiscreteVoltageControlled()) {
            // Find controllers which are in the given non-impedant connected set and which are controlling the same bus
            DiscreteVoltageControl dvc = lfBus.getDiscreteVoltageControl();
            List<LfBranch> sharedControllersInNonImpedentSet = dvc.getControllers().stream()
                .filter(c -> nonImpedantSet.contains(c.getBus1()) || nonImpedantSet.contains(c.getBus2()))
                .collect(Collectors.toList());

            // We keep only one controller in the non impedant connected set for each controlled lfBus
            sharedControllersInNonImpedentSet.stream().skip(1).forEach(dvc::removeController);
        }
    }

    private static void createPhaseControl(LfNetwork lfNetwork, PhaseTapChanger ptc, String controllerBranchId, String legId) {
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
            LfBus controlledBus = lfNetwork.getBusById(ptc.getRegulationTerminal().getBusView().getBus().getId());
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

    private static void createVoltageControl(LfNetwork lfNetwork, RatioTapChanger rtc, String controllerBranchId, String legId) {
        if (rtc != null && rtc.isRegulating()) {
            LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId + legId);
            if (controllerBranch.getBus1() == null || controllerBranch.getBus2() == null) {
                LOGGER.warn("Voltage controller branch {} is open: no voltage control created", controllerBranch.getId());
                return;
            }
            Terminal regulationTerminal = rtc.getRegulationTerminal();
            if (regulationTerminal.getBusView().getBus() == null) {
                LOGGER.warn("Regulating terminal of voltage controller branch {} is out of voltage: no voltage control created", controllerBranch.getId());
                return;
            }
            LfBus controlledBus = lfNetwork.getBusById(regulationTerminal.getBusView().getBus().getId());
            if ((controlledBus.getControllerBuses().isEmpty() && controlledBus.hasVoltageControl()) || !controlledBus.getControllerBuses().isEmpty()) {
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

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork, boolean breakers) {
        Bus bus = getBus(terminal, breakers);
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private static LfNetwork create(MutableInt num, List<Bus> buses, List<Switch> switches, LfNetworkParameters parameters) {
        LfNetwork lfNetwork = new LfNetwork(num.getValue(), parameters.getSlackBusSelector());
        num.increment();

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();

        createBuses(buses, parameters.isGeneratorVoltageRemoteControl(), parameters.isBreakers(), lfNetwork, loadingContext, report);
        createBranches(lfNetwork, loadingContext, report, parameters.isTwtSplitShuntAdmittance(), parameters.isBreakers());
        if (switches != null) {
            for (Switch sw : switches) {
                VoltageLevel vl = sw.getVoltageLevel();
                Bus bus1 = vl.getBusBreakerView().getBus1(sw.getId());
                Bus bus2 = vl.getBusBreakerView().getBus2(sw.getId());
                LfBus lfBus1 = lfNetwork.getBusById(bus1.getId());
                LfBus lfBus2 = lfNetwork.getBusById(bus2.getId());
                lfNetwork.addBranch(new LfSwitch(lfBus1, lfBus2, sw));
            }
        }

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
