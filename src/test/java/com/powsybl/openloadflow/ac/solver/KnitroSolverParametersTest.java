/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import org.checkerframework.checker.units.qual.N;
import org.junit.jupiter.api.Test;
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
        assertEquals(1, parametersKnitro.getGradientComputationMode());

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
        assertEquals(0.5, parametersKnitro.getMinRealisticVoltage());
        assertEquals(1.5, parametersKnitro.getMaxRealisticVoltage());
        // set other value
        parametersKnitro.setMinRealisticVoltage(0.95);
        parametersKnitro.setMaxRealisticVoltage(1.05);
        assertEquals(0.95, parametersKnitro.getMinRealisticVoltage());
        assertEquals(1.05, parametersKnitro.getMaxRealisticVoltage());
        // wrong values
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setMinRealisticVoltage(-Math.pow(10, -6)));
        assertEquals("Realistic voltage bounds must strictly greater then 0", e.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setMaxRealisticVoltage(-2.0));
        assertEquals("Realistic voltage bounds must strictly greater then 0", e2.getMessage());
        IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class, () -> parametersKnitro.setMaxRealisticVoltage(0.90));
        assertEquals("Realistic voltage upper bounds must greater then lower bounds", e3.getMessage());
    }

    @Test
    void testSetAndGetConvEpsPerEq() {
        /* OpenLoadFlowParameters */
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters();

        // check default value
        assertEquals(Math.pow(10,-6),olfParameters.getKnitroSolverConvEpsPerEq());

        // set other value
        olfParameters.setKnitroSolverConvEpsPerEq(Math.pow(10,-2));
        assertEquals(Math.pow(10,-2),olfParameters.getKnitroSolverConvEpsPerEq());

        // wrong values
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> olfParameters.setKnitroSolverConvEpsPerEq(Math.pow(-10,-3)));
        assertEquals("Invalid value for parameter knitroConvEpsPerEq: -0.001",e.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> olfParameters.setKnitroSolverConvEpsPerEq(0));
        assertEquals("Invalid value for parameter knitroConvEpsPerEq: 0.0",e2.getMessage());

        /* DefaultKnitroSolverStoppingCriteria */
        DefaultKnitroSolverStoppingCriteria defaultKnitroSolverStoppingCriteria = new DefaultKnitroSolverStoppingCriteria();

        // check default value
        assertEquals(Math.pow(10,-6),defaultKnitroSolverStoppingCriteria.getConvEpsPerEq());

        // set other value
        DefaultKnitroSolverStoppingCriteria newValue = new DefaultKnitroSolverStoppingCriteria(Math.pow(10,-2));
        assertEquals(Math.pow(10,-2),newValue.getConvEpsPerEq());

    }

    @Test
    void testToString() {
        KnitroSolverParameters parameters = new KnitroSolverParameters();
        assertEquals("KnitroSolverParameters(gradientComputationMode=1, " +
//                "convEpsPerEq=1.0E-6, " +
                "stoppingCriteria=DefaultKnitroSolverStoppingCriteria, " +
                "minRealisticVoltage=0.5, " +
                "maxRealisticVoltage=1.5, " +
                "alwaysUpdateNetwork=false" +
                ")", parameters.toString());
    }
}