/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class CountriesToBalanceTest {

    private Network network;
    private Generator g1;
    private Generator g2;

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        network = IeeeCdfNetworkFactory.create14();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        for (Generator g : network.getGenerators()) {
            g.setMaxP(2000);
        }
        g1 = network.getGenerator("B1-G");
        g2 = network.getGenerator("B2-G");
        g1.getTerminal().getVoltageLevel().getSubstation().orElseThrow().setCountry(Country.FR);
        g2.getTerminal().getVoltageLevel().getSubstation().orElseThrow().setCountry(Country.BE);
        for (Load l : network.getLoads()) {
            l.setP0(l.getP0() * 1.1);
            l.setQ0(l.getQ0() * 1.1);
        }
    }

    @Test
    void testAc() {
        var parameters = new LoadFlowParameters();

        loadFlowRunner.run(network, parameters);
        // by default g1 and g2 are balancing
        assertActivePowerEquals(-246.973, g1.getTerminal());
        assertActivePowerEquals(-54.573, g2.getTerminal());

        parameters.setCountriesToBalance(Set.of(Country.FR));
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(-261.547, g1.getTerminal()); // only g1 is balancing
        assertActivePowerEquals(-40.0, g2.getTerminal());

        parameters.setCountriesToBalance(Set.of(Country.BE));
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(-232.4, g1.getTerminal());
        assertActivePowerEquals(-67.458, g2.getTerminal()); // only g2 is balancing

        parameters.setCountriesToBalance(Set.of(Country.FR, Country.BE));
        loadFlowRunner.run(network, parameters);
        // both are balancing
        assertActivePowerEquals(-246.973, g1.getTerminal());
        assertActivePowerEquals(-54.573, g2.getTerminal());

        parameters.setCountriesToBalance(Set.of());
        loadFlowRunner.run(network, parameters);
        // empty also means both are balancing
        assertActivePowerEquals(-246.973, g1.getTerminal());
        assertActivePowerEquals(-54.573, g2.getTerminal());

        parameters.setDistributedSlack(false);
        loadFlowRunner.run(network, parameters);
        // no more balancing
        assertActivePowerEquals(-232.4, g1.getTerminal());
        assertActivePowerEquals(-40.0, g2.getTerminal());
    }

    @Test
    void testDc() {
        var parameters = new LoadFlowParameters()
                .setDc(true);

        loadFlowRunner.run(network, parameters);
        // by default g1 and g2 are balancing
        assertActivePowerEquals(-238.649, g1.getTerminal());
        assertActivePowerEquals(-46.25, g2.getTerminal());

        parameters.setCountriesToBalance(Set.of(Country.FR));
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(-244.9, g1.getTerminal()); // only g1 is balancing
        assertActivePowerEquals(-40.0, g2.getTerminal());

        parameters.setCountriesToBalance(Set.of(Country.BE));
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(-232.4, g1.getTerminal());
        assertActivePowerEquals(-52.5, g2.getTerminal()); // only g2 is balancing

        parameters.setCountriesToBalance(Set.of(Country.FR, Country.BE));
        loadFlowRunner.run(network, parameters);
        // both are balancing
        assertActivePowerEquals(-238.649, g1.getTerminal());
        assertActivePowerEquals(-46.25, g2.getTerminal());

        parameters.setCountriesToBalance(Set.of());
        loadFlowRunner.run(network, parameters);
        // empty also means both are balancing
        assertActivePowerEquals(-238.649, g1.getTerminal());
        assertActivePowerEquals(-46.25, g2.getTerminal());

        parameters.setDistributedSlack(false);
        loadFlowRunner.run(network, parameters);
        // no more balancing
        assertActivePowerEquals(-232.4, g1.getTerminal());
        assertActivePowerEquals(-40.0, g2.getTerminal());
    }
}
