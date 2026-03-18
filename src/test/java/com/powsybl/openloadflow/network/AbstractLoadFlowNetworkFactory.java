/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.StaticVarCompensator.RegulationMode;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
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

    protected static DcNode createDcNode(Network network, String id, double nominalV) {
        return createDcNode(network, id, nominalV, false);
    }

    protected static DcNode createDcNode(Network network, String id, double nominalV, boolean grounded) {
        DcNode dn = network.newDcNode().
                setId(id).
                setNominalV(nominalV).
                add();

        if (grounded) {
            network.newDcGround()
                    .setId(id + "_ground")
                    .setDcNode(id)
                    .add();
        }
        return dn;
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
                .setMaxP(2 * p)
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
                .setMaxP(2 * p)
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
        return createLine(network, b1, b2, id, 0, x);
    }

    protected static Line createLine(Network network, Bus b1, Bus b2, String id, double r, double x) {
        return network.newLine()
                .setId(id)
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setBus2(b2.getId())
                .setConnectableBus2(b2.getId())
                .setR(r)
                .setX(x)
                .add();
    }

    protected static DcLine createDcLine(Network network, DcNode dn1, DcNode dn2, String id, double r) {
        return network.newDcLine()
                .setId(id)
                .setDcNode1(dn1.getId())
                .setDcNode2(dn2.getId())
                .setR(r)
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
        return createTransformer(network, substationId, b1, b2, id, 0, x, rho);
    }

    protected static TwoWindingsTransformer createTransformer(Network network, String substationId, Bus b1, Bus b2, String id, double r, double x, double rho) {
        return network.getSubstation(substationId).newTwoWindingsTransformer()
                .setId(id)
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setBus2(b2.getId())
                .setConnectableBus2(b2.getId())
                .setRatedU1(b1.getVoltageLevel().getNominalV())
                .setRatedU2(b2.getVoltageLevel().getNominalV() * rho)
                .setR(r)
                .setX(x)
                .add();
    }

    protected static ThreeWindingsTransformer createThreeWindingsTransformer(Network network, String substationId,
                                                                             Bus b1, Bus b2, Bus b3, String id, double x1, double rho1, double x2, double rho2, double x3, double rho3) {
        return network.getSubstation(substationId).newThreeWindingsTransformer()
                .setId(id)
                .setRatedU0(1)
                .newLeg1()
                .setBus(b1.getId())
                .setConnectableBus(b1.getId())
                .setRatedU(rho1)
                .setR(0)
                .setX(x1)
                .add()
                .newLeg2()
                .setBus(b2.getId())
                .setConnectableBus(b2.getId())
                .setRatedU(rho2)
                .setR(0)
                .setX(x2)
                .add()
                .newLeg3()
                .setBus(b3.getId())
                .setConnectableBus(b3.getId())
                .setRatedU(rho3)
                .setR(0)
                .setX(x3)
                .add()
                .add();
    }

    protected static BoundaryLine createBoundaryLine(Bus b, String id, double x, double p0, double q0) {
        return b.getVoltageLevel().newBoundaryLine()
            .setId(id)
            .setBus(b.getId())
            .setConnectableBus(b.getId())
            .setR(0)
            .setX(x)
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
                .setMaxP(2 * activePowerSetpoint)
                .add();
    }

    protected static VoltageSourceConverter createVoltageSourceConverterPccQac(Bus b, DcNode dn1, DcNode dn2, String id, double targetP, double targetQ) {
        return createVoltageSourceConverterPccQac(b, dn1, dn2, id, 0., 0., 0., targetP, targetQ);
    }

    protected static VoltageSourceConverter createVoltageSourceConverterPccVac(Bus b, DcNode dn1, DcNode dn2, String id, double targetP, double targetVac) {
        return createVoltageSourceConverterPccVac(b, dn1, dn2, id, 0., 0., 0., targetP, targetVac);
    }

    protected static VoltageSourceConverter createVoltageSourceConverterVdcQac(Bus b, DcNode dn1, DcNode dn2, String id, double targetVdc, double targetQ) {
        return createVoltageSourceConverterVdcQac(b, dn1, dn2, id, 0., 0.,0., targetVdc, targetQ);
    }

    protected static VoltageSourceConverter createVoltageSourceConverterVdcVac(Bus b, DcNode dn1, DcNode dn2, String id, double targetVdc, double targetVac) {
        return createVoltageSourceConverterVdcVac(b, dn1, dn2, id, 0., 0., 0., targetVdc,  targetVac);
    }

    protected static VoltageSourceConverter createVoltageSourceConverterPccQac(Bus b, DcNode dn1, DcNode dn2, String id, double idle, double sw, double r, double targetP, double targetQ) {
        return createVoltageSourceConverter(b, dn1, dn2, id, idle, sw, r, AcDcConverter.ControlMode.P_PCC, targetP, Double.NaN, false, Double.NaN, targetQ);
    }

    protected static VoltageSourceConverter createVoltageSourceConverterPccVac(Bus b, DcNode dn1, DcNode dn2, String id, double idle, double sw, double r, double targetP, double targetVac) {
        return createVoltageSourceConverter(b, dn1, dn2, id, idle, sw, r, AcDcConverter.ControlMode.P_PCC, targetP, Double.NaN, true, targetVac, Double.NaN);
    }

    protected static VoltageSourceConverter createVoltageSourceConverterVdcQac(Bus b, DcNode dn1, DcNode dn2, String id, double idle, double sw, double r, double targetVdc, double targetQ) {
        return createVoltageSourceConverter(b, dn1, dn2, id, idle, sw, r, AcDcConverter.ControlMode.V_DC, Double.NaN, targetVdc, false, Double.NaN, targetQ);
    }

    protected static VoltageSourceConverter createVoltageSourceConverterVdcVac(Bus b, DcNode dn1, DcNode dn2, String id, double idle, double sw, double r, double targetVdc, double targetVac) {
        return createVoltageSourceConverter(b, dn1, dn2, id, idle, sw, r, AcDcConverter.ControlMode.V_DC, Double.NaN, targetVdc, true, targetVac, Double.NaN);
    }

    protected static VoltageSourceConverter createVoltageSourceConverter(Bus b, DcNode dn1, DcNode dn2, String id, double idle, double sw, double r, AcDcConverter.ControlMode mode, double targetP, double targetVdc, boolean voltageRegulatorOn, double targetVac, double targetQ) {
        return b.getVoltageLevel().newVoltageSourceConverter()
                .setId(id)
                .setBus1(b.getId())
                .setDcNode1(dn1.getId())
                .setDcNode2(dn2.getId())
                .setDcConnected1(true)
                .setDcConnected2(true)
                .setIdleLoss(idle)
                .setSwitchingLoss(sw)
                .setResistiveLoss(r)
                .setControlMode(mode)
                .setTargetP(targetP)
                .setTargetVdc(targetVdc)
                .setVoltageRegulatorOn(voltageRegulatorOn)
                .setVoltageSetpoint(targetVac)
                .setReactivePowerSetpoint(targetQ)
                .add();
    }

    protected static ShuntCompensator createFixedShuntCompensator(Bus bus, String id, double gPerSection, double bPersection) {
        return createFixedShuntCompensator(bus, id, gPerSection, bPersection, 1);
    }

    protected static ShuntCompensator createFixedShuntCompensator(Bus bus, String id, double gPerSection, double bPersection, int maximumSectionCount) {
        return bus.getVoltageLevel()
                .newShuntCompensator()
                .setId(id)
                .setBus(bus.getId())
                .setConnectableBus(bus.getId())
                .setSectionCount(maximumSectionCount)
                .newLinearModel()
                .setGPerSection(gPerSection)
                .setBPerSection(bPersection)
                .setMaximumSectionCount(maximumSectionCount)
                .add()
                .add();
    }

    protected static ShuntCompensator createShuntCompensator(Bus bus, String id, double g, double b, double v,
                                                             boolean voltageControl) {
        ShuntCompensator sh = bus.getVoltageLevel()
                .newShuntCompensator()
                .setId(id)
                .setBus(bus.getId())
                .setConnectableBus(bus.getId())
                .setSectionCount(1)
                .newNonLinearModel()
                .beginSection()
                .setB(b)
                .setG(g)
                .endSection()
                .add()
                .add();
        sh.setTargetV(v)
                .setRegulatingTerminal(sh.getTerminal())
                .setTargetDeadband(0.0)
                .setVoltageRegulatorOn(voltageControl);
        return sh;
    }

    protected static StaticVarCompensator createStaticVarCompensator(Bus bus, String id, double qSetpoint,
                                                                     double vSetpoint, RegulationMode regulationMode) {
        StaticVarCompensator svc = bus.getVoltageLevel()
                .newStaticVarCompensator()
                .setId(id)
                .setBus(bus.getId())
                .setConnectableBus(bus.getId())
                .setBmin(-1.0)
                .setBmax(1.0)
                .setRegulating(false)
                .setRegulationMode(RegulationMode.VOLTAGE)
                .add();
        svc.setRegulatingTerminal(svc.getTerminal())
                .setVoltageSetpoint(vSetpoint)
                .setReactivePowerSetpoint(qSetpoint)
                .setRegulationMode(regulationMode)
                .setRegulating(true);

        return svc;
    }
}

