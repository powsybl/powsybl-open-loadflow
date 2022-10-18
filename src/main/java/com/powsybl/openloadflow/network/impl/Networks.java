/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.SlackBusSelector;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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

    public static List<LfNetwork> load(Network network, SlackBusSelector slackBusSelector, Reporter reporter) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), slackBusSelector, reporter);
    }

    public static List<LfNetwork> load(Network network, LfNetworkParameters parameters, Reporter reporter) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters, reporter);
    }

    public static List<LfNetwork> load(Network network, LfNetworkParameters networkParameters,
                                       Set<Switch> switchesToOpen, Set<Switch> switchesToClose, Reporter reporter) {
        if (switchesToOpen.isEmpty() && switchesToClose.isEmpty()) {
            return load(network, networkParameters, reporter);
        } else {
            String tmpVariantId = "olf-tmp-" + UUID.randomUUID();
            String variantId = network.getVariantManager().getWorkingVariantId();
            network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
            network.getVariantManager().setWorkingVariant(tmpVariantId);
            try {
                network.getSwitchStream().filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                        .forEach(sw -> sw.setRetained(false));
                switchesToOpen.stream().filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                        .forEach(sw -> sw.setRetained(true));
                switchesToClose.stream().filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                        .forEach(sw -> sw.setRetained(true));
                switchesToClose.forEach(sw -> sw.setOpen(false)); // in order to be present in the network.
                return load(network, networkParameters, reporter);
            } finally {
                network.getVariantManager().removeVariant(tmpVariantId);
                network.getVariantManager().setWorkingVariant(variantId);
            }
        }
    }
}
