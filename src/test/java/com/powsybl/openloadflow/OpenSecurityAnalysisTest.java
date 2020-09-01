/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.tasks.AbstractTrippingTask;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.graph.NaiveGraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import com.powsybl.security.LimitViolationFilter;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class OpenSecurityAnalysisTest {

    private Network network;

    /**
     *                   G
     *              C    |
     * BBS1 -------[+]------- BBS2     VL1
     *         |        [+] B1
     *         |         |
     *     L1  |         | L2
     *         |         |
     *     B3 [+]       [+] B4
     * BBS3 -----------------          VL2
     *             |
     *             LD
     *
     * 6 buses
     * 6 branches
     *
     *            G
     *            |
     *      o--C--o
     *      |     |
     *      |     B2
     *      |     |
     *      |     o
     *      |     |
     *      L1    L2
     *      |     |
     *      o     o
     *      |     |
     *      B3    B4
     *      |     |
     *      ---o---
     *         |
     *         LD
     */
    static Network createNetwork() {
        Network network = Network.create("test", "test");
        Substation s = network.newSubstation()
                .setId("S")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        vl1.getNodeBreakerView().newBusbarSection()
                .setId("BBS1")
                .setNode(0)
                .add();
        vl1.getNodeBreakerView().newBusbarSection()
                .setId("BBS2")
                .setNode(1)
                .add();
        vl1.getNodeBreakerView().newBreaker()
                .setId("C")
                .setNode1(0)
                .setNode2(1)
                .add();
        vl1.getNodeBreakerView().newBreaker()
                .setId("B1")
                .setNode1(1)
                .setNode2(3)
                .add();
        vl1.getNodeBreakerView().newInternalConnection()
                .setNode1(1)
                .setNode2(4)
                .add();
        vl1.getNodeBreakerView().newInternalConnection()
                .setNode1(0)
                .setNode2(5)
                .add();
        vl1.newGenerator()
                .setId("G")
                .setNode(4)
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(398)
                .setTargetP(603.77)
                .setTargetQ(301.0)
                .add();

        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        vl2.getNodeBreakerView().newBusbarSection()
                .setId("BBS3")
                .setNode(0)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("B3")
                .setNode1(0)
                .setNode2(1)
                .add();
        vl2.getNodeBreakerView().newBreaker()
                .setId("B4")
                .setNode1(0)
                .setNode2(2)
                .add();
        vl2.getNodeBreakerView().newInternalConnection()
                .setNode1(0)
                .setNode2(3)
                .add();
        vl2.newLoad()
                .setId("LD")
                .setNode(3)
                .setP0(600.0)
                .setQ0(200.0)
                .add();

        network.newLine()
                .setId("L1")
                .setVoltageLevel1("VL1")
                .setNode1(5)
                .setVoltageLevel2("VL2")
                .setNode2(1)
                .setR(3.0)
                .setX(33.0)
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
                .setB2(386E-6 / 2)
                .add();

        network.newLine()
                .setId("L2")
                .setVoltageLevel1("VL1")
                .setNode1(3)
                .setVoltageLevel2("VL2")
                .setNode2(2)
                .setR(3.0)
                .setX(33.0)
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
                .setB2(386E-6 / 2)
                .add();

        network.getLine("L1").newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine("L1").newCurrentLimits2().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits1().setPermanentLimit(940.0).add();
        network.getLine("L2").newCurrentLimits2().setPermanentLimit(940.0).add();

        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
    }

    @Test
    void test() {
        SecurityAnalysisParameters saParameters = new SecurityAnalysisParameters();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = new OpenLoadFlowParameters()
                .setSlackBusSelector(new NameSlackBusSelector("VL1_1"));
        lfParameters.addExtension(OpenLoadFlowParameters.class, olfParameters);
        saParameters.setLoadFlowParameters(lfParameters);
        ContingenciesProvider contingenciesProvider = network -> Arrays.asList(
                new Contingency("L1", new BranchContingency("L1") {
                    @Override
                    public AbstractTrippingTask toTask() {
                        return new LfBranchTripping(id, voltageLevelId);
                    }
                }),
                new Contingency("L2", new BranchContingency("L2") {
                    @Override
                    public AbstractTrippingTask toTask() {
                        return new LfBranchTripping(id, voltageLevelId);
                    }
                }));

        OpenSecurityAnalysis securityAnalysis = new OpenSecurityAnalysis(network, new DefaultLimitViolationDetector(),
            new LimitViolationFilter(), new DenseMatrixFactory(), () -> new NaiveGraphDecrementalConnectivity<>(LfBus::getNum));
        SecurityAnalysisResult result = securityAnalysis.runSync(saParameters, contingenciesProvider);
        assertTrue(result.getPreContingencyResult().isComputationOk());
        assertEquals(0, result.getPreContingencyResult().getLimitViolations().size());
        assertEquals(2, result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().get(0).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(0).getLimitViolationsResult().getLimitViolations().size());
        assertTrue(result.getPostContingencyResults().get(1).getLimitViolationsResult().isComputationOk());
        assertEquals(2, result.getPostContingencyResults().get(1).getLimitViolationsResult().getLimitViolations().size());
    }
}
