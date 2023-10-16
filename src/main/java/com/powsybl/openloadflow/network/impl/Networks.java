/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.extensions.OverloadManagementSystem;
import com.powsybl.openloadflow.network.impl.extensions.SubstationAutomationSystems;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
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

    public static List<LfNetwork> load(Network network, SlackBusSelector slackBusSelector) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), slackBusSelector);
    }

    public static List<LfNetwork> load(Network network, LfNetworkParameters parameters) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters);
    }

    public static List<LfNetwork> load(Network network, LfNetworkParameters parameters, Reporter reporter) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters, reporter);
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
    }

    private static void restoreInitialTopology(LfNetwork network, Set<Switch> allSwitchesToClose) {
        var connectivity = network.getConnectivity();
        connectivity.startTemporaryChanges();
        allSwitchesToClose.stream().map(Identifiable::getId).forEach(id -> {
            LfBranch branch = network.getBranchById(id);
            if (branch != null) {
                connectivity.removeEdge(branch);
            }
        });
        Set<LfBus> removedBuses = connectivity.getVerticesRemovedFromMainComponent();
        removedBuses.forEach(bus -> bus.setDisabled(true));
        Set<LfBranch> removedBranches = new HashSet<>(connectivity.getEdgesRemovedFromMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : removedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(removedBranches::add);
        }
        removedBranches.forEach(branch -> branch.setDisabled(true));
    }

    private static void addSwitchesOperatedByAutomata(Network network, LfTopoConfig topoConfig) {
        for (Substation substation : network.getSubstations()) {
            SubstationAutomationSystems systems = substation.getExtension(SubstationAutomationSystems.class);
            if (systems != null) {
                for (OverloadManagementSystem system : systems.getOverloadManagementSystems()) {
                    Switch aSwitch = network.getSwitch(system.getSwitchIdToOperate());
                    if (aSwitch != null) {
                        if (system.isSwitchOpen()) {
                            topoConfig.getSwitchesToOpen().add(aSwitch);
                        } else {
                            topoConfig.getSwitchesToClose().add(aSwitch);
                        }
                    }
                }
            }
        }
    }

    public static LfNetworkList load(Network network, LfNetworkParameters networkParameters,
                                     LfTopoConfig topoConfig, Reporter reporter) {
        return load(network, networkParameters, topoConfig, LfNetworkList.DefaultVariantCleaner::new, reporter);
    }

    public static LfNetworkList load(Network network, LfNetworkParameters networkParameters, LfTopoConfig topoConfig,
                                     LfNetworkList.VariantCleanerFactory variantCleanerFactory, Reporter reporter) {
        LfTopoConfig modifiedTopoConfig;
        if (networkParameters.isSimulateAutomationSystems()) {
            modifiedTopoConfig = new LfTopoConfig(topoConfig);
            addSwitchesOperatedByAutomata(network, modifiedTopoConfig);
            if (modifiedTopoConfig.isBreaker()) {
                networkParameters.setBreakers(true);
            }
        } else {
            modifiedTopoConfig = topoConfig;
        }
        if (!modifiedTopoConfig.isBreaker()) {
            return new LfNetworkList(load(network, networkParameters, reporter));
        } else {
            if (!networkParameters.isBreakers()) {
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

            List<LfNetwork> lfNetworks = load(network, networkParameters, reporter);

            if (!modifiedTopoConfig.getSwitchesToClose().isEmpty()) {
                for (LfNetwork lfNetwork : lfNetworks) {
                    // disable all buses and branches not connected to main component (because of switch to close)
                    restoreInitialTopology(lfNetwork, modifiedTopoConfig.getSwitchesToClose());
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
