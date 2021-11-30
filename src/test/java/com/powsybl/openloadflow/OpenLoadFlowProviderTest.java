/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.tools.PowsyblCoreVersion;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenLoadFlowProviderTest {

    @Test
    void test() {
        LoadFlowProvider loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        assertEquals("OpenLoadFlow", loadFlowProvider.getName());
        assertEquals(new PowsyblCoreVersion().getMavenProjectVersion(), loadFlowProvider.getVersion());
    }

    @Test
    void testDcParameters() {
        Network network = Mockito.mock(Network.class);
        DcLoadFlowParameters dcParameters = OpenLoadFlowProvider.createDcParameters(network, new DenseMatrixFactory(), new LoadFlowParameters(), new OpenLoadFlowParameters(), true);
        assertEquals("DcLoadFlowParameters(networkParameters=LfNetworkParameters(slackBusSelector=MostMeshedSlackBusSelector, generatorVoltageRemoteControl=false, minImpedance=false, twtSplitShuntAdmittance=false, breakers=false, plausibleActivePowerLimit=5000.0, addRatioToLinesWithDifferentNominalVoltageAtBothEnds=false, computeMainConnectedComponentOnly=true, countriesToBalance=[], distributedOnConformLoad=false, phaseControl=false, transformerVoltageControl=false, voltagePerReactivePowerControl=false, reactivePowerRemoteControl=false), equationSystemCreationParameters=DcEquationSystemCreationParameters(updateFlows=true, indexTerms=false, forcePhaseControlOffAndAddAngle1Var=true, useTransformerRatio=true), matrixFactory=DenseMatrixFactory, distributedSlack=true, balanceType=PROPORTIONAL_TO_GENERATION_P_MAX, setVToNan=true)",
                     dcParameters.toString());
    }

    @Test
    void testAcParameters() {
        Network network = Mockito.mock(Network.class);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), new LoadFlowParameters(), new OpenLoadFlowParameters(), false);
        assertEquals("AcLoadFlowParameters(networkParameters=LfNetworkParameters(slackBusSelector=MostMeshedSlackBusSelector, generatorVoltageRemoteControl=true, minImpedance=false, twtSplitShuntAdmittance=false, breakers=false, plausibleActivePowerLimit=5000.0, addRatioToLinesWithDifferentNominalVoltageAtBothEnds=false, computeMainConnectedComponentOnly=true, countriesToBalance=[], distributedOnConformLoad=false, phaseControl=false, transformerVoltageControl=false, voltagePerReactivePowerControl=false, reactivePowerRemoteControl=false), equationSystemCreationParameters=AcEquationSystemCreationParameters(forceA1Var=false, branchesWithCurrent=null), newtonRaphsonParameters=NewtonRaphsonParameters(maxIteration=30, voltageInitializer=UniformValueVoltageInitializer, stoppingCriteria=DefaultNewtonRaphsonStoppingCriteria), outerLoops=[DistributedSlackOuterLoop, ReactiveLimitsOuterLoop], matrixFactory=DenseMatrixFactory)",
                     acParameters.toString());
    }
}
