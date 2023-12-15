/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;

/**
 * <p>test network with lines connecting buses with different nominal voltages
 * , all other parameters identical (r, x, gsh, bsh, ...)</p>
 * <p>all lines should solve to the exact same solution</p>
 *
 * <pre>
 *
 *           225kV Bus     side1    side2      225kV Bus
 *      g1----[b225g]--------(l225-225)---------[b225l]---->l225 Load
 *   Generator   |
 *               |         side1    side2      220kV Bus
 *               ------------(l225-220)---------[b220A]---->l220A Load
 *               |
 *               |         side1    side2      230kV Bus
 *               ------------(l225-230)---------[b230A]---->l230A Load
 *               |
 *               |         side2    side1      220kV Bus
 *               ------------(l220-225)---------[b220B]---->l220B Load
 *               |
 *               |         side2    side1      230kV Bus
 *               ------------(l230-225)---------[b230B]---->l230B Load
 *
 * </pre>
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LinesWithDifferentNominalVoltagesNetworkFactory extends AbstractLoadFlowNetworkFactory {

    public static Network create() {
        Network network = Network.create("lines-different-nominal-voltage", "code");
        Bus b225g = createBus(network, "b225g", 225);
        Bus b225l = createBus(network, "b225l", 225);
        Bus b220A = createBus(network, "b220A", 220);
        Bus b230A = createBus(network, "b230A", 230);
        Bus b220B = createBus(network, "b220B", 220);
        Bus b230B = createBus(network, "b230B", 230);
        Generator g1 = b225g.getVoltageLevel()
                .newGenerator()
                .setId("g1")
                .setBus("b225g")
                .setConnectableBus("b225g")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(600)
                .setTargetP(500)
                .setTargetV(225)
                .setVoltageRegulatorOn(true)
                .add();
        g1.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .withDroop(4)
                .add();
        // loads
        final double pLoad = 100;
        final double qLoad = 40;
        createLoad(b225l, "l225", pLoad, qLoad);
        createLoad(b220A, "l220A", pLoad, qLoad);
        createLoad(b230A, "l230A", pLoad, qLoad);
        createLoad(b220B, "l220B", pLoad, qLoad);
        createLoad(b230B, "l230B", pLoad, qLoad);
        // lines
        final double r = 2.0;
        final double x = 30.0;
        final double halfGsh = 3e-5;
        final double halfBsh = 2e-5;
        createLine(network, b225g, b225l, "l225-225", x);
        createLine(network, b225g, b220A, "l225-220", x);
        createLine(network, b225g, b230A, "l225-230", x);
        createLine(network, b220B, b225g, "l220-225", x);
        createLine(network, b230B, b225g, "l230-225", x);
        network.getLines().forEach(l -> l.setR(r).setB1(halfBsh).setG1(halfGsh).setB2(halfBsh).setG2(halfGsh));
        return network;
    }

}
