/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class Networks {

    private static final String PROPERTY_V = "v";
    private static final String PROPERTY_ANGLE = "angle";

    private Networks() {
    }

    private static <T extends Injection<T>> void resetInjectionsState(Iterable<T> injections) {
        for (T injection : injections) {
            injection.getTerminal().setP(Double.NaN)
                    .setQ(Double.NaN);
        }
    }

    public static void resetState(Network network) {
        for (Bus b : network.getBusView().getBuses()) {
            b.setV(Double.NaN)
                    .setAngle(Double.NaN);
        }
        for (Branch<?> b : network.getBranches()) {
            b.getTerminal1().setP(Double.NaN)
                    .setQ(Double.NaN);
            b.getTerminal2().setP(Double.NaN)
                    .setQ(Double.NaN);
        }
        for (ThreeWindingsTransformer twt : network.getThreeWindingsTransformers()) {
            twt.getLeg1().getTerminal().setP(Double.NaN)
                    .setQ(Double.NaN);
            twt.getLeg2().getTerminal().setP(Double.NaN)
                    .setQ(Double.NaN);
            twt.getLeg3().getTerminal().setP(Double.NaN)
                    .setQ(Double.NaN);
        }
        for (ShuntCompensator sc : network.getShuntCompensators()) {
            sc.getTerminal().setP(Double.NaN)
                    .setQ(Double.NaN);
        }
        resetInjectionsState(network.getGenerators());
        resetInjectionsState(network.getStaticVarCompensators());
        resetInjectionsState(network.getVscConverterStations());
        resetInjectionsState(network.getLoads());
        resetInjectionsState(network.getLccConverterStations());
        resetInjectionsState(network.getBatteries());
        resetInjectionsState(network.getDanglingLines());
    }

    private static double getDoubleProperty(Identifiable<?> identifiable, String name) {
        Objects.requireNonNull(identifiable);
        String value = identifiable.getProperty(name);
        return value != null ? Double.parseDouble(value) : Double.NaN;
    }

    private static void setDoubleProperty(Identifiable<?> identifiable, String name, double value) {
        Objects.requireNonNull(identifiable);
        if (Double.isNaN(value)) {
            identifiable.removeProperty(name);
        } else {
            identifiable.setProperty(name, Double.toString(value));
        }
    }

    public static double getPropertyV(Identifiable<?> identifiable) {
        return getDoubleProperty(identifiable, PROPERTY_V);
    }

    public static void setPropertyV(Identifiable<?> identifiable, double v) {
        setDoubleProperty(identifiable, PROPERTY_V, v);
    }

    public static double getPropertyAngle(Identifiable<?> identifiable) {
        return getDoubleProperty(identifiable, PROPERTY_ANGLE);
    }

    public static void setPropertyAngle(Identifiable<?> identifiable, double angle) {
        setDoubleProperty(identifiable, PROPERTY_ANGLE, angle);
    }

    public static double zeroIfNan(double value) {
        return Double.isNaN(value) ? 0.0 : value;
    }

    public static List<LfNetwork> load(Network network, SlackBusSelector slackBusSelector) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), slackBusSelector);
    }

    public static List<LfNetwork> load(Network network, LfNetworkParameters parameters) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters);
    }

    public static List<LfNetwork> load(Network network, LfNetworkParameters parameters, ReportNode reportNode) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters, reportNode);
    }

    public static List<LfNetwork> load(Network network, LfTopoConfig topoConfig, LfNetworkParameters parameters, ReportNode reportNode) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), topoConfig, parameters, reportNode);
    }

    private static void retainAndCloseNecessarySwitches(Network network, LfTopoConfig topoConfig) {
        network.getSwitchStream()
                .filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                .forEach(sw -> sw.setRetained(false));
        topoConfig.getSwitchesToOpen().stream()
                .filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                .forEach(sw -> sw.setRetained(true));
        topoConfig.getSwitchesToClose().stream()
                .filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                .forEach(sw -> sw.setRetained(true));

        topoConfig.getSwitchesToClose().forEach(sw -> sw.setOpen(false)); // in order to be present in the network.
        topoConfig.getBranchIdsToClose().stream().map(network::getBranch).forEach(branch -> {
            branch.getTerminal1().connect();
            branch.getTerminal2().connect();
        }); // in order to be present in the network.
    }

    private static void restoreInitialTopology(LfNetwork network, Set<String> switchAndBranchIdsToClose) {
        var connectivity = network.getConnectivity();
        connectivity.startTemporaryChanges();
        Set<String> toRemove = new HashSet<>();
        switchAndBranchIdsToClose.forEach(id -> {
            LfBranch branch = network.getBranchById(id);
            if (branch != null) {
                connectivity.removeEdge(branch);
                toRemove.add(id);
            }
        });
        switchAndBranchIdsToClose.removeAll(toRemove);
        Set<LfBus> removedBuses = connectivity.getVerticesRemovedFromMainComponent();
        removedBuses.forEach(bus -> bus.setDisabled(true));
        Set<LfBranch> removedBranches = new HashSet<>(connectivity.getEdgesRemovedFromMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : removedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(removedBranches::add);
        }
        removedBranches.forEach(branch -> branch.setDisabled(true));
        for (LfHvdc hvdc : network.getHvdcs()) {
            if (isIsolatedBusForHvdc(hvdc.getBus1(), connectivity) || isIsolatedBusForHvdc(hvdc.getBus2(), connectivity)) {
                hvdc.setDisabled(true);
                hvdc.getConverterStation1().setTargetP(0.0);
                hvdc.getConverterStation2().setTargetP(0.0);
            }
        }
    }

    private static void addSwitchesOperatedByAutomationSystem(Network network, LfTopoConfig topoConfig,
                                                              OverloadManagementSystem system) {
        system.getTrippings().stream()
                .filter(t -> t.getType() == OverloadManagementSystem.Tripping.Type.SWITCH_TRIPPING)
                .forEach(tripping -> {
                    Switch aSwitch =
                            network.getSwitch(((OverloadManagementSystem.SwitchTripping) tripping).getSwitchToOperateId());
                    if (aSwitch != null) {
                        if (tripping.isOpenAction()) {
                            topoConfig.getSwitchesToOpen().add(aSwitch);
                        } else {
                            topoConfig.getSwitchesToClose().add(aSwitch);
                        }
                    }
                });
    }

    private static void addBranchesOperatedByAutomationSystem(Network network, LfTopoConfig topoConfig,
                                                              OverloadManagementSystem system) {
        system.getTrippings().stream()
                .filter(t -> t.getType() == OverloadManagementSystem.Tripping.Type.BRANCH_TRIPPING)
                .forEach(tripping -> {
                    Branch branch =
                            network.getBranch(((OverloadManagementSystem.BranchTripping) tripping).getBranchToOperateId());
                    if (branch != null && !tripping.isOpenAction()
                            && !branch.getTerminal1().isConnected() && !branch.getTerminal2().isConnected()) {
                        topoConfig.getBranchIdsToClose().add(branch.getId());
                    }
                });
    }

    private static void addSwitchesOperatedByAutomationSystem(Network network, LfTopoConfig topoConfig) {
        for (Substation substation : network.getSubstations()) {
            for (OverloadManagementSystem system : substation.getOverloadManagementSystems()) {
                addSwitchesOperatedByAutomationSystem(network, topoConfig, system);
                addBranchesOperatedByAutomationSystem(network, topoConfig, system);
            }
        }
    }

    public static LfNetworkList load(Network network, LfNetworkParameters networkParameters,
                                     LfTopoConfig topoConfig, ReportNode reportNode) {
        return load(network, networkParameters, topoConfig, LfNetworkList.DefaultVariantCleaner::new, reportNode);
    }

    public static LfNetworkList load(Network network, LfNetworkParameters networkParameters, LfTopoConfig topoConfig,
                                     LfNetworkList.VariantCleanerFactory variantCleanerFactory, ReportNode reportNode) {
        LfTopoConfig modifiedTopoConfig;
        if (networkParameters.isSimulateAutomationSystems()) {
            modifiedTopoConfig = new LfTopoConfig(topoConfig);
            addSwitchesOperatedByAutomationSystem(network, modifiedTopoConfig);
            if (modifiedTopoConfig.isBreaker()) {
                networkParameters.setBreakers(true);
            }
        } else {
            modifiedTopoConfig = topoConfig;
        }
        if (!modifiedTopoConfig.isBreaker() && modifiedTopoConfig.getBranchIdsToClose().isEmpty()) {
            return new LfNetworkList(load(network, topoConfig, networkParameters, reportNode));
        } else {
            if (!networkParameters.isBreakers() && modifiedTopoConfig.isBreaker()) {
                throw new PowsyblException("LF networks have to be built from bus/breaker view");
            }

            // create a temporary working variant to build LF networks
            String tmpVariantId = "olf-tmp-" + UUID.randomUUID();
            String workingVariantId = network.getVariantManager().getWorkingVariantId();
            network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
            network.getVariantManager().setWorkingVariant(tmpVariantId);

            // retain in topology all switches that could be open or close
            // and close switches that could be closed during the simulation
            retainAndCloseNecessarySwitches(network, modifiedTopoConfig);

            List<LfNetwork> lfNetworks = load(network, topoConfig, networkParameters, reportNode);

            if (!(modifiedTopoConfig.getSwitchesToClose().isEmpty() && modifiedTopoConfig.getBranchIdsToClose().isEmpty())) {
                Set<String> switchAndBranchIdsLeftToClose = modifiedTopoConfig.getSwitchesToClose().stream()
                        .filter(Objects::nonNull)
                        .map(Identifiable::getId)
                        .collect(Collectors.toSet());
                switchAndBranchIdsLeftToClose.addAll(modifiedTopoConfig.getBranchIdsToClose());
                for (LfNetwork lfNetwork : lfNetworks) {
                    // all switches and branches were closed
                    if (switchAndBranchIdsLeftToClose.isEmpty()) {
                        break;
                    }
                    // disable all buses and branches not connected to main component (because of switch to close)
                    restoreInitialTopology(lfNetwork, switchAndBranchIdsLeftToClose);
                }
            }

            return new LfNetworkList(lfNetworks, variantCleanerFactory.create(network, workingVariantId, tmpVariantId));
        }
    }

    public static Iterable<Bus> getBuses(Network network, boolean breaker) {
        return breaker ? network.getBusBreakerView().getBuses()
                       : network.getBusView().getBuses();
    }

    public static Bus getBus(Terminal terminal, boolean breakers) {
        return breakers ? terminal.getBusBreakerView().getBus()
                        : terminal.getBusView().getBus();
    }

    public static boolean isIsolatedBusForHvdc(LfBus bus, GraphConnectivity<LfBus, LfBranch> connectivity) {
        // used only for hvdc lines.
        // this criteria can be improved later depending on use case
        return connectivity.getConnectedComponent(bus).size() == 1 && bus.getLoadTargetP() == 0.0
                && bus.getGenerators().stream().noneMatch(LfGeneratorImpl.class::isInstance);
    }

    public static boolean isIsolatedBusForHvdc(LfBus bus, Set<LfBus> disabledBuses) {
        // used only for hvdc lines for DC sensitivity analysis where we don't have the connectivity.
        // this criteria can be improved later depending on use case
        return disabledBuses.contains(bus) && bus.getLoadTargetP() == 0.0
                && bus.getGenerators().stream().noneMatch(LfGeneratorImpl.class::isInstance);
    }

    public static Optional<Terminal> getEquipmentRegulatingTerminal(Network network, String equipmentId) {
        Generator generator = network.getGenerator(equipmentId);
        if (generator != null) {
            return Optional.of(generator.getRegulatingTerminal());
        }
        StaticVarCompensator staticVarCompensator = network.getStaticVarCompensator(equipmentId);
        if (staticVarCompensator != null) {
            return Optional.of(staticVarCompensator.getRegulatingTerminal());
        }
        TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer(equipmentId);
        if (t2wt != null) {
            RatioTapChanger rtc = t2wt.getRatioTapChanger();
            if (rtc != null) {
                return Optional.of(rtc.getRegulationTerminal());
            }
        }
        ThreeWindingsTransformer t3wt = network.getThreeWindingsTransformer(equipmentId);
        if (t3wt != null) {
            for (ThreeWindingsTransformer.Leg leg : t3wt.getLegs()) {
                RatioTapChanger rtc = leg.getRatioTapChanger();
                if (rtc != null && rtc.isRegulating()) {
                    return Optional.of(rtc.getRegulationTerminal());
                }
            }
        }
        ShuntCompensator shuntCompensator = network.getShuntCompensator(equipmentId);
        if (shuntCompensator != null) {
            return Optional.of(shuntCompensator.getRegulatingTerminal());
        }
        VscConverterStation vsc = network.getVscConverterStation(equipmentId);
        if (vsc != null) {
            return Optional.of(vsc.getTerminal()); // local regulation only
        }
        return Optional.empty();
    }
}
