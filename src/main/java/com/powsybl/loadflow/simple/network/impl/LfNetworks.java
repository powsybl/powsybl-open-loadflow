/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network.impl;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.simple.network.LfBranch;
import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.LfNetwork;
import com.powsybl.loadflow.simple.network.SlackBusSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.loadflow.simple.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LfNetworks {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfNetworks.class);

    private static class CreationContext {

        private final Set<Branch> branchSet = new LinkedHashSet<>();

        private final List<DanglingLine> danglingLines = new ArrayList<>();

        private final Set<ThreeWindingsTransformer> t3wtSet = new LinkedHashSet<>();

        private final Map<String, Integer> busIdToNum = new HashMap<>();
    }

    private LfNetworks() {
    }

    private static List<LfBus> createBuses(List<Bus> buses, CreationContext creationContext) {
        List<LfBus> lfBuses = new ArrayList<>(buses.size());
        int[] generatorCount = new int[1];

        for (Bus bus : buses) {
            LfBusImpl lfBus = addLfBus(bus, lfBuses, creationContext.busIdToNum);

            bus.visitConnectedEquipments(new DefaultTopologyVisitor() {

                private void visitBranch(Branch branch) {
                    creationContext.branchSet.add(branch);
                    // add to neighbors if connected at both sides
                    Bus bus1 = branch.getTerminal1().getBusView().getBus();
                    Bus bus2 = branch.getTerminal2().getBusView().getBus();
                    if (bus1 != null && bus2 != null) {
                        lfBus.addNeighbor();
                    }
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
                    creationContext.t3wtSet.add(transformer);
                }

                @Override
                public void visitGenerator(Generator generator) {
                    lfBus.addGenerator(generator);
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
                    creationContext.danglingLines.add(danglingLine);
                }

                @Override
                public void visitStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
                    lfBus.addStaticVarCompensator(staticVarCompensator);
                }

                @Override
                public void visitBattery(Battery battery) {
                    lfBus.addBattery(battery);
                }

                @Override
                public void visitHvdcConverterStation(HvdcConverterStation<?> converterStation) {
                    switch (converterStation.getHvdcType()) {
                        case VSC:
                            lfBus.addVscConverterStation((VscConverterStation) converterStation);
                            break;
                        case LCC:
                            throw new UnsupportedOperationException("TODO: LCC");
                        default:
                            throw new IllegalStateException("Unknown HVDC converter station type: " + converterStation.getHvdcType());
                    }
                }
            });
        }

        if (generatorCount[0] == 0) {
            throw new PowsyblException("Connected component without any regulating generator");
        }

        return lfBuses;
    }

    private static List<LfBranch> createBranches(List<LfBus> lfBuses, CreationContext creationContext) {
        List<LfBranch> lfBranches = new ArrayList<>();

        for (Branch branch : creationContext.branchSet) {
            LfBus lfBus1 = getLfBus(branch.getTerminal1(), lfBuses, creationContext.busIdToNum);
            LfBus lfBus2 = getLfBus(branch.getTerminal2(), lfBuses, creationContext.busIdToNum);
            lfBranches.add(LfBranchImpl.create(branch, lfBus1, lfBus2));
        }

        for (DanglingLine danglingLine : creationContext.danglingLines) {
            LfDanglingLineBus lfBus2 = addLfBus(danglingLine, lfBuses, creationContext.busIdToNum);
            LfBus lfBus1 = getLfBus(danglingLine.getTerminal(), lfBuses, creationContext.busIdToNum);
            lfBranches.add(LfDanglingLineBranch.create(danglingLine, lfBus1, lfBus2));
        }

        for (ThreeWindingsTransformer t3wt : creationContext.t3wtSet) {
            LfStarBus lfBus0 = addLfBus(t3wt, lfBuses, creationContext.busIdToNum);
            LfBus lfBus1 = getLfBus(t3wt.getLeg1().getTerminal(), lfBuses, creationContext.busIdToNum);
            LfBus lfBus2 = getLfBus(t3wt.getLeg2().getTerminal(), lfBuses, creationContext.busIdToNum);
            LfBus lfBus3 = getLfBus(t3wt.getLeg3().getTerminal(), lfBuses, creationContext.busIdToNum);
            lfBranches.add(LfLeg1Branch.create(lfBus1, lfBus0, t3wt.getLeg1()));
            lfBranches.add(LfLeg2or3Branch.create(lfBus2, lfBus0, t3wt, t3wt.getLeg2()));
            lfBranches.add(LfLeg2or3Branch.create(lfBus3, lfBus0, t3wt, t3wt.getLeg3()));
        }

        return lfBranches;
    }

    private static LfBus getLfBus(Terminal terminal, List<LfBus> lfBuses, Map<String, Integer> busIdToNum) {
        Bus bus = terminal.getBusView().getBus();
        if (bus != null) {
            int num = busIdToNum.get(bus.getId());
            return lfBuses.get(num);
        }
        return null;
    }

    private static LfBusImpl addLfBus(Bus bus, List<LfBus> lfBuses, Map<String, Integer> busIdToNum) {
        int busNum = lfBuses.size();
        LfBusImpl lfBus = LfBusImpl.create(bus, busNum);
        busIdToNum.put(bus.getId(), busNum);
        lfBuses.add(lfBus);
        return lfBus;
    }

    private static LfDanglingLineBus addLfBus(DanglingLine danglingLine, List<LfBus> lfBuses, Map<String, Integer> busIdToNum) {
        int busNum = lfBuses.size();
        LfDanglingLineBus lfBus = new LfDanglingLineBus(danglingLine, busNum);
        busIdToNum.put(lfBus.getId(), busNum);
        lfBuses.add(lfBus);
        return lfBus;
    }

    private static LfStarBus addLfBus(ThreeWindingsTransformer t3wt, List<LfBus> lfBuses, Map<String, Integer> busIdToNum) {
        int busNum = lfBuses.size();
        LfStarBus lfBus = new LfStarBus(t3wt, busNum);
        busIdToNum.put(lfBus.getId(), busNum);
        lfBuses.add(lfBus);
        return lfBus;
    }

    private static LfNetwork create(List<Bus> buses, SlackBusSelector slackBusSelector) {
        CreationContext creationContext = new CreationContext();
        List<LfBus> lfBuses = createBuses(buses, creationContext);
        List<LfBranch> lfBranches = createBranches(lfBuses, creationContext);
        return new LfNetwork(lfBuses, lfBranches, slackBusSelector);
    }

    public static List<LfNetwork> create(Network network, SlackBusSelector slackBusSelector) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(slackBusSelector);

        Stopwatch stopwatch = Stopwatch.createStarted();

        Map<Integer, List<Bus>> buseByCc = new TreeMap<>();
        for (Bus bus : network.getBusView().getBuses()) {
            Component cc = bus.getConnectedComponent();
            if (cc != null) {
                buseByCc.computeIfAbsent(cc.getNum(), k -> new ArrayList<>()).add(bus);
            }
        }

        List<LfNetwork> lfNetworks = buseByCc.entrySet().stream()
                .filter(e -> e.getKey() == ComponentConstants.MAIN_NUM)
                .map(e -> create(e.getValue(), slackBusSelector))
                .collect(Collectors.toList());

        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "LF networks created in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return lfNetworks;
    }

    public static void resetState(Network network) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        for (Bus b : network.getBusView().getBuses()) {
            b.setV(Double.NaN);
            b.setAngle(Double.NaN);
        }
        for (ShuntCompensator sc : network.getShuntCompensators()) {
            sc.getTerminal().setQ(Double.NaN);
        }
        for (Branch b : network.getBranches()) {
            b.getTerminal1().setP(Double.NaN);
            b.getTerminal1().setQ(Double.NaN);
            b.getTerminal2().setP(Double.NaN);
            b.getTerminal2().setQ(Double.NaN);
        }

        stopwatch.stop();
        LOGGER.debug(PERFORMANCE_MARKER, "IIDM network reset done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

}
