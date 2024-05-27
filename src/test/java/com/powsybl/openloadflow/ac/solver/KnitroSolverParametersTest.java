/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class KnitroSolverParametersTest {

    @Test
    void testGradientComputationMode() {
        KnitroSolverParameters parametersKnitro = new KnitroSolverParameters();
        // default value
        assertEquals(2, parametersKnitro.getGradientComputationMode());

        // set other value
        parametersKnitro.setGradientComputationMode(3);
        assertEquals(3, parametersKnitro.getGradientComputationMode());

        // wrong values
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setGradientComputationMode(0));
        assertEquals("Knitro gradient computation mode must be between 1 and 3", e.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setGradientComputationMode(4));
        assertEquals("Knitro gradient computation mode must be between 1 and 3", e2.getMessage());
    }

    @Test
    void getAndSetVoltageBounds() {
        KnitroSolverParameters parametersKnitro = new KnitroSolverParameters();
        //TODO
        // default value
        assertEquals(0.5,parametersKnitro.getMinRealisticVoltage());
        assertEquals(1.5,parametersKnitro.getMaxRealisticVoltage());
        // set other value
        parametersKnitro.setMinRealisticVoltage(0.95);
        parametersKnitro.setMaxRealisticVoltage(1.05);
        assertEquals(0.95,parametersKnitro.getMinRealisticVoltage());
        assertEquals(1.05,parametersKnitro.getMaxRealisticVoltage());
        // wrong values
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setMinRealisticVoltage(-Math.pow(10,-6)));
        assertEquals("Realistic voltage bounds must strictly greater then 0", e.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setMaxRealisticVoltage(-2.0));
        assertEquals("Realistic voltage bounds must strictly greater then 0", e2.getMessage());
        IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setMaxRealisticVoltage(0.90));
        assertEquals("Realistic voltage upper bounds must greater then lower bounds", e3.getMessage());
    }

// TODO a transferer dans le fichier adéquat
//    @Test
//    void testSetAndGetConvEpsPerEq() {
//        /*
//         * Checks Knitro's parameter convEpsPerEq default value, setting it to other value and trying to set it to wrong value
//         */
//        KnitroSolverParameters parametersKnitro = new KnitroSolverParameters();
//
//        // check default value
//        assertEquals(parametersKnitro.getConvEpsPerEq(),Math.pow(10,-6));
//
//        // set other value
//        parametersKnitro.setConvEpsPerEq(Math.pow(10,-2));
//        assertEquals(Math.pow(10,-2),parametersKnitro.getConvEpsPerEq());
//
//        // wrong values
//        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setConvEpsPerEq(Math.pow(-10,-3)));
//        assertEquals("Knitro final relative stopping tolerance for the feasibility error must be strictly greater than 0",e.getMessage());
//        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setConvEpsPerEq(0));
//        assertEquals("Knitro final relative stopping tolerance for the feasibility error must be strictly greater than 0",e2.getMessage());
//    }



    @Test
    void testToString() {
        KnitroSolverParameters parameters = new KnitroSolverParameters();
        assertEquals("KnitroSolverParameters(gradientComputationMode=2, " +
//                "convEpsPerEq=1.0E-6, " +
                "stoppingCriteria=DefaultKnitroSolverStoppingCriteria, " +
                "minRealisticVoltage=0.5, " +
                "maxRealisticVoltage=1.5)"
                , parameters.toString());
    }

}
