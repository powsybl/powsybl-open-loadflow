package com.powsybl.openloadflow.ac.util;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;

public class NetworkBuilder {
    private Network network;
    private VoltageLevel vl1;
    private Bus bus1;
    private VoltageLevel vl2;
    private Bus bus2;
    private Line bus1bus2line;
    private Generator bus1gen;
    private Load bus2ld;
    private StaticVarCompensator bus2svc;
    private Generator bus2gen;
    private ShuntCompensator bus2sc;
    private Line bus2openLine;

    public NetworkBuilder addNetworkBus1GenBus2Svc() {
        network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("s1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("s2")
                .add();
        vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("bus1")
                .add();
        bus1gen = vl1.newGenerator()
                .setId("bus1gen")
                .setConnectableBus(bus1.getId())
                .setBus(bus1.getId())
                .setTargetP(101.3664)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("bus2")
                .add();
        bus2svc = vl2.newStaticVarCompensator()
                .setId("bus2svc")
                .setConnectableBus(bus2.getId())
                .setBus(bus2.getId())
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();
        bus1bus2line = network.newLine()
                .setId("bus1bus2line")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        return this;
    }

    public NetworkBuilder addBus2Load() {
        bus2ld = vl2.newLoad()
                .setId("bus2ld")
                .setConnectableBus(bus2.getId())
                .setBus(bus2.getId())
                .setP0(101)
                .setQ0(150)
                .add();
        return this;
    }

    public NetworkBuilder setBus2SvcVoltageAndSlope() {
        bus2svc.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.01)
                .add();
        return this;
    }

    public NetworkBuilder setBus2SvcRegulationMode(StaticVarCompensator.RegulationMode regulationMode) {
        switch (regulationMode) {
            case VOLTAGE:
                bus2svc.setVoltageSetpoint(385)
                        .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
                break;
            case REACTIVE_POWER:
                bus2svc.setReactivePowerSetpoint(300)
                        .setRegulationMode(StaticVarCompensator.RegulationMode.REACTIVE_POWER);
                break;
            case OFF:
                bus2svc.setRegulationMode(StaticVarCompensator.RegulationMode.OFF);
                break;
        }
        return this;
    }

    public NetworkBuilder addBus2Gen() {
        bus2gen = bus2.getVoltageLevel()
                .newGenerator()
                .setId("bus2gen")
                .setBus(bus2.getId())
                .setConnectableBus(bus2.getId())
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(100)
                .setMaxP(300)
                .setTargetP(0)
                .setTargetQ(300)
                .setVoltageRegulatorOn(false)
                .add();
        return this;
    }

    public NetworkBuilder setBus2GenRegulationMode(boolean voltageRegulatorOn) {
        if (voltageRegulatorOn) {
            bus2gen.setTargetV(385);
        } else {
            bus2gen.setTargetQ(300);
        }
        bus2gen.setVoltageRegulatorOn(voltageRegulatorOn);
        return this;
    }

    public NetworkBuilder addBus2Sc() {
        bus2sc = bus2.getVoltageLevel().newShuntCompensator()
                .setId("bus2sc")
                .setBus(bus2.getId())
                .setConnectableBus(bus2.getId())
                .setSectionCount(1)
                .newLinearModel()
                .setBPerSection(Math.pow(10, -4))
                .setMaximumSectionCount(1)
                .add()
                .add();
        return this;
    }

    /**
     *
     * @param bus bus1 or bus2
     * @return
     */
    private NetworkBuilder addOpenLine(String bus) {
        bus2openLine = network.newLine()
                .setId(bus + "openLine")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus.equals("bus1") ? bus1.getId() : null)
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus.equals("bus2") ? bus2.getId() : null)
                .setConnectableBus2(bus2.getId())
                .setR(1)
                .setX(3)
                .setG1(1E-6d)
                .setG2(1E-6d)
                .setB1(1E-6d)
                .setB2(1E-6d)
                .add();
        return this;
    }

    public NetworkBuilder addBus1OpenLine() {
        return addOpenLine("bus1");
    }

    public NetworkBuilder addBus2OpenLine() {
        return addOpenLine("bus2");
    }

    public Network build() {
        return network;
    }

    public VoltageLevel getVl1() {
        return vl1;
    }

    public Bus getBus1() {
        return bus1;
    }

    public VoltageLevel getVl2() {
        return vl2;
    }

    public Bus getBus2() {
        return bus2;
    }

    public Line getBus1bus2line() {
        return bus1bus2line;
    }

    public Generator getBus1gen() {
        return bus1gen;
    }

    public Load getBus2ld() {
        return bus2ld;
    }

    public StaticVarCompensator getBus2svc() {
        return bus2svc;
    }

    public Generator getBus2gen() {
        return bus2gen;
    }

    public ShuntCompensator getBus2sc() {
        return bus2sc;
    }

    public Line getBus2openLine() {
        return bus2openLine;
    }
}
