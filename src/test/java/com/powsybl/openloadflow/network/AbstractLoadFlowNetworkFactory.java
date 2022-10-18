/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLoadFlowNetworkFactory {

    protected static Bus createBus(Network network, String id) {
        return createBus(network, id, 1d);
    }

    protected static Bus createBus(Network network, String id, double nominalV) {
        return createBus(network, id + "_s", id, nominalV);
    }

    protected static Bus createBus(Network network, String substationId, String id) {
        return createBus(network, substationId, id, 1);
    }

    protected static Bus createBus(Network network, String substationId, String id, double nominalV) {
        Substation s = network.getSubstation(substationId);
        if (s == null) {
            s = network.newSubstation()
                    .setId(substationId)
                    .setCountry(Country.FR)
                    .add();
        }
        VoltageLevel vl = s.newVoltageLevel()
                .setId(id + "_vl")
                .setNominalV(nominalV)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        return vl.getBusBreakerView().newBus()
                .setId(id)
                .add();
    }

    protected static Bus createOtherBus(Network network, String id, String voltageLevelId) {
        VoltageLevel vl = network.getVoltageLevel(voltageLevelId);
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

    protected static Generator createGenerator2(Bus b, String id, double p, double v) {
        Generator g = b.getVoltageLevel()
                .newGenerator()
                .setId(id)
                .setBus(b.getId())
                .setConnectableBus(b.getId())
                .setEnergySource(EnergySource.OTHER)
                .setMinP(0)
                .setMaxP(p)
                .add();
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

    protected static Switch createSwitch(Network network, Bus b1, Bus b2, String id) {
        return network.getVoltageLevel(b1.getVoltageLevel().getId()).getBusBreakerView().newSwitch()
                .setId(id)
                .setBus1(b1.getId())
                .setBus2(b2.getId())
                .setOpen(false)
                .add();
    }

    protected static TwoWindingsTransformer createTransformer(Network network, String substationId, Bus b1, Bus b2, String id, double x, double rho) {
        return network.getSubstation(substationId).newTwoWindingsTransformer()
                .setId(id)
                .setVoltageLevel1(b1.getVoltageLevel().getId())
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setVoltageLevel2(b2.getVoltageLevel().getId())
                .setBus2(b2.getId())
                .setConnectableBus2(b2.getId())
                .setRatedU1(1)
                .setRatedU2(rho)
                .setR(0)
                .setX(x)
                .setG(0)
                .setB(0)
                .add();
    }

    protected static ThreeWindingsTransformer createThreeWindingsTransformer(Network network, String substationId,
        Bus b1, Bus b2, Bus b3, String id, double x1, double rho1, double x2, double rho2, double x3, double rho3) {
        return network.getSubstation(substationId).newThreeWindingsTransformer()
            .setId(id)
            .setRatedU0(1)
            .newLeg1()
            .setVoltageLevel(b1.getVoltageLevel().getId())
            .setBus(b1.getId())
            .setConnectableBus(b1.getId())
            .setRatedU(rho1)
            .setR(0)
            .setX(x1)
            .setG(0)
            .setB(0)
            .add()
            .newLeg2()
            .setVoltageLevel(b2.getVoltageLevel().getId())
            .setBus(b2.getId())
            .setConnectableBus(b2.getId())
            .setRatedU(rho2)
            .setR(0)
            .setX(x2)
            .setG(0)
            .setB(0)
            .add()
            .newLeg3()
            .setVoltageLevel(b3.getVoltageLevel().getId())
            .setBus(b3.getId())
            .setConnectableBus(b3.getId())
            .setRatedU(rho3)
            .setR(0)
            .setX(x3)
            .setG(0)
            .setB(0)
            .add()
            .add();
    }

    protected static DanglingLine createDanglingLine(Bus b, String id, double x, double p0, double q0) {
        return b.getVoltageLevel().newDanglingLine()
            .setId(id)
            .setBus(b.getId())
            .setConnectableBus(b.getId())
            .setR(0)
            .setX(x)
            .setG(0)
            .setB(0)
            .setP0(p0)
            .setQ0(q0)
            .add();
    }

    protected static LccConverterStation createLcc(Bus b, String id) {
        return b.getVoltageLevel().newLccConverterStation()
            .setId(id)
            .setConnectableBus(b.getId())
            .setBus(b.getId())
            .setPowerFactor(0.8f)
            .setLossFactor(1.1f)
            .add();
    }

    protected static VscConverterStation createVsc(Bus b, String id, double voltageSetpoint, double reactivePowerSetpoint) {
        return b.getVoltageLevel().newVscConverterStation()
            .setId(id)
            .setConnectableBus(b.getId())
            .setBus(b.getId())
            .setVoltageRegulatorOn(true)
            .setVoltageSetpoint(voltageSetpoint)
            .setReactivePowerSetpoint(reactivePowerSetpoint)
            .setLossFactor(1.1f)
            .add();
    }

    protected static HvdcLine createHvdcLine(Network network, String id, HvdcConverterStation station1, HvdcConverterStation station2,
                                             double nominalV, double r, double activePowerSetpoint) {
        return network.newHvdcLine()
            .setId(id)
            .setConverterStationId1(station1.getId())
            .setConverterStationId2(station2.getId())
            .setNominalV(nominalV)
            .setR(r)
            .setActivePowerSetpoint(activePowerSetpoint)
            .setConvertersMode(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER)
            .setMaxP(activePowerSetpoint)
            .add();
    }
}

