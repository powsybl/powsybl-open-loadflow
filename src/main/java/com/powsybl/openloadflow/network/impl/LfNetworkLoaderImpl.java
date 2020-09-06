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
import com.powsybl.openloadflow.network.*;
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

    private static class LoadingContext {

        private final Set<Branch> branchSet = new LinkedHashSet<>();

        private final List<DanglingLine> danglingLines = new ArrayList<>();

        private final Set<ThreeWindingsTransformer> t3wtSet = new LinkedHashSet<>();
    }

    private static void createBuses(List<Bus> buses, boolean voltageRemoteControl, LfNetwork lfNetwork,
                                    LoadingContext loadingContext, LfNetworkLoadingReport report) {
        Map<LfBusImpl, String> controllerBusToControlledBusId = new LinkedHashMap<>();

        for (Bus bus : buses) {
            LfBusImpl lfBus = createBus(bus, voltageRemoteControl, loadingContext, report, controllerBusToControlledBusId);
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

    private static LfBusImpl createBus(Bus bus, boolean voltageRemoteControl, LoadingContext loadingContext, LfNetworkLoadingReport report,
                                       Map<LfBusImpl, String> controllerBusToControlledBusId) {
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
                Bus controlledBus = regulatingTerminal.getBusView().getBus();
                Bus connectedBus = injection.getTerminal().getBusView().getBus();
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
                                       boolean twtSplitShuntAdmittance) {
        for (Branch branch : loadingContext.branchSet) {
            LfBus lfBus1 = getLfBus(branch.getTerminal1(), lfNetwork);
            LfBus lfBus2 = getLfBus(branch.getTerminal2(), lfNetwork);
            addBranch(lfNetwork, LfBranchImpl.create(branch, lfBus1, lfBus2, twtSplitShuntAdmittance), report);
        }

        for (DanglingLine danglingLine : loadingContext.danglingLines) {
            LfDanglingLineBus lfBus2 = new LfDanglingLineBus(danglingLine);
            lfNetwork.addBus(lfBus2);
            LfBus lfBus1 = getLfBus(danglingLine.getTerminal(), lfNetwork);
            addBranch(lfNetwork, LfDanglingLineBranch.create(danglingLine, lfBus1, lfBus2), report);
        }

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            LfStarBus lfBus0 = new LfStarBus(t3wt);
            lfNetwork.addBus(lfBus0);
            LfBus lfBus1 = getLfBus(t3wt.getLeg1().getTerminal(), lfNetwork);
            LfBus lfBus2 = getLfBus(t3wt.getLeg2().getTerminal(), lfNetwork);
            LfBus lfBus3 = getLfBus(t3wt.getLeg3().getTerminal(), lfNetwork);
            addBranch(lfNetwork, LfLegBranch.create(lfBus1, lfBus0, t3wt, t3wt.getLeg1(), twtSplitShuntAdmittance), report);
            addBranch(lfNetwork, LfLegBranch.create(lfBus2, lfBus0, t3wt, t3wt.getLeg2(), twtSplitShuntAdmittance), report);
            addBranch(lfNetwork, LfLegBranch.create(lfBus3, lfBus0, t3wt, t3wt.getLeg3(), twtSplitShuntAdmittance), report);
        }
    }

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork) {
        Bus bus = terminal.getBusView().getBus();
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private static LfNetwork create(MutableInt num, List<Bus> buses, LfNetworkLoadingParameters parameters) {
        LfNetwork lfNetwork = new LfNetwork(num.getValue(), parameters.getSlackBusSelector());
        num.increment();

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();

        createBuses(buses, parameters.isGeneratorVoltageRemoteControl(), lfNetwork, loadingContext, report);
        createBranches(lfNetwork, loadingContext, report, parameters.isTwtSplitShuntAdmittance());

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
    public Optional<List<LfNetwork>> load(Object network, LfNetworkLoadingParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);

        if (network instanceof Network) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<Pair<Integer, Integer>, List<Bus>> buseByCc = new TreeMap<>();
            for (Bus bus : ((Network) network).getBusView().getBuses()) {
                Component cc = bus.getConnectedComponent();
                Component sc = bus.getSynchronousComponent();
                if (cc != null && sc != null) {
                    buseByCc.computeIfAbsent(Pair.of(cc.getNum(), sc.getNum()), k -> new ArrayList<>()).add(bus);
                }
            }

            MutableInt num = new MutableInt(0);
            List<LfNetwork> lfNetworks = buseByCc.entrySet().stream()
                    .filter(e -> e.getKey().getLeft() == ComponentConstants.MAIN_NUM)
                    .map(e -> create(num, e.getValue(), parameters))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            stopwatch.stop();
            LOGGER.debug(PERFORMANCE_MARKER, "LF networks created in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return Optional.of(lfNetworks);
        }

        return Optional.empty();
    }
}
