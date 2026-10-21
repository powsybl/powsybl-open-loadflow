/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.sc.extensions.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class ReferenceNetwork {

    private ReferenceNetwork() {
    }

    @SuppressWarnings("checkstyle:MethodLength")
    public static Network createShortCircuitReference() {
        Network network = Network.create("ShortCircuitReference", "reference");

        // This is an 8 bus grid used as a reference in many paper to get the equivalent Thevenin impedance for a balanced short circuit
        // Sbase = 15 MVA
        double sbase = 15.;
        //
        // Bus1 Vnom = 115 kV
        double bus1Vnom = 115.;
        //
        // Utility at bus 1: Sutility=1500 MVA, X/R = 15, Xpu = Sbase/Sutility which gives Zpu = 0.0007+j0.01
        // Z=VnomBus1Â²/Sbase * Zpu = 0.617167 + j8.81667 ohms which gives yeq= 0.007901-j0.112868 and then Peq = 104.488 MW and Qeq = 1492.69 MVAR
        double xdUtility = 0.01 * bus1Vnom * bus1Vnom / sbase; //8.81667;
        double rdUtility = 0.0007 * bus1Vnom * bus1Vnom / sbase; //0.617167;
        //
        // Bus2 Vnom = 13.8 kV
        double bus2Vnom = 13.8;
        //
        // Transformer T1 (bus1 - bus2) : ST1 = 15 MVA and X/R = 20 and XT1% = 7%
        // |Zpu| = XT1%/100 * (Sbase/ST1) which gives : Zpu = 0.0035 + j0.0699 which gives Z = Zpu * Zbase2 = 0.044436+j0.88745 ohms
        double xT1 = 0.0699 * bus2Vnom * bus2Vnom / sbase; //0.88745;
        double rT1 = 0.0035 * bus2Vnom * bus2Vnom / sbase; //0.044436;
        //
        // Motor M1 cable (bus2 - bus3) : Zpu = 0.0008 + j0.0003 which and Z = 0.00977+j0.003809 ohms
        double xl23 = 0.0003 * bus2Vnom * bus2Vnom / sbase; //0.003809;
        double rl23 = 0.0008 * bus2Vnom * bus2Vnom / sbase; //0.00977;
        //
        // Motor M1 at node bus3: synchronous machine, SM1=4000 HP, Xd'%=15% and X/R=28.9
        // Xd'pu = Xd'%/100*Sbase/(SM1*0.8) then Zpu = 0.0251+j0.703 then Z = 0.31867+j8.92529
        double xM1 = 0.703 * bus2Vnom * bus2Vnom / sbase; //8.92529;
        double rM1 = 0.0251 * bus2Vnom * bus2Vnom / sbase; //0.31867;
        //
        // Cable bus2 - bus 4: Z = 0.0182+j0.01077 then Zpu = 0.001451+j0.000848
        double xl24 = 0.000848 * bus2Vnom * bus2Vnom / sbase; //0.01077;
        double rl24 = 0.001451 * bus2Vnom * bus2Vnom / sbase; //0.0182;
        //
        // bus5 Vnom=2.4 kV
        double bus5Vnom = 2.4;
        //
        // Transformer T2 (bus4 - bus5): ST2 = 3.75 MVA , X/R = 11 and XT2% = 5.5%
        // |Zpu| = XT2%/100 * (Sbase/ST2) which gives : Zpu = 0.0199+j0.2191 then Z = Zpu * Zbase5 = 0.007642+j0.08134
        double xT2 = 0.2191 * bus5Vnom * bus5Vnom / sbase; //0.08134;
        double rT2 = 0.0199 * bus5Vnom * bus5Vnom / sbase; //0.007642;
        //
        // Motor M2 at node bus5: induction machine, SM1=500 HP, Xd'%=16.7% and X/R=19.3
        // Xd'pu = Xd'%/100*Sbase/(SM2*0.95) then Zpu = 0.3331+j6.3284 then Z = 0.12791+j2.43011
        double xM2 = 6.3284 * bus5Vnom * bus5Vnom / sbase; //2.43011;
        double rM2 = 0.3331 * bus5Vnom * bus5Vnom / sbase; //0.12791;
        //
        // Motor M3 at node bus5: induction machine, SM1=2000 HP, Xd'%=16.7% and X/R=30
        // Xd'pu = Xd'%/100*Sbase/(SM3*0.9) then Zpu = 0.0449+j1.3917 then Z = 0.017242+j0.534413
        double xM3 = 1.3917 * bus5Vnom * bus5Vnom / sbase; //0.534413;
        double rM3 = 0.0449 * bus5Vnom * bus5Vnom / sbase; //0.017242;
        //
        // Cable bus2 - bus6: Zpu = 0.0049+j0.0007 and Z = 0.06228+j0.00944
        double xl26 = 0.0007 * bus2Vnom * bus2Vnom / sbase; //0.00944;
        double rl26 = 0.0049 * bus2Vnom * bus2Vnom / sbase; //0.06228;
        //
        // bus7 Vnom=0.277 kV
        double bus7Vnom = 0.277;
        //
        // Transformer T3 (bus6 - bus7): ST3 = 1.5 MVA, X/R = 6.5 and XT3% = 5.75%
        // |Zpu| = XT3%/100 * (Sbase/ST3) which gives : Zpu = 0.0874+j0.5683 then Z = Zpu * Zbase7 = 0.000447+j0.002907
        double xT3 = 0.5683 * bus7Vnom * bus7Vnom / sbase; //0.002907;
        double rT3 = 0.0874 * bus7Vnom * bus7Vnom / sbase; //0.000447;
        //
        // Motor M4 at bus7: Zpu = 0.446924+j*2.3148 and Z = 0.002286+j0.011841
        double xM4SubTrans = 2.3148 * bus7Vnom * bus7Vnom / sbase; //0.011841;
        double rM4SubTrans = 0.446924 * bus7Vnom * bus7Vnom / sbase; //0.002286;
        double xM4Trans = 9.3985 * bus7Vnom * bus7Vnom / sbase;
        double rM4Trans = 1.3614 * bus7Vnom * bus7Vnom / sbase;

        //
        // Cable bus7 - bus8: Zpu = 0.8691+j0.6966 and Z = 0.01335+j0.0107
        double xl78 = 0.6966 * bus7Vnom * bus7Vnom / sbase; //0.0107;
        double rl78 = 0.8691 * bus7Vnom * bus7Vnom / sbase; //0.01335;

        Substation substation12 = network.newSubstation()
                .setId("S12")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation12.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(bus1Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus1Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(bus1Vnom).setAngle(0.);

        VoltageLevel vl2 = substation12.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(bus2Vnom).setAngle(0);

        Substation substation3 = network.newSubstation()
                .setId("S3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl3 = substation3.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(bus2Vnom).setAngle(0);

        Substation substation45 = network.newSubstation()
                .setId("S45")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation45.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(bus2Vnom).setAngle(0);

        VoltageLevel vl5 = substation45.newVoltageLevel()
                .setId("VL_5")
                .setNominalV(bus5Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus5Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus5 = vl5.getBusBreakerView().newBus()
                .setId("B5")
                .add();
        bus5.setV(bus5Vnom).setAngle(0);

        Substation substation67 = network.newSubstation()
                .setId("S6")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl6 = substation67.newVoltageLevel()
                .setId("VL_6")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus6 = vl6.getBusBreakerView().newBus()
                .setId("B6")
                .add();
        bus6.setV(bus2Vnom).setAngle(0);

        VoltageLevel vl7 = substation67.newVoltageLevel()
                .setId("VL_7")
                .setNominalV(bus7Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus7Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus7 = vl7.getBusBreakerView().newBus()
                .setId("B7")
                .add();
        bus7.setV(bus7Vnom).setAngle(0);

        Substation substation8 = network.newSubstation()
                .setId("S8")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl8 = substation8.newVoltageLevel()
                .setId("VL_8")
                .setNominalV(bus7Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus7Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus8 = vl8.getBusBreakerView().newBus()
                .setId("B8")
                .add();
        bus8.setV(bus7Vnom).setAngle(0);

        Generator utility = vl1.newGenerator()
                .setId("Utility")
                .setBus(bus1.getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setTargetV(bus1Vnom)
                .setVoltageRegulatorOn(true)
                .add();

        utility.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xdUtility)
                .withDirectTransX(xdUtility) //put in per unit Sbase = 100. to be compatible with the per unit of the rest of the grid
                .withStepUpTransformerX(0.)
                .add();

        Generator m1 = vl3.newGenerator()
                .setId("M1")
                .setBus(bus3.getId())
                .setMinP(-100.0)
                .setMaxP(0.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(bus2Vnom)
                .setVoltageRegulatorOn(false)
                .add();

        m1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM1)
                .withDirectTransX(xM1 * 1.5)
                .withStepUpTransformerX(0.)
                .add();

        Generator m2 = vl5.newGenerator()
                .setId("M2")
                .setBus(bus5.getId())
                .setMinP(-100.0)
                .setMaxP(0.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(bus5Vnom)
                .setVoltageRegulatorOn(false)
                .add();

        m2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM2)
                .withDirectTransX(xM2 / 0.4)
                .withStepUpTransformerX(0.)
                .add();

        Generator m3 = vl5.newGenerator()
                .setId("M3")
                .setBus(bus5.getId())
                .setMinP(-100.0)
                .setMaxP(0.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(bus5Vnom)
                .setVoltageRegulatorOn(false)
                .add();

        m3.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM3)
                .withDirectTransX(xM3 * 1.5)
                .withStepUpTransformerX(0.)
                .add();

        Generator m4 = vl7.newGenerator()
                .setId("M4")
                .setBus(bus7.getId())
                .setMinP(-100.0)
                .setMaxP(0.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(bus7Vnom)
                .setVoltageRegulatorOn(false)
                .add();

        m4.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM4SubTrans)
                .withDirectTransX(xM4Trans)
                .withStepUpTransformerX(0.)
                .add();

        network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(rl23)
                .setX(xl23)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        network.newLine()
                .setId("B2_B4")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(rl24)
                .setX(xl24)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        network.newLine()
                .setId("B2_B6")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl6.getId())
                .setBus2(bus6.getId())
                .setConnectableBus2(bus6.getId())
                .setR(rl26)
                .setX(xl26)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        network.newLine()
                .setId("B7_B8")
                .setVoltageLevel1(vl7.getId())
                .setBus1(bus7.getId())
                .setConnectableBus1(bus7.getId())
                .setVoltageLevel2(vl8.getId())
                .setBus2(bus8.getId())
                .setConnectableBus2(bus8.getId())
                .setR(rl78)
                .setX(xl78)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        substation12.newTwoWindingsTransformer()
                .setId("T1")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setRatedU2(bus2Vnom)
                .setR(rT1)
                .setX(xT1)
                .setG(0.0D)
                .setB(0.0D).add();
        substation45.newTwoWindingsTransformer()
                .setId("T2")
                .setVoltageLevel1(vl4.getId())
                .setBus1(bus4.getId())
                .setConnectableBus1(bus4.getId())
                .setRatedU1(bus2Vnom)
                .setVoltageLevel2(vl5.getId())
                .setBus2(bus5.getId())
                .setConnectableBus2(bus5.getId())
                .setRatedU2(bus5Vnom)
                .setR(rT2)
                .setX(xT2)
                .setG(0.0D)
                .setB(0.0D)
                .add();
        substation67.newTwoWindingsTransformer()
                .setId("T3")
                .setVoltageLevel1(vl6.getId())
                .setBus1(bus6.getId())
                .setConnectableBus1(bus6.getId())
                .setRatedU1(bus2Vnom)
                .setVoltageLevel2(vl7.getId())
                .setBus2(bus7.getId())
                .setConnectableBus2(bus7.getId())
                .setRatedU2(bus7Vnom)
                .setR(rT3)
                .setX(xT3)
                .setG(0.0D)
                .setB(0.0D)
                .add();

        //map to store the real part of the transient impedance
        utility.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rdUtility)
                .withSubTransRd(rdUtility)
                .add();
        m1.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM1 * 1.5)
                .withSubTransRd(rM1)
                .add();
        m2.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM2 / 0.4)
                .withSubTransRd(rM2)
                .add();
        m3.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM3 * 1.5)
                .withSubTransRd(rM3)
                .add();
        m4.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM4Trans)
                .withSubTransRd(rM4SubTrans)
                .add();

        return network;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    public static Network create6NodesIec9094() {
        Network network = Network.create("ShortCircuit_IEC_3.1", "IEC_3.1");
        // This is a reference grid composed of 6 nodes with 20 kV and 400V nominal voltages

        double bus1Vnom = 20.; // 20 kV
        double bus2Vnom = 0.4; // 400 V

        double ratedV2 = 0.41; // 410 V

        double kT1 = 0.975;
        double xT1 = 0.010312 * kT1;
        double rT1 = 0.002753 * kT1;
        double coeffRoT1 = 1.;
        double coeffXoT1 = 0.95;

        double kT2 = 0.975;
        double xT2 = 0.016100 * kT2;
        double rT2 = 0.004833 * kT2;
        double coeffRoT2 = 1.;
        double coeffXoT2 = 0.95;

        double xL2 = 0.000136;
        double rL2 = 0.000416;
        double coeffRoL2 = 4.23;
        double coeffXoL2 = 1.21;

        double xL1 = 0.000395;
        double rL1 = 0.000385;
        double coeffRoL1 = 3.7;
        double coeffXoL1 = 1.81;

        double xL3 = 0.001740;
        double rL3 = 0.005420;
        double coeffRoL3 = 3.;
        double coeffXoL3 = 4.46;

        double xL4 = 0.01485;
        double rL4 = 0.01850;
        double coeffRoL4 = 2.;
        double coeffXoL4 = 3.;

        // we model the feeder through a load: TODO : check if relevant for all IEC examples
        // Given the formula: |Zfeeder| = c.Un/(sqrt(3).Ik".rho²) at Vnom = 0.41 kV
        // Then Xfeeder = |Zfeeder| * 0.995 and Rfeeder = 0.1 * Xfeeder
        // Impedance to be applied at feeder : Zfeeder = 0.126115 +j1.26353 ohms at 20 kV (or Zfeeder = 0.053 +j0.531 mohms at 0.41kV as given in the example with rho)

        //using formula P(MW) = Re(Z) * |V|² / |Z|² and Q(MVA) = Im(Z) * |V|² / |Z|² we transform the feeder into a load
        double pEquivalentFeeder = 31.286;
        double qEquivalentFeeder = 313.451;

        Substation substation123 = network.newSubstation()
                .setId("S123")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation123.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(bus1Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus1Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(bus1Vnom).setAngle(0.);

        VoltageLevel vl2 = substation123.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(bus2Vnom).setAngle(0.);

        VoltageLevel vl3 = substation123.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(bus2Vnom).setAngle(0.);

        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(bus2Vnom).setAngle(0.);

        Substation substation5 = network.newSubstation()
                .setId("S5")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl5 = substation5.newVoltageLevel()
                .setId("VL_5")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus5 = vl5.getBusBreakerView().newBus()
                .setId("B5")
                .add();
        bus5.setV(bus2Vnom).setAngle(0.);

        Substation substation6 = network.newSubstation()
                .setId("S6")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl6 = substation6.newVoltageLevel()
                .setId("VL_6")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus6 = vl6.getBusBreakerView().newBus()
                .setId("B6")
                .add();
        bus6.setV(bus2Vnom).setAngle(0.);

        vl1.newLoad()
                .setId("LOAD_FEEDER") // we try to model the feeder through a load that will be transformed into an impedance
                .setBus(bus1.getId())
                .setP0(pEquivalentFeeder)
                .setQ0(qEquivalentFeeder)
                .add();

        var t1 = substation123.newTwoWindingsTransformer()
                .setId("T1")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setRatedU2(ratedV2)
                .setR(rT1)
                .setX(xT1)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(630.)
                .add();
        var t2 = substation123.newTwoWindingsTransformer()
                .setId("T2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setRatedU2(ratedV2)
                .setR(rT2)
                .setX(xT2)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(400.)
                .add();

        Line l2 = network.newLine()
                .setId("L2_B2_B4")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(rL2)
                .setX(xL2)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l1 = network.newLine()
                .setId("L1_B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(rL1)
                .setX(xL1)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l3 = network.newLine()
                .setId("L3_B4_B5")
                .setVoltageLevel1(vl4.getId())
                .setBus1(bus4.getId())
                .setConnectableBus1(bus4.getId())
                .setVoltageLevel2(vl5.getId())
                .setBus2(bus5.getId())
                .setConnectableBus2(bus5.getId())
                .setR(rL3)
                .setX(xL3)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l4 = network.newLine()
                .setId("L4_B5_B6")
                .setVoltageLevel1(vl5.getId())
                .setBus1(bus5.getId())
                .setConnectableBus1(bus5.getId())
                .setVoltageLevel2(vl6.getId())
                .setBus2(bus6.getId())
                .setConnectableBus2(bus6.getId())
                .setR(rL4)
                .setX(xL4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        //additional data

        l1.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL1 * rL1)
                .withXz(coeffXoL1 * xL1)
                .add();
        l2.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL2 * rL2)
                .withXz(coeffXoL2 * xL2)
                .add();
        l3.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL3 * rL3)
                .withXz(coeffXoL3 * xL3)
                .add();
        l4.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4)
                .withXz(coeffXoL4 * xL4)
                .add();

        t1.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(coeffRoT1 * rT1)
                .withXz(coeffXoT1 * xT1)
                .withConnectionType1(WindingConnectionType.DELTA)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();
        t2.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(coeffRoT2 * rT2)
                .withXz(coeffXoT2 * xT2)
                .withConnectionType1(WindingConnectionType.DELTA)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();

        return network;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    public static Network create8NodesIEC9094() {
        //This example grid represents the high-voltage testnetwork 380kV / 110kV / 30 kV / 10 kV with 8 bus bars
        Network network = Network.create("ShortCircuit_IEC_3.1", "IEC_3.1");

        double vEhv = 380.; // Vnom at bus 1 is 380 kV
        double vHv = 110.; // high voltage = 110 kV
        double vMv = 30.; // medium voltage is 30 kV
        double vLv = 10.; // low voltage is 10 kV

        double xG1 = 26.336676;
        double rG1 = 0.498795;
        double coeffRoG1 = 0.439059 / rG1; // grounded = true
        double xo1ground = 66.;
        double coeffXoG1 = (13.340874 + xo1ground) / xG1;

        double xG2 = 35.340713;
        double rG2 = 1.203944;

        double rG3 = 0.01779;
        double xG3 = 1.089623;

        double xM1 = 3.414968;
        double rM1 = 0.341497;

        double xM2 = 4.121368;
        double rM2 = 0.412137;

        double xL1 = 7.8;
        double rL1 = 2.4;
        double coeffRoL1 = 6.4 / rL1;
        double coeffXoL1 = 25.2 / xL1;

        double xL2 = 3.9;
        double rL2 = 1.2;
        double coeffRoL2 = 3.2 / rL2;
        double coeffXoL2 = 12.6 / xL2;

        double xL3 = 0.975; // TODO : check if this 2 times or only once
        double rL3 = 0.3;
        double coeffRoL3 = 1.3 / rL3;
        double coeffXoL3 = 4.65 / xL3;

        double xL4 = 3.88;
        double rL4 = 0.96;
        double coeffRoL4 = 2.2 / rL4;
        double coeffXoL4 = 11. / xL4;

        double xL5 = 5.79;
        double rL5 = 1.8;
        double coeffRoL5 = 3.3 / rL5;
        double coeffXoL5 = 16.5 / xL5;

        double xL6 = 0.086;
        double rL6 = 0.082;
        double coeffRoL6 = 1.0; // not used
        double coeffXoL6 = 1.0; // not used

        double rho52 = 115 * 115 / (10.5 * 10.5); // Unlike presented in the doc, rT5 and xT5 are expressed on the HV side, we need to divide by rho5Â²
        double rT5 = 2.046454 / rho52;
        double xT5 = 49.072241 / rho52;

        double rT6 = rT5;
        double xT6 = xT5;

        double rhoB2 = 1. / (120. * 120.);
        double rT3a = 0.045714 * rhoB2;
        double xT3a = 8.0969989 * rhoB2;
        double rT3b = 0.053563 * rhoB2;
        double xT3b = -0.079062 * rhoB2;
        double rT3c = 0.408560 * rhoB2;
        double xT3c = 20.292035 * rhoB2;

        double coeffRoT4 = 0.107281 / (rT3b + rT3c) * rhoB2;
        double coeffXoT4 = 18.195035 / (xT3b + xT3c) * rhoB2;

        Substation substation1289 = network.newSubstation()
                .setId("S1289")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1289.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(vEhv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vEhv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(vEhv).setAngle(0.);

        VoltageLevel vl2 = substation1289.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(vHv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vHv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(vHv).setAngle(0.);

        VoltageLevel vl8 = substation1289.newVoltageLevel()
                .setId("VL_8")
                .setNominalV(vMv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vMv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus8 = vl8.getBusBreakerView().newBus()
                .setId("B8")
                .add();
        bus8.setV(vMv).setAngle(0.);

        VoltageLevel vl9 = substation1289.newVoltageLevel()
                .setId("VL_9")
                .setNominalV(vMv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vMv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus9 = vl9.getBusBreakerView().newBus()
                .setId("B9")
                .add();
        bus9.setV(vMv).setAngle(0.);

        Substation substation3 = network.newSubstation()
                .setId("S3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl3 = substation3.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(vHv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vHv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(vHv).setAngle(0.);

        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(vHv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vHv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(vHv).setAngle(0.);

        Substation substation56 = network.newSubstation()
                .setId("S56")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl5 = substation56.newVoltageLevel()
                .setId("VL_5")
                .setNominalV(vHv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vHv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus5 = vl5.getBusBreakerView().newBus()
                .setId("B5")
                .add();
        bus5.setV(vHv).setAngle(0.);

        VoltageLevel vl6 = substation56.newVoltageLevel()
                .setId("VL_6")
                .setNominalV(vLv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vLv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus6 = vl6.getBusBreakerView().newBus()
                .setId("B6")
                .add();
        bus6.setV(vLv).setAngle(0.);

        Substation substation7 = network.newSubstation()
                .setId("S7")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl7 = substation7.newVoltageLevel()
                .setId("VL_7")
                .setNominalV(vLv)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * vLv)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus7 = vl7.getBusBreakerView().newBus()
                .setId("B7")
                .add();
        bus7.setV(vLv).setAngle(0.);

        // Feeders :
        // Same as previous IEC example : we model the feeder through a load: TODO : check if relevant for all IEC examples
        double pEquivalentFeeder1 = 2262.42;  //Impedance to be applied at feeder : Zfeeder = 0.631933 +j6.319335 ohms
        double qEquivalentFeeder1 = 22624.3; //using formula P(MW) = Re(Z) * |V|Â² / |Z|Â² and Q(MVA) = Im(Z) * |V|Â² / |Z|Â²

        double pEquivalentFeeder2 = 275.753;
        double qEquivalentFeeder2 = 2757.53;
        double coeffF2Ro = 6.6;
        double coeffF2Xo = 3.3;

        vl1.newLoad()
                .setId("LOAD_FEEDER1") // we try to model the feeder through a load that will be transformed into an impedance
                .setBus(bus1.getId())
                .setP0(pEquivalentFeeder1)
                .setQ0(qEquivalentFeeder1)
                .add();

        // This feeder is changed into a machine because it is not yet possible to model a grounded feeder with a load
        /*vl5.newLoad()
                .setId("LOAD_FEEDER2") // we try to model the feeder through a load that will be transformed into an impedance
                .setBus(bus5.getId())
                .setP0(pEquivalentFeeder2)
                .setQ0(qEquivalentFeeder2)
                .add();*/

        Generator q2 = vl5.newGenerator() //alternative modelling
                .setId("Q2")
                .setBus(bus5.getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(vHv)
                .setVoltageRegulatorOn(false)
                .add();

        double xFeeder2 = 4.344543;
        double rFeeder2 = 0.434454;
        q2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xFeeder2) // TODO : add table to store coeffs for homopolar values
                .withDirectTransX(xFeeder2)
                .withStepUpTransformerX(0.)
                .add();

        // Generating units :
        Generator g1 = vl4.newGenerator()
                .setId("G1")
                .setBus(bus4.getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(vHv)
                .setVoltageRegulatorOn(false)
                .setRatedS(150.)
                .add();

        g1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xG1) // TODO : add table to store coeffs for homopolar values
                .withDirectTransX(xG1)
                .withStepUpTransformerX(0.)
                .add();

        Generator g2 = vl3.newGenerator()
                .setId("G2")
                .setBus(bus3.getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(vHv)
                .setVoltageRegulatorOn(false)
                .add();

        g2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xG2) // TODO : add table to store coeffs for homopolar values
                .withDirectTransX(xG2)
                .withStepUpTransformerX(0.)
                .add();

        Generator g3 = vl6.newGenerator()
                .setId("G3")
                .setBus(bus6.getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(vLv)
                .setVoltageRegulatorOn(false)
                .add();

        g3.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xG3) // TODO : add table to store coeffs for homopolar values
                .withDirectTransX(xG3)
                .withStepUpTransformerX(0.)
                .add();

        // Machines :
        // They are considered as non-grounded generating units
        Generator m1 = vl7.newGenerator()
                .setId("M1")
                .setBus(bus7.getId())
                .setMinP(-100.0)
                .setMaxP(0.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(vLv)
                .setVoltageRegulatorOn(false)
                .add();

        m1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM1) // TODO : add table to store coeffs for homopolar values
                .withDirectTransX(xM1)
                .withStepUpTransformerX(0.)
                .add();

        Generator m2 = vl7.newGenerator()
                .setId("M2")
                .setBus(bus7.getId())
                .setMinP(-100.0)
                .setMaxP(0.)
                .setTargetP(0.)
                .setTargetQ(0.)
                .setTargetV(vLv)
                .setVoltageRegulatorOn(false)
                .add();

        m2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM2) // TODO : add table to store coeffs for homopolar values
                .withDirectTransX(xM2)
                .withStepUpTransformerX(0.)
                .add();

        Line l1 = network.newLine()
                .setId("L1_B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(rL1)
                .setX(xL1)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l2 = network.newLine()
                .setId("L2_B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(rL2)
                .setX(xL2)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l3 = network.newLine()
                .setId("L3_B2_B5")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl5.getId())
                .setBus2(bus5.getId())
                .setConnectableBus2(bus5.getId())
                .setR(rL3)
                .setX(xL3)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l4 = network.newLine()
                .setId("L4_B5_B3")
                .setVoltageLevel1(vl5.getId())
                .setBus1(bus5.getId())
                .setConnectableBus1(bus5.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(rL4)
                .setX(xL4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l5 = network.newLine()
                .setId("L5_B5_B4")
                .setVoltageLevel1(vl5.getId())
                .setBus1(bus5.getId())
                .setConnectableBus1(bus5.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(rL5)
                .setX(xL5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l6 = network.newLine()
                .setId("L6_B6_B7")
                .setVoltageLevel1(vl6.getId())
                .setBus1(bus6.getId())
                .setConnectableBus1(bus6.getId())
                .setVoltageLevel2(vl7.getId())
                .setBus2(bus7.getId())
                .setConnectableBus2(bus7.getId())
                .setR(rL6)
                .setX(xL6)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        var t5 = substation56.newTwoWindingsTransformer()
                .setId("T5")
                .setVoltageLevel1(vl5.getId())
                .setBus1(bus5.getId())
                .setConnectableBus1(bus5.getId())
                .setRatedU1(115.)
                .setRatedS(100.)
                .setVoltageLevel2(vl6.getId())
                .setBus2(bus6.getId())
                .setConnectableBus2(bus6.getId())
                .setRatedU2(10.5)
                .setR(rT5)
                .setX(xT5)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(31.5)
                .add();

        var t6 = substation56.newTwoWindingsTransformer()
                .setId("T6")
                .setVoltageLevel1(vl5.getId())
                .setBus1(bus5.getId())
                .setConnectableBus1(bus5.getId())
                .setRatedU1(115.)
                .setVoltageLevel2(vl6.getId())
                .setBus2(bus6.getId())
                .setConnectableBus2(bus6.getId())
                .setRatedU2(10.5)
                .setR(rT6)
                .setX(xT6)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(31.5)
                .add();

        ThreeWindingsTransformer twt3 = substation1289.newThreeWindingsTransformer()
                .setId("T3")
                .setRatedU0(1.0D)
                .newLeg1()
                .setR(rT3a)
                .setX(xT3a)
                .setG(0.)
                .setB(0.)
                .setRatedU(400.0D)
                .setRatedS(100.0D)
                .setVoltageLevel(vl1.getId())
                .setBus(bus1.getId())
                .add()
                .newLeg2()
                .setR(rT3b)
                .setX(xT3b)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedU(120.0D)
                .setRatedS(100.0D)
                .setVoltageLevel(vl2.getId())
                .setBus(bus2.getId())
                .add()
                .newLeg3()
                .setR(rT3c)
                .setX(xT3c)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedU(30.)
                .setRatedS(100.0D)
                .setVoltageLevel(vl8.getId())
                .setBus(bus8.getId())
                .add()
                .add();
        ThreeWindingsTransformer twt4 = substation1289.newThreeWindingsTransformer()
                .setId("T4")
                .setRatedU0(1.0D)
                .newLeg1()
                .setR(rT3a)
                .setX(xT3a)
                .setG(0.)
                .setB(0.)
                .setRatedU(400.0D)
                .setVoltageLevel(vl1.getId())
                .setBus(bus1.getId())
                .add()
                .newLeg2()
                .setR(rT3b)
                .setX(xT3b)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedU(120.0D)
                .setVoltageLevel(vl2.getId())
                .setBus(bus2.getId())
                .add()
                .newLeg3()
                .setR(rT3c)
                .setX(xT3c)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedU(30.)
                .setVoltageLevel(vl9.getId())
                .setBus(bus9.getId())
                .add()
                .add();

        //additional data
        // Machines :
        g1.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rG1)
                .add();
        g2.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rG2)
                .add();
        g3.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rG3)
                .add();
        m1.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM1)
                .add();
        m2.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM2)
                .add();
        q2.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rFeeder2)
                .add();

        g1.newExtension(GeneratorFortescueAdder.class)
                .withGrounded(true)
                .withXz(xG1 * coeffXoG1)
                .withRz(rG1 * coeffRoG1)
                .add();
        // extension used to get the default generator type
        g1.newExtension(GeneratorFortescueTypeAdder.class)
                .add();

        q2.newExtension(GeneratorFortescueAdder.class)
                .withGrounded(true)
                .withRz(rFeeder2 * coeffF2Ro)
                .withXz(xFeeder2 * coeffF2Xo)
                .add();
        // extension used to get the default generator type
        q2.newExtension(GeneratorFortescueTypeAdder.class)
                .add();

        // transformers :
        twt3.newExtension(ThreeWindingsTransformerFortescueAdder.class)
                .leg1().withConnectionType(WindingConnectionType.Y_GROUNDED)
                .leg1().withXz(twt3.getLeg1().getX())
                .leg1().withRz(twt3.getLeg1().getR())
                .leg2().withConnectionType(WindingConnectionType.Y)
                .leg2().withXz(twt3.getLeg2().getX())
                .leg2().withRz(twt3.getLeg2().getR())
                .leg3().withConnectionType(WindingConnectionType.DELTA)
                .leg3().withXz(twt3.getLeg3().getX())
                .leg3().withRz(twt3.getLeg3().getR())
                .add();

        twt4.newExtension(ThreeWindingsTransformerFortescueAdder.class)
                .leg1().withConnectionType(WindingConnectionType.Y)
                .leg1().withXz(twt4.getLeg1().getX())
                .leg1().withRz(twt4.getLeg1().getR())
                .leg1().withFreeFluxes(true)
                .leg2().withConnectionType(WindingConnectionType.Y_GROUNDED)
                .leg2().withXz(twt4.getLeg2().getX() * coeffXoT4)
                .leg2().withRz(twt4.getLeg2().getR() * coeffRoT4)
                .leg2().withFreeFluxes(true)
                .leg3().withConnectionType(WindingConnectionType.DELTA)
                .leg3().withXz(twt4.getLeg3().getX() * coeffXoT4)
                .leg3().withRz(twt4.getLeg3().getR() * coeffRoT4)
                .leg3().withFreeFluxes(true)
                .add();

        t5.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withXz(xT5)
                .withRz(rT5)
                .withConnectionType1(WindingConnectionType.Y)
                .withConnectionType2(WindingConnectionType.Y)
                .add();
        t6.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withXz(xT6)
                .withRz(rT6)
                .withConnectionType1(WindingConnectionType.Y)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();

        // Lines :
        l1.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL1 * rL1)
                .withXz(coeffXoL1 * xL1)
                .add();
        l2.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL2 * rL2)
                .withXz(coeffXoL2 * xL2)
                .add();
        l3.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL3 * rL3)
                .withXz(coeffXoL3 * xL3)
                .add();
        l4.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4)
                .withXz(coeffXoL4 * xL4)
                .add();
        l5.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL5 * rL5)
                .withXz(coeffXoL5 * xL5)
                .add();
        l6.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL6 * rL6)
                .withXz(coeffXoL6 * xL6)
                .add();

        return network;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    public static Network create6NodeIec9094Plus() {
        // This is the same grid as the 6 nodes but with additional nodes created
        // to split lines L2 (Bus 2 and Bus 4) and L4 (Bus 5 and Bus 6) into 2 pieces
        // to show feasability of short circuits in the middle of a line
        Network network = Network.create("ShortCircuit_IEC_3.1", "IEC_3.1");

        double bus1Vnom = 20.; // 20 kV
        double bus2Vnom = 0.4; // 400 V

        double ratedV2 = 0.41; // 410 V

        double kT1 = 0.975;
        double xT1 = 0.010312 * kT1;
        double rT1 = 0.002753 * kT1;
        double coeffRoT1 = 1.;
        double coeffXoT1 = 0.95;

        double kT2 = 0.975;
        double xT2 = 0.016100 * kT2;
        double rT2 = 0.004833 * kT2;
        double coeffRoT2 = 1.;
        double coeffXoT2 = 0.95;

        double xL2 = 0.000136;
        double rL2 = 0.000416;
        double coeffRoL2 = 4.23;
        double coeffXoL2 = 1.21;

        double xL1 = 0.000395;
        double rL1 = 0.000385;
        double coeffRoL1 = 3.7;
        double coeffXoL1 = 1.81;

        double xL3 = 0.001740;
        double rL3 = 0.005420;
        double coeffRoL3 = 3.;
        double coeffXoL3 = 4.46;

        double xL4 = 0.01485;
        double rL4 = 0.01850;
        double coeffRoL4 = 2.;
        double coeffXoL4 = 3.;

        // we model the feeder through a load: TODO : check if relevant for all IEC examples
        // Given the formula: |Zfeeder| = c.Un/(sqrt(3).Ik".rho²) at Vnom = 0.41 kV
        // Then Xfeeder = |Zfeeder| * 0.995 and Rfeeder = 0.1 * Xfeeder
        // Impedance to be applied at feeder : Zfeeder = 0.126115 +j1.26353 ohms at 20 kV (or Zfeeder = 0.053 +j0.531 mohms at 0.41kV as given in the example with rho)

        //using formula P(MW) = Re(Z) * |V|² / |Z|² and Q(MVA) = Im(Z) * |V|² / |Z|² we transform the feeder into a load
        double pEquivalentFeeder = 31.286;
        double qEquivalentFeeder = 313.451;

        Substation substation123 = network.newSubstation()
                .setId("S123")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation123.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(bus1Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus1Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(bus1Vnom).setAngle(0.);

        VoltageLevel vl2 = substation123.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(bus2Vnom).setAngle(0.);

        VoltageLevel vl3 = substation123.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(bus2Vnom).setAngle(0.);

        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(bus2Vnom).setAngle(0.);

        Substation substation25 = network.newSubstation()
                .setId("S25")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl25 = substation25.newVoltageLevel()
                .setId("VL_25")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus25 = vl25.getBusBreakerView().newBus()
                .setId("B25")
                .add();
        bus25.setV(bus2Vnom).setAngle(0.);

        Substation substation5 = network.newSubstation()
                .setId("S5")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl5 = substation5.newVoltageLevel()
                .setId("VL_5")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus5 = vl5.getBusBreakerView().newBus()
                .setId("B5")
                .add();
        bus5.setV(bus2Vnom).setAngle(0.);

        // we create a node in between to verify formula of short circuit in the middle of a line
        Substation substation55 = network.newSubstation()
                .setId("S55")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl55 = substation55.newVoltageLevel()
                .setId("VL_55")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus55 = vl55.getBusBreakerView().newBus()
                .setId("B55")
                .add();
        bus55.setV(bus2Vnom).setAngle(0.);

        Substation substation6 = network.newSubstation()
                .setId("S6")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl6 = substation6.newVoltageLevel()
                .setId("VL_6")
                .setNominalV(bus2Vnom)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * bus2Vnom)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus6 = vl6.getBusBreakerView().newBus()
                .setId("B6")
                .add();
        bus6.setV(bus2Vnom).setAngle(0.);

        vl1.newLoad()
                .setId("LOAD_FEEDER") // we try to model the feeder through a load that will be transformed into an impedance
                .setBus(bus1.getId())
                .setP0(pEquivalentFeeder)
                .setQ0(qEquivalentFeeder)
                .add();

        var t1 = substation123.newTwoWindingsTransformer()
                .setId("T1")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setRatedU2(ratedV2)
                .setR(rT1)
                .setX(xT1)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(630.)
                .add();
        var t2 = substation123.newTwoWindingsTransformer()
                .setId("T2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setRatedU2(ratedV2)
                .setR(rT2)
                .setX(xT2)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(400.)
                .add();

        Line l25 = network.newLine()
                .setId("L2_B2_B25")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl25.getId())
                .setBus2(bus25.getId())
                .setConnectableBus2(bus25.getId())
                .setR(rL2 * 0.3)
                .setX(xL2 * 0.3)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l54 = network.newLine()
                .setId("L2_B25_B4")
                .setVoltageLevel1(vl25.getId())
                .setBus1(bus25.getId())
                .setConnectableBus1(bus25.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(rL2 * 0.7)
                .setX(xL2 * 0.7)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l1 = network.newLine()
                .setId("L1_B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(rL1)
                .setX(xL1)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l3 = network.newLine()
                .setId("L3_B4_B5")
                .setVoltageLevel1(vl4.getId())
                .setBus1(bus4.getId())
                .setConnectableBus1(bus4.getId())
                .setVoltageLevel2(vl5.getId())
                .setBus2(bus5.getId())
                .setConnectableBus2(bus5.getId())
                .setR(rL3)
                .setX(xL3)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l455 = network.newLine()
                .setId("L455_B5_B55")
                .setVoltageLevel1(vl5.getId())
                .setBus1(bus5.getId())
                .setConnectableBus1(bus5.getId())
                .setVoltageLevel2(vl55.getId())
                .setBus2(bus55.getId())
                .setConnectableBus2(bus55.getId())
                .setR(rL4 * 0.3)
                .setX(xL4 * 0.3)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        Line l456 = network.newLine()
                .setId("L4_B55_B6")
                .setVoltageLevel1(vl55.getId())
                .setBus1(bus55.getId())
                .setConnectableBus1(bus55.getId())
                .setVoltageLevel2(vl6.getId())
                .setBus2(bus6.getId())
                .setConnectableBus2(bus6.getId())
                .setR(rL4 * 0.7)
                .setX(xL4 * 0.7)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        //additional data

        l1.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL1 * rL1)
                .withXz(coeffXoL1 * xL1)
                .add();
        l25.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL2 * rL2 * 0.3)
                .withXz(coeffXoL2 * xL2 * 0.3)
                .add();
        l54.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL2 * rL2 * 0.7)
                .withXz(coeffXoL2 * xL2 * 0.7)
                .add();
        l3.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL3 * rL3)
                .withXz(coeffXoL3 * xL3)
                .add();
        /*l4.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4)
                .withXz(coeffXoL4 * xL4)
                .add();*/
        l455.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4 * 0.3)
                .withXz(coeffXoL4 * xL4 * 0.3)
                .add();
        l456.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4 * 0.7)
                .withXz(coeffXoL4 * xL4 * 0.7)
                .add();

        t1.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(coeffRoT1 * rT1)
                .withXz(coeffXoT1 * xT1)
                .withConnectionType1(WindingConnectionType.DELTA)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();
        t2.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(coeffRoT2 * rT2)
                .withXz(coeffXoT2 * xT2)
                .withConnectionType1(WindingConnectionType.DELTA)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();

        return network;
    }

}

