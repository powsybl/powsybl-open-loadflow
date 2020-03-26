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
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkLoader;
import com.powsybl.openloadflow.network.SlackBusSelector;
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
        int[] generatorCount = new int[1];
        Map<LfBusImpl, String> controllerBusToControlledBusId = new LinkedHashMap<>();

        for (Bus bus : buses) {
            LfBusImpl lfBus = LfBusImpl.create(bus);
            lfNetwork.addBus(lfBus);

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
                    String controlledBusId = regulatingTerminal.getBusView().getBus().getId();
                    String connectedBusId = injection.getTerminal().getBusView().getBus().getId();
                    if (!Objects.equals(controlledBusId, connectedBusId)) {
                        if (voltageRemoteControl) {
                            // controller to controlled bus link will be set later because controlled bus might not have
                            // been yet created
                            controllerBusToControlledBusId.put(lfBus, controlledBusId);
                        } else {
                            double remoteNominalV = regulatingTerminal.getVoltageLevel().getNominalV();
                            double localNominalV = injection.getTerminal().getVoltageLevel().getNominalV();
                            scaleV = localNominalV / remoteNominalV;
                            LOGGER.warn("Remote voltage control is not yet supported. The voltage target of " +
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
                    generatorCount[0]++;
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
                }

                @Override
                public void visitStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
                    double scaleV = checkVoltageRemoteControl(staticVarCompensator, staticVarCompensator.getRegulatingTerminal(),
                            staticVarCompensator.getVoltageSetPoint());
                    lfBus.addStaticVarCompensator(staticVarCompensator, scaleV, report);
                }

                @Override
                public void visitBattery(Battery battery) {
                    lfBus.addBattery(battery);
                }

                @Override
                public void visitHvdcConverterStation(HvdcConverterStation<?> converterStation) {
                    switch (converterStation.getHvdcType()) {
                        case VSC:
                            lfBus.addVscConverterStation((VscConverterStation) converterStation, report);
                            break;
                        case LCC:
                            throw new UnsupportedOperationException("LCC converter station is not yet supported");
                        default:
                            throw new IllegalStateException("Unknown HVDC converter station type: " + converterStation.getHvdcType());
                    }
                }
            });
        }

        if (generatorCount[0] == 0) {
            throw new PowsyblException("Connected component without any regulating generator");
        }

        // set controller -> controlled link
        for (Map.Entry<LfBusImpl, String> e : controllerBusToControlledBusId.entrySet()) {
            LfBusImpl controllerBus = e.getKey();
            String controlledBusId = e.getValue();
            LfBus controlledBus = lfNetwork.getBusById(controlledBusId);
            controllerBus.setControlledBus((LfBusImpl) controlledBus);
        }
    }

    private static void createBranches(LfNetwork lfNetwork, LoadingContext loadingContext) {
        for (Branch branch : loadingContext.branchSet) {
            LfBus lfBus1 = getLfBus(branch.getTerminal1(), lfNetwork);
            LfBus lfBus2 = getLfBus(branch.getTerminal2(), lfNetwork);
            lfNetwork.addBranch(LfBranchImpl.create(branch, lfBus1, lfBus2));
        }

        for (DanglingLine danglingLine : loadingContext.danglingLines) {
            LfDanglingLineBus lfBus2 = new LfDanglingLineBus(danglingLine);
            lfNetwork.addBus(lfBus2);
            LfBus lfBus1 = getLfBus(danglingLine.getTerminal(), lfNetwork);
            lfNetwork.addBranch(LfDanglingLineBranch.create(danglingLine, lfBus1, lfBus2));
        }

        for (ThreeWindingsTransformer t3wt : loadingContext.t3wtSet) {
            LfStarBus lfBus0 = new LfStarBus(t3wt);
            lfNetwork.addBus(lfBus0);
            LfBus lfBus1 = getLfBus(t3wt.getLeg1().getTerminal(), lfNetwork);
            LfBus lfBus2 = getLfBus(t3wt.getLeg2().getTerminal(), lfNetwork);
            LfBus lfBus3 = getLfBus(t3wt.getLeg3().getTerminal(), lfNetwork);
            lfNetwork.addBranch(LfLegBranch.create(lfBus1, lfBus0, t3wt, t3wt.getLeg1()));
            lfNetwork.addBranch(LfLegBranch.create(lfBus2, lfBus0, t3wt, t3wt.getLeg2()));
            lfNetwork.addBranch(LfLegBranch.create(lfBus3, lfBus0, t3wt, t3wt.getLeg3()));
        }
    }

    private static LfBus getLfBus(Terminal terminal, LfNetwork lfNetwork) {
        Bus bus = terminal.getBusView().getBus();
        return bus != null ? lfNetwork.getBusById(bus.getId()) : null;
    }

    private static LfNetwork create(List<Bus> buses, SlackBusSelector slackBusSelector, boolean voltageRemoteControl) {
        LfNetwork lfNetwork = new LfNetwork(slackBusSelector);

        LoadingContext loadingContext = new LoadingContext();
        LfNetworkLoadingReport report = new LfNetworkLoadingReport();

        createBuses(buses, voltageRemoteControl, lfNetwork, loadingContext, report);
        createBranches(lfNetwork, loadingContext);

        if (report.generatorsDiscardedFromVoltageControlBecauseNotStarted > 0) {
            LOGGER.warn("{} generators have been discarded from voltage control because not started",
                    report.generatorsDiscardedFromVoltageControlBecauseNotStarted);
        }
        if (report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall > 0) {
            LOGGER.warn("{} generators have been discarded from voltage control because of a too small max reactive range",
                    report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPLesserOrEqualsToZero > 0) {
            LOGGER.warn("{} generators have been discarded from active power control because of a targetP <= 0",
                    report.generatorsDiscardedFromActivePowerControlBecauseTargetPLesserOrEqualsToZero);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP > 0) {
            LOGGER.warn("{} generators have been discarded from active power control because of a targetP > maxP",
                    report.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThenMaxP);
        }
        if (report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible > 0) {
            LOGGER.warn("{} generators have been discarded from active power control because of maxP not plausible",
                    report.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible);
        }

        return lfNetwork;
    }

    @Override
    public Optional<List<LfNetwork>> load(Object network, SlackBusSelector slackBusSelector, boolean voltageRemoteControl) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(slackBusSelector);

        if (network instanceof Network) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<Integer, List<Bus>> buseByCc = new TreeMap<>();
            for (Bus bus : ((Network) network).getBusView().getBuses()) {
                Component cc = bus.getConnectedComponent();
                if (cc != null) {
                    buseByCc.computeIfAbsent(cc.getNum(), k -> new ArrayList<>()).add(bus);
                }
            }

            List<LfNetwork> lfNetworks = buseByCc.entrySet().stream()
                    .filter(e -> e.getKey() == ComponentConstants.MAIN_NUM)
                    .map(e -> create(e.getValue(), slackBusSelector, voltageRemoteControl))
                    .collect(Collectors.toList());

            stopwatch.stop();
            LOGGER.debug(PERFORMANCE_MARKER, "LF networks created in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            return Optional.of(lfNetworks);
        }

        return Optional.empty();
    }
}
