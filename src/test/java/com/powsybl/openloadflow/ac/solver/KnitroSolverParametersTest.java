/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.loadflow.LoadFlowParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class KnitroSolverParametersTest {

    @Test
    void testGradientComputationMode() {
        KnitroSolverParameters parameters = new KnitroSolverParameters();
        // default value
        assertEquals(2, parameters.getGradientComputationMode());

        // set other value
        parameters.setGradientComputationMode(3);
        assertEquals(3, parameters.getGradientComputationMode());

        // wrong values
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parameters.setGradientComputationMode(0));
        assertEquals("Knitro gradient computation mode must be between 1 and 3", e.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> parameters.setGradientComputationMode(4));
        assertEquals("Knitro gradient computation mode must be between 1 and 3", e2.getMessage());
    }

    @Test
    void testConvEpsPerEq() {
        KnitroSolverParameters parameters = new KnitroSolverParameters();
        NewtonRaphsonParameters parametersNewtonRaphson = new NewtonRaphsonParameters();

        // default value
        assertEquals(parameters.getConvEpsPerEq(),NewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ);

        // set other value
        parameters.setConvEpsPerEq(Math.pow(10,-6));
        assertEquals(Math.pow(10,-6),parameters.getConvEpsPerEq());

        // wrong values
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parameters.setConvEpsPerEq(Math.pow(-10,-3)));
        assertEquals("Knitro final relative stopping tolerance for the feasibility error must be strictly greater than 0",e.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> parameters.setConvEpsPerEq(0));
        assertEquals("Knitro final relative stopping tolerance for the feasibility error must be strictly greater than 0",e2.getMessage());
    }

//    @Test
//    void testInitValues(){
//        KnitroSolverParameters parameters = new KnitroSolverParameters();
//
//        // default value
//        assertEquals(parameters.getVoltageInitMode(),LoadFlowParameters.DEFAULT_VOLTAGE_INIT_MODE);
//
//        // set other value
//        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
//        assertEquals(LoadFlowParameters.VoltageInitMode.DC_VALUES,parameters.getVoltageInitMode());
//    }


    @Test
    void testToString() {
        KnitroSolverParameters parameters = new KnitroSolverParameters();
        assertEquals("KnitroSolverParameters(gradientComputationMode=2; convEpsPerEq=1.0E-4; voltageInitMode=UNIFORM_VALUES)", parameters.toString());
    }

}
