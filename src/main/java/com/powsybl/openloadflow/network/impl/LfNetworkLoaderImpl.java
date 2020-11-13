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

    private static void createBuses(List<Bus> buses, LfNetwork lfNetwork, List<AbstractLfBus> lfBuses, boolean voltageRemoteControl, boolean breakers,
                                    LoadingContext loadingContext, LfNetworkLoadingReport report) {
        for (Bus bus : buses) {
            LfBusImpl lfBus = createBus(bus, voltageRemoteControl, breakers, loadingContext, report);
            lfNetwork.addBus(lfBus);
            lfBuses.add(lfBus);
        }
    }

    private static void createVoltageControls(LfNetwork lfNetwork, List<AbstractLfBus> lfBuses, boolean voltageRemoteControl, boolean breakers) {
        // set controller -> controlled link
        for (AbstractLfBus controllerBus : lfBuses) {

            LfBus controlledBus = null;
            double controllerTargetV = Double.NaN;

            for (LfGenerator lfGenerator : controllerBus.getGenerators()) {
                LfBus generatorControlledBus = lfNetwork.getBusById(lfGenerator.getControlledBusId(breakers));

                // check that remote control bus is the same for all generators of current controller bus
                checkUniqueControlledBus(controlledBus, generatorControlledBus, controllerBus);

                // check target voltage
                checkGeneratorTargetV(lfGenerator, controllerTargetV,
                    controllerBus, generatorControlledBus, voltageRemoteControl);
                controllerTargetV = lfGenerator.getTargetV(); // in per-unit system

                if (lfGenerator.hasVoltageControl()) {
                    controlledBus = voltageRemoteControl ? generatorControlledBus : controllerBus;
                }
            }

            controllerBus.setTargetV(controllerTargetV);
            if (controlledBus != null) {
                controllerBus.setControlledBus((AbstractLfBus) controlledBus);
            }
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

    private static void checkGeneratorTargetV(LfGenerator lfGenerator, double previousTargetV, LfBus controllerBus,
                                              LfBus controlledBus, boolean voltageRemoteControl) {
        double targetV = lfGenerator.getTargetV();
        if (!Double.isNaN(previousTargetV) && FastMath.abs(previousTargetV - targetV) > TARGET_V_EPSILON) {
            String generatorIds = controllerBus.getGenerators().stream().map(LfGenerator::getId).collect(Collectors.joining(", "));
            throw new PowsyblException("Generators [" + generatorIds + "] are connected to the same bus '" + controllerBus.getId()
                + "' with a different target voltages: " + targetV * controlledBus.getNominalV() + " and " + previousTargetV * controlledBus.getNominalV());
        }
        if (voltageRemoteControl && controlledBus != controllerBus) {
            double remoteNominalV = controlledBus.getNominalV();
            double localNominalV = controllerBus.getNominalV();
            double scaleV = localNominalV / remoteNominalV;
            double updatedTargetV = lfGenerator.getTargetV() * scaleV;
            LOGGER.warn("Remote voltage control is not activated. The voltage target of " +
                    "{} ({}) with remote control is rescaled from {} to {}",
                controllerBus.getId(), lfGenerator.getClass().getSimpleName(), lfGenerator.getTargetV(), updatedTargetV);
        }
    }

    private static Bus getBus(Terminal terminal, boolean breakers) {
        return breakers ? terminal.getBusBreakerView().getBus() : terminal.getBusView().getBus();
    }

    private static LfBusImpl createBus(Bus bus, boolean voltageRemoteControl, boolean breakers, LoadingContext loadingContext,
                                       LfNetworkLoadingReport report) {
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

            @Override
            public void visitGenerator(Generator generator) {
                lfBus.addGenerator(generator, report);
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

    private static void createBranches(List<AbstractLfBus> lfBuses, LfNetwork lfNetwork, boolean twtSplitShuntAdmittance, boolean breakers, LoadingContext loadingContext, LfNetworkLoadingReport report) {
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
            }
        }

        for (DanglingLine danglingLine : loadingContext.danglingLines) {
            LfDanglingLineBus lfBus2 = new LfDanglingLineBus(danglingLine);
            lfNetwork.addBus(lfBus2);
            lfBuses.add(lfBus2);
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
                legNumber++;
            }
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
            LfBranch controllerBranch = lfNetwork.getBranchById(controllerBranchId + legId);
            LfBus controlledBus = lfNetwork.getBusById(ptc.getRegulationTerminal().getBusView().getBus().getId());
            DiscretePhaseControl.ControlledSide controlledSide = controlledBus == controlledBranch.getBus1() ?
                DiscretePhaseControl.ControlledSide.ONE : DiscretePhaseControl.ControlledSide.TWO;
            if (controlledBranch instanceof LfLegBranch && controlledBus == controlledBranch.getBus2()) {
                throw new IllegalStateException("Leg " + controlledBranch.getId() + " has a non supported control at star bus side");
            }
            double targetValue = ptc.getRegulationValue() / PerUnit.SB;
            double targetDeadband = ptc.getTargetDeadband() /  PerUnit.SB;

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

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork, boolean breakers) {
        Bus bus = getBus(terminal, breakers);
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private static LfNetwork create(MutableInt num, List<Bus> buses, List<Switch> switches, LfNetworkParameters parameters) {
        LfNetwork lfNetwork = new LfNetwork(num.getValue(), parameters.getSlackBusSelector());
        num.increment();

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();

        List<AbstractLfBus> lfBuses = new ArrayList<>();
        createBuses(buses, lfNetwork, lfBuses, parameters.isGeneratorVoltageRemoteControl(), parameters.isBreakers(), loadingContext, report);
        createBranches(lfBuses, lfNetwork, parameters.isTwtSplitShuntAdmittance(), parameters.isBreakers(), loadingContext, report);
        createVoltageControls(lfNetwork, lfBuses, parameters.isGeneratorVoltageRemoteControl(), parameters.isBreakers());
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
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPLesserOrEqualsToZero > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of a targetP <= 0",
                    lfNetwork.getNum(), report.generatorsDiscardedFromActivePowerControlBecauseTargetPLesserOrEqualsToZero);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of a targetP > maxP",
                    lfNetwork.getNum(), report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible > 0) {
            LOGGER.warn("Network {}: {} generators have been discarded from active power control because of maxP not plausible",
                    lfNetwork.getNum(), report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible);
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
