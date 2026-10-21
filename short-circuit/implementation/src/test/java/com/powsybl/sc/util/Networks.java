/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class Networks {

    private Networks() {
    }

    public static Network create4n() {
        Network network = Network.create("4n", "test");
        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(100.0).setAngle(0.);

        Substation substation2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = substation2.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(100.0).setAngle(0);
        Generator gen2 = vl2.newGenerator()
                .setId("G2")
                .setBus(bus2.getId())
                .setMinP(0.0)
                .setMaxP(150)
                .setTargetP(10)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        gen2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(20)
                .withDirectTransX(20)
                .withStepUpTransformerX(0.)
                .add();

        Substation substation3 = network.newSubstation()
                .setId("S3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl3 = substation3.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(100.0).setAngle(0.);
        vl3.newLoad()
                .setId("LOAD_3")
                .setBus(bus3.getId())
                .setP0(10.0)
                .setQ0(100.)
                .add();

        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(100.0).setAngle(0.);

        network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B1_B3")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B1_B4")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.6)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        return network;
    }

    public static Network create4nShunt() {
        Network network = Network.create("4n_shunt", "test");
        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(100.0).setAngle(0.);

        Substation substation2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = substation2.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(100.0).setAngle(0);
        vl2.newGenerator()
                .setId("G2")
                .setBus(bus2.getId())
                .setMinP(0.0)
                .setMaxP(150)
                .setTargetP(10)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        Substation substation3 = network.newSubstation()
                .setId("S3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl3 = substation3.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(100.0).setAngle(0.);
        vl3.newLoad()
                .setId("LOAD_3")
                .setBus(bus3.getId())
                .setP0(10.0)
                .setQ0(100.)
                .add();

        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(100.0).setAngle(0.);
        //add a shunt compared to the previous 4n network
        vl4.newShuntCompensator()
                .setId("SHUNT_4")
                .setBus(bus4.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel().setMaximumSectionCount(1).setBPerSection(-0.00105).add()
                .add();

        network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B1_B3")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B1_B4")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.6)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        return network;
    }

    public static Network create4nShuntEq() {
        Network network = Network.create("4n_Shunt_Eq", "test");
        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(100.0).setAngle(0.);

        Substation substation2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = substation2.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(100.0).setAngle(0);
        vl2.newGenerator()
                .setId("G2")
                .setBus(bus2.getId())
                .setMinP(0.0)
                .setMaxP(150)
                .setTargetP(10)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        Substation substation3 = network.newSubstation()
                .setId("S3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl3 = substation3.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(100.0).setAngle(0.);
        vl3.newLoad()
                .setId("LOAD_3")
                .setBus(bus3.getId())
                .setP0(10.0)
                .setQ0(100.)
                .add();

        //no node 4 in 4n equivalent shunt resulting from the suppression of node 4

        network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B1_B3")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.6)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        //no line connected to node 4

        /////////////////////
        //add equivalent part
        /////////////////////
        // Reduction hypothesis : add line x = 0.0450525 (lf pu) between B1 and B3
        network.newLine()
                .setId("B1_B3_eq")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(4.50525) //pu in the lf gives a / 100 factor
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        //reduction hypothesis got from 4n shunt = Equivalent shunt g=0.0 b=-0.04661228566671838 at bus num=1
        //reduction hypothesis got from 4n shunt =  Equivalent shunt g=0.0 b=-0.05826535708339975 at bus num=3
        vl3.newShuntCompensator()
                .setId("SHUNT_3_EQ")
                .setBus(bus3.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel().setMaximumSectionCount(1).setBPerSection(-0.00058265).add() //lf pu = / 100
                .add();
        vl1.newShuntCompensator()
                .setId("SHUNT_1_EQ")
                .setBus(bus1.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel().setMaximumSectionCount(1).setBPerSection(-0.00046612).add() //lf pu = / 100
                .add();

        return network;
    }

}

