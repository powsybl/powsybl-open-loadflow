/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.sc.util.ReferenceNetwork;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
class ShortCircuitBiphasedGroundTest {

    private LoadFlowParameters loadFlowParameters;

    private MatrixFactory matrixFactory;

    @BeforeEach
    void setUp() {
        loadFlowParameters = LoadFlowParameters.load().setTwtSplitShuntAdmittance(true);
        matrixFactory = new DenseMatrixFactory();
    }

    @Test
    void shortCircuit6NodesIec9094() {

        Network network = ReferenceNetwork.create6NodesIec9094();

        ShortCircuitFaultImpedance scZ1 = new ShortCircuitFaultImpedance(new Complex(0.));
        ShortCircuitFault sc1 = new ShortCircuitFault("B3", "sc1", "B3", scZ1, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);

        ShortCircuitFaultImpedance scZ2 = new ShortCircuitFaultImpedance(new Complex(0.), new Complex(0.0001, 0.0002), new Complex(0.0003, 0.0004));
        ShortCircuitFault sc2 = new ShortCircuitFault("B3", "sc2", "B3", scZ2, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);

        ShortCircuitFaultImpedance scZ3 = new ShortCircuitFaultImpedance(new Complex(0.000007, 0.00005), new Complex(0.0001, 0.0002), new Complex(0.0003, 0.0004));
        ShortCircuitFault sc3 = new ShortCircuitFault("B3", "sc3", "B3", scZ3, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);

        List<ShortCircuitFault> faultList = List.of(sc1, sc2, sc3);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, false, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();

        assertEquals(36.83479069716216, getIk(scbEngine, sc1), 1e-5);
        assertEquals(36.83269305365278, getIk(scbEngine, sc2), 1e-5);
        assertEquals(36.831803523930354, getIk(scbEngine, sc3), 1e-5);

    }

    private static double getIk(ShortCircuitUnbalancedEngine scbEngine, ShortCircuitFault fault) {
        return scbEngine.resultsPerFault.get(fault).getIk().abs();
    }
}

