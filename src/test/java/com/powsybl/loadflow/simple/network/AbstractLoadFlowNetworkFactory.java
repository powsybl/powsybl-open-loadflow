/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import com.powsybl.iidm.network.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLoadFlowNetworkFactory {

    protected static Bus createBus(Network network, String id) {
        return createBus(network, id, 1d);
    }

    protected static Bus createBus(Network network, String id, double nominalV) {
        Substation s = network.newSubstation()
                .setId(id + "_s")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl = s.newVoltageLevel()
                .setId(id + "_vl")
                .setNominalV(nominalV)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        return vl.getBusBreakerView().newBus()
                .setId(id)
                .add();
    }

    protected static Generator createGenerator(Bus b, String id, double p) {
        return createGenerator(b, id, p, 1);
    }

    protected static Generator createGenerator(Bus b, String id, double p, double v) {
        Generator g = b.getVoltageLevel()
                .newGenerator()
                .setId(id)
                .setBus(b.getId())
                .setConnectableBus(b.getId())
                .setEnergySource(EnergySource.OTHER)
                .setMinP(0)
                .setMaxP(p)
                .setTargetP(p)
                .setTargetV(v)
                .setVoltageRegulatorOn(true)
                .add();
        g.getTerminal().setP(-p).setQ(0);
        return g;
    }

    protected static Load createLoad(Bus b, String id, double p) {
        return createLoad(b, id, p, 0);
    }

    protected static Load createLoad(Bus b, String id, double p, double q) {
        Load l = b.getVoltageLevel().newLoad()
                .setId(id)
                .setBus(b.getId())
                .setConnectableBus(b.getId())
                .setP0(p)
                .setQ0(q)
                .add();
        l.getTerminal().setP(p).setQ(q);
        return l;
    }

    protected static Line createLine(Network network, Bus b1, Bus b2, String id, double x) {
        return network.newLine()
                .setId(id)
                .setVoltageLevel1(b1.getVoltageLevel().getId())
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setVoltageLevel2(b2.getVoltageLevel().getId())
                .setBus2(b2.getId())
                .setConnectableBus2(b2.getId())
                .setR(0)
                .setX(x)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
    }
}

