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
class ShortCircuitBiphasedCommonSupportTest {

    private LoadFlowParameters loadFlowParameters;

    private MatrixFactory matrixFactory;

    @BeforeEach
    void setUp() {
        loadFlowParameters = LoadFlowParameters.load().setTwtSplitShuntAdmittance(true);
        matrixFactory = new DenseMatrixFactory();
    }

    @Test
    void shortCircuitIec31BiphasedCommonSupport() {

        Network network = ReferenceNetwork.create6NodesIec9094();

        ShortCircuitFault sc1 = new ShortCircuitFault("B2", "B3", "sc1", "B2", new ShortCircuitFaultImpedance(new Complex(0.)),
                ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT, ShortCircuitFault.ShortCircuitBiphasedType.C1_A2);
        List<ShortCircuitFault> faultList = List.of(sc1);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();

        assertEquals(28.85869102832315, getDefaultIk(scbEngine, sc1), 0.00001); // TODO : check manually result

    }

    @Test
    void shortCircuitIec31MultiBiphasedCommonSupport() {

        Network network = ReferenceNetwork.create6NodesIec9094();
        Complex zFaultToGround = new Complex(0.);
        ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFaultToGround);
        ShortCircuitFault sc1 = new ShortCircuitFault("B2", "B3", "sc1", "B2", scz, ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT, ShortCircuitFault.ShortCircuitBiphasedType.C1_B2);
        ShortCircuitFault sc2 = new ShortCircuitFault("B4", "B5", "sc2", "B4", scz, ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT, ShortCircuitFault.ShortCircuitBiphasedType.C1_C2);
        // TODO : a list that contains BIPHASED_COMMON_SUPPORT with the same nodes is not supported yet : FIX_ME
        List<ShortCircuitFault> faultList = List.of(sc1, sc2);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();

        assertEquals(28.85869102832315, getDefaultIk(scbEngine, sc1), 0.00001); // TODO : check manually result
        assertEquals(0., getDefaultIk(scbEngine, sc2), 0.00001); // TODO : check manually result

    }

    private static double getDefaultIk(ShortCircuitUnbalancedEngine scbEngine,
                                 ShortCircuitFault fault) {
        return scbEngine.resultsPerFault.get(fault).getDefaultIk().abs();
    }

}

