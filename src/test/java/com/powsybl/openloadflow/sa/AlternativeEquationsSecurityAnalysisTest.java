/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.PostContingencyResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC security analysis with alternative equations
 * ({@link OpenLoadFlowParameters#setAlternativeEquations(boolean)}): contingencies disabling branches and buses
 * (islanding detected by the connectivity analysis) and post-contingency PV/PQ switching preserve the matrix
 * structure, and results must be identical to the legacy modeling. In particular the power mismatch of an islanded
 * part is absorbed like in the legacy modeling (where islanded buses are structurally removed): islanded buses are
 * solved against trivial local equations and contribute zeros to the main component balances, so the slack
 * distribution only compensates within the main component.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
class AlternativeEquationsSecurityAnalysisTest extends AbstractOpenSecurityAnalysisTest {

    AlternativeEquationsSecurityAnalysisTest(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    /**
     * Eurostag network with an additional generator GEN2 behind a single transformer: losing NGEN2_NHV1 islands
     * VLGEN2 with GEN2 (100 MW generation loss to be absorbed by the main component).
     */
    private static Network createEurostagNetworkWithIslandableGenerator() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getGenerator("GEN").newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(280)
                .add();
        Substation p1 = network.getSubstation("P1");
        VoltageLevel vlgen2 = p1.newVoltageLevel()
                .setId("VLGEN2")
                .setNominalV(24.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vlgen2.getBusBreakerView().newBus()
                .setId("NGEN2")
                .add();
        vlgen2.newGenerator()
                .setId("GEN2")
                .setBus("NGEN2")
                .setConnectableBus("NGEN2")
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(24.5)
                .setTargetP(100)
                .add()
                .newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(100)
                .add();
        int zb380 = 380 * 380 / 100;
        p1.newTwoWindingsTransformer()
                .setId("NGEN2_NHV1")
                .setBus1("NGEN2")
                .setConnectableBus1("NGEN2")
                .setRatedU1(24.0)
                .setBus2("NHV1")
                .setConnectableBus2("NHV1")
                .setRatedU2(400.0)
                .setR(0.24 / 1800 * zb380)
                .setX(Math.sqrt(10 * 10 - 0.24 * 0.24) / 1800 * zb380)
                .add();
        network.getLoad("LOAD").setP0(699.838);
        return network;
    }

    private void checkSameResultsAsLegacyModeling(Supplier<Network> networkSupplier, List<Contingency> contingencies) {
        checkSameResultsAsLegacyModeling(networkSupplier, contingencies, new LoadFlowParameters());
    }

    private void checkSameResultsAsLegacyModeling(Supplier<Network> networkSupplier, List<Contingency> contingencies,
                                                  LoadFlowParameters lfParameters) {
        OpenLoadFlowParameters lfParametersExt = lfParameters.getExtension(OpenLoadFlowParameters.class);
        if (lfParametersExt == null) {
            lfParametersExt = OpenLoadFlowParameters.create(lfParameters);
        }
        lfParametersExt.setAlternativeEquations(false);

        Network legacyNetwork = networkSupplier.get();
        SecurityAnalysisResult legacyResult = runSecurityAnalysis(legacyNetwork, contingencies, createNetworkMonitors(legacyNetwork), lfParameters);

        lfParametersExt.setAlternativeEquations(true);
        Network network = networkSupplier.get();
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, createNetworkMonitors(network), lfParameters);

        assertEquals(legacyResult.getPreContingencyResult().getStatus(), result.getPreContingencyResult().getStatus());
        compareNetworkResults(legacyResult.getPreContingencyResult().getNetworkResult(), result.getPreContingencyResult().getNetworkResult());
        assertEquals(legacyResult.getPostContingencyResults().size(), result.getPostContingencyResults().size());
        for (int i = 0; i < legacyResult.getPostContingencyResults().size(); i++) {
            PostContingencyResult legacyPostContingencyResult = legacyResult.getPostContingencyResults().get(i);
            PostContingencyResult postContingencyResult = result.getPostContingencyResults().get(i);
            assertEquals(legacyPostContingencyResult.getContingency().getId(), postContingencyResult.getContingency().getId());
            assertEquals(legacyPostContingencyResult.getStatus(), postContingencyResult.getStatus());
            compareNetworkResults(legacyPostContingencyResult.getNetworkResult(), postContingencyResult.getNetworkResult());
        }
    }

    private static void compareNetworkResults(NetworkResult legacyNetworkResult, NetworkResult networkResult) {
        assertEquals(legacyNetworkResult.getBranchResults().size(), networkResult.getBranchResults().size());
        for (BranchResult legacyBranchResult : legacyNetworkResult.getBranchResults()) {
            BranchResult branchResult = networkResult.getBranchResult(legacyBranchResult.getBranchId());
            assertEquals(legacyBranchResult.getP1(), branchResult.getP1(), 1e-2, "p1 mismatch on branch " + legacyBranchResult.getBranchId());
            assertEquals(legacyBranchResult.getQ1(), branchResult.getQ1(), 1e-2, "q1 mismatch on branch " + legacyBranchResult.getBranchId());
            assertEquals(legacyBranchResult.getP2(), branchResult.getP2(), 1e-2, "p2 mismatch on branch " + legacyBranchResult.getBranchId());
            assertEquals(legacyBranchResult.getQ2(), branchResult.getQ2(), 1e-2, "q2 mismatch on branch " + legacyBranchResult.getBranchId());
        }
        assertEquals(legacyNetworkResult.getBusResults().size(), networkResult.getBusResults().size());
        for (int i = 0; i < legacyNetworkResult.getBusResults().size(); i++) {
            BusResult legacyBusResult = legacyNetworkResult.getBusResults().get(i);
            BusResult busResult = networkResult.getBusResults().get(i);
            assertEquals(legacyBusResult.getBusId(), busResult.getBusId());
            assertEquals(legacyBusResult.getV(), busResult.getV(), 1e-4, "v mismatch on bus " + legacyBusResult.getBusId());
            assertEquals(legacyBusResult.getAngle(), busResult.getAngle(), 1e-4, "angle mismatch on bus " + legacyBusResult.getBusId());
        }
    }

    @Test
    void localVoltageControlWithIslandingContingencyTest() {
        // NGEN2_NHV1 contingency islands VLGEN2 with GEN2: the 100 MW generation loss must be absorbed by the main
        // component slack distribution exactly like with the legacy modeling; NHV1_NHV2_1 contingency triggers
        // post-contingency PV/PQ switching on reactive limits
        checkSameResultsAsLegacyModeling(AlternativeEquationsSecurityAnalysisTest::createEurostagNetworkWithIslandableGenerator,
                List.of(Contingency.twoWindingsTransformer("NGEN2_NHV1"),
                        Contingency.line("NHV1_NHV2_1")));
    }

    @Test
    void remoteVoltageControlWithIslandingContingencyTest() {
        // tr1 contingency islands b1 with controller generator g1: the remaining controllers g2 and g3 keep
        // controlling b4 remotely with their reactive power distribution
        checkSameResultsAsLegacyModeling(() -> {
            Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
            network.getGenerator("g1").newMinMaxReactiveLimits()
                    .setMinQ(-50)
                    .setMaxQ(50)
                    .add();
            return network;
        }, List.of(Contingency.twoWindingsTransformer("tr1"),
                   Contingency.twoWindingsTransformer("tr2")));
    }

    @Test
    void hvdcAcEmulationContingenciesTest() {
        // buses connected to an AC emulated HVDC link are eligible to alternative equations: the AC emulation
        // active power terms and their saturation switching are term-level and stay structure preserving; the hvdc
        // and generator contingencies must give the same results as the legacy modeling
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        lfParameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .setHvdcAcEmulation(true);
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        checkSameResultsAsLegacyModeling(() -> {
            Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
            network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                    .withDroop(180)
                    .withP0(0.f)
                    .withEnabled(true)
                    .add();
            network.getGeneratorStream().forEach(generator -> generator.setMaxP(10));
            return network;
        },
            List.of(Contingency.line("l12"),
                    Contingency.line("l46"),
                    Contingency.generator("g1"),
                    Contingency.hvdcLine("hvdc34")),
            lfParameters);
    }

    @Test
    void islandedGenerationIsAbsorbedByMainComponentTest() {
        // explicit mismatch containment check: after islanding GEN2 (100 MW), the main component flows reflect the
        // distributed compensation of the lost generation, identically to the legacy modeling
        Network network = createEurostagNetworkWithIslandableGenerator();
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(lfParameters)
                .setAlternativeEquations(true);
        SecurityAnalysisResult result = runSecurityAnalysis(network, List.of(Contingency.twoWindingsTransformer("NGEN2_NHV1")),
                createNetworkMonitors(network), lfParameters);

        NetworkResult preContingencyNetworkResult = result.getPreContingencyResult().getNetworkResult();
        NetworkResult postContingencyNetworkResult = result.getPostContingencyResults().get(0).getNetworkResult();
        // before the contingency, GEN2 injects about 100 MW into NHV1 through NGEN2_NHV1
        assertEquals(100, preContingencyNetworkResult.getBranchResult("NGEN2_NHV1").getP1(), 1);
        // after the contingency, the lost injection is compensated by the main component generator: the flow coming
        // from NGEN increases by about the islanded generation (modulo losses), and is not transferred anywhere else
        double preGenFlow = preContingencyNetworkResult.getBranchResult("NGEN_NHV1").getP1();
        double postGenFlow = postContingencyNetworkResult.getBranchResult("NGEN_NHV1").getP1();
        assertEquals(100, postGenFlow - preGenFlow, 5);
        assertTrue(postContingencyNetworkResult.getBusResults().stream()
                .noneMatch(busResult -> busResult.getVoltageLevelId().equals("VLGEN2")
                        && !Double.isNaN(busResult.getV()) && Math.abs(busResult.getV() - 24.0) < 1e-3),
                "the trivial solution of the islanded bus must not be reported as a result");
    }
}
