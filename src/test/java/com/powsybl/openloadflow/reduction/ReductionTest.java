/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.iidm.network.Network;
import com.powsybl.ieeecdf.converter.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.NetworkSlackBusSelector;
import com.powsybl.openloadflow.network.SlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.reduction.equations.ReductionEquationSystem;
import com.powsybl.openloadflow.reduction.equations.ReductionEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.AdmittanceMatrix;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.math.matrix.Matrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.usefultoys.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author JB Heyberger <jean-baptiste.heyberger at rte-france.com>
 */
class ReductionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReductionTest.class);

    private LoadFlowParameters parameters;

    private OpenLoadFlowProvider loadFlowProvider;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
    }

    /**
     * Check behaviour of the load flow for simple manipulations on eurostag example 1 network.
     * - line opening
     * - load change
     */
    @Test
    void computeCurrentInjectorTest() {
        Network network = IeeeCdfNetworkFactory.create14();

        List<String> voltageLevels = new ArrayList<>();

        OpenLoadFlowParameters parametersExt = loadFlowProvider.getParametersExt(parameters);
        SlackBusSelector slackBusSelector = new NetworkSlackBusSelector(network, parametersExt.getSlackBusSelector());
        ReductionParameters reductionParameters = new ReductionParameters(slackBusSelector, loadFlowProvider.getMatrixFactory(), voltageLevels);

        ReductionEngine re = new ReductionEngine(network, reductionParameters);
        LfNetwork lfNetwork = re.getNetworks().get(0);

        ReductionEquationSystemCreationParameters creationParameters = new ReductionEquationSystemCreationParameters(true, false);
        EquationSystem equationSystem = ReductionEquationSystem.create(lfNetwork, new VariableSet(), creationParameters);

        VoltageInitializer voltageInitializer = reductionParameters.getVoltageInitializer();

        AdmittanceMatrix a = new AdmittanceMatrix(equationSystem, reductionParameters.getMatrixFactory());
        Matrix a1 = a.getMatrix();
        Matrix mV = a.getVoltageVector(voltageInitializer);
        //System.out.println("===> v =");
        //mV.print(System.out);

        Matrix mI = mV.times(a1);
        //System.out.println("===> i =");
        //mI.print(System.out);

        double[] x = re.rowVectorToDouble(mI);

        assertEquals(2.1919, x[0], 0.01);
        assertEquals(0.1581, x[1], 0.01);
        assertEquals(0.1506, x[2], 0.01);
        assertEquals(-0.2990, x[3], 0.01);
    }

    @Test
    void ieee14ReductionTest() {

        Network network = IeeeCdfNetworkFactory.create14();

        List<String> voltageLevels = new ArrayList<>();
        voltageLevels.add("VL12");
        voltageLevels.add("VL13");

        OpenLoadFlowParameters parametersExt = loadFlowProvider.getParametersExt(parameters);
        SlackBusSelector slackBusSelector = new NetworkSlackBusSelector(network, parametersExt.getSlackBusSelector());
        ReductionParameters reductionParameters = new ReductionParameters(slackBusSelector, loadFlowProvider.getMatrixFactory(), voltageLevels);

        ReductionEngine re = new ReductionEngine(network, reductionParameters);

        re.run();

        ReductionEngine.ReductionResults results = re.getReductionResults();

        assertEquals(2, results.getBusNumToRealIeq().size(), 0);

    }
}
