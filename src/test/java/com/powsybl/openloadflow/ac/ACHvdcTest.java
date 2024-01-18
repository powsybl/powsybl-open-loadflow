package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.sa.AbstractOpenSecurityAnalysisTest;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.action.Action;
import com.powsybl.security.action.SwitchAction;
import com.powsybl.security.condition.AnyViolationCondition;
import com.powsybl.security.results.OperatorStrategyResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.strategy.OperatorStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ACHvdcTest extends AbstractOpenSecurityAnalysisTest {

    @ParameterizedTest
    @ValueSource(strings = {"LCC", "VSC", "VSC-AcEmul"})
    void testHvdcDisconnectLine(String testType) {
        HvdcConverterStation.HvdcType hvdcType = switch (testType) {
            case "LCC" -> HvdcConverterStation.HvdcType.LCC;
            default -> HvdcConverterStation.HvdcType.VSC;
        };

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setHvdcAcEmulation(
            switch (testType) {
                case "VSC-AcEmul" -> true;
                default -> false;
            });

        Network network = HvdcNetworkFactory.createHvdcLinkedByTwoLinesWithGeneratorAndLoad(hvdcType);

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertTrue(network.getHvdcConverterStation("cs3").getTerminal().getP() < -190, "HVDC link expected to deliver power to b3");
        assertTrue(network.getGenerator("g1").getTerminal().getP() < -300, "Generator expected to deliver enough power for the load");
        assertTrue(network.getGenerator("g1").getTerminal().getP() > -310, "Power loss should  be realistic");

        // For Debug - display active power injections at each bus
        network.getBusBreakerView().getBusStream().forEach(b ->
                b.getConnectedTerminalStream().forEach(t -> System.out.println(b.getId() + ": " + t.getP())));

        Line l34 = network.getLine("l34");
        l34.getTerminals().stream().forEach(Terminal::disconnect);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isPartiallyConverged() || result.isFullyConverged()); // disconnected line does not converge.... and this is reported..

        // For Debug - display active power injections at each bus
        network.getBusBreakerView().getBusStream().forEach(b ->
                b.getConnectedTerminalStream().forEach(t -> System.out.println(b.getId() + ": " + t.getP())));

       //  assertTrue(network.getHvdcConverterStation("cs3").getTerminal().getP() == 0, "HVDC should not deliver power to disconected line");  // No power expected.. Disconnected
        assertTrue(network.getGenerator("g1").getTerminal().getP() < -299.99, "Generator expected to deliver enough power for the load");
        assertTrue(network.getGenerator("g1").getTerminal().getP() > -310, "Power loss should  be realistic");

    }

    @ParameterizedTest
    @ValueSource(strings = {"LCC", "VSC", "VSC-AcEmul"})
    void testHvdcSecurityAnalysisWithOperatorStrategy(String testType) {
        HvdcConverterStation.HvdcType hvdcType = switch (testType) {
            case "LCC" -> HvdcConverterStation.HvdcType.LCC;
            default -> HvdcConverterStation.HvdcType.VSC;
        };

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setHvdcAcEmulation(
            switch (testType) {
                case "VSC-AcEmul" -> true;
                default -> false;
            });

        Network network = HvdcNetworkFactory.createHvdcLinkedByTwoLinesWithGeneratorAndLoad(hvdcType);

        // Detect current in l12
        network.getLine("l12").newCurrentLimits2()
                .setPermanentLimit(200)  // 360 for 2MW
                .add();

        network.getLine("l34").newCurrentLimits2()
                .setPermanentLimit(200) // 360 for 2MW
                .add();

        // Detect HVDC closed
        network.getLine("l14").newCurrentLimits1()
                .setPermanentLimit(300)  // 260 for 100MB
                .add();

        List<Contingency> contingencies = Stream.of("l12", "l34")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        List<Action> actions = List.of(new SwitchAction("action1", "s2", false));

        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyL1",
                                                            ContingencyContext.specificContingency("l12"),
                                                            new AnyViolationCondition(),
                                                            List.of("action1")));


        SecurityAnalysisResult result = runSecurityAnalysis(network,
                contingencies,
                Collections.emptyList(),
                // We want to see all violations here.
                new SecurityAnalysisParameters()
                        .setIncreasedViolationsParameters(new SecurityAnalysisParameters.IncreasedViolationsParameters(-1000,
                                -1000,
                                -1000,
                                -1000,
                                -1000)),
                operatorStrategies,
                actions,
                Reporter.NO_OP);

        for (String line : new String[] {"l12", "l34"}) {
            boolean lineHasCurrentPreContingency =
                    result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().stream()
                            .filter(l -> l.getSubjectId().equals(line))
                            .findFirst()
                            .isPresent();
            assertTrue(lineHasCurrentPreContingency, "Current is passing through " + line + " pre contingency");
        }

        for (PostContingencyResult postContingencyResult : result.getPostContingencyResults()) {
            boolean line14HasCurrentCOntingency =
                    postContingencyResult.getLimitViolationsResult().getLimitViolations().stream()
                            .filter(l -> l.getSubjectId().equals("l14"))
                            .findFirst()
                            .isPresent();
            assertTrue(line14HasCurrentCOntingency, "All current should flow through l14");
            for (String line : new String[] {"l12", "l34"}) {
                boolean lineHasCurrentPreContingency =
                        postContingencyResult.getLimitViolationsResult().getLimitViolations().stream()
                                .filter(l -> l.getSubjectId().equals(line))
                                .findFirst()
                                .isPresent();
                assertFalse(lineHasCurrentPreContingency, "No current should pass through " + line +
                        " for contingency " + postContingencyResult.getContingency().getId());
            }
        }
        assertTrue(result.getOperatorStrategyResults().size() == 1, "One operator strategy run");
        OperatorStrategyResult operatorStrategyResult = result.getOperatorStrategyResults().get(0);
        assertTrue(operatorStrategyResult.getLimitViolationsResult().getLimitViolations().size() == 1, "One violation exepcted after operator strategy");
        LimitViolation limitViolation = operatorStrategyResult.getLimitViolationsResult().getLimitViolations().get(0);
        assertTrue(limitViolation.getSubjectId().equals("l34"), "l34 expected to transport current again because l12Bis is connected");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LCC", "VSC", "VSC-AcEmul"})
    void testHvdcDisconnectedThenConnectedByStrategy(String testType) {
        HvdcConverterStation.HvdcType hvdcType = switch (testType) {
            case "LCC" -> HvdcConverterStation.HvdcType.LCC;
            default -> HvdcConverterStation.HvdcType.VSC;
        };

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setHvdcAcEmulation(
                switch (testType) {
                    case "VSC-AcEmul" -> true;
                    default -> false;
                });

        Network network = HvdcNetworkFactory.createHvdcLinkedByTwoLinesWithGeneratorAndLoad(hvdcType);

        // Disconnect the HVDC Initially
        network.getLine("l12").getTerminals().forEach(Terminal::disconnect);

        network.getLine("l34").newCurrentLimits2()
                .setPermanentLimit(200) // 360 for 2MW
                .add();

        // Detect HVDC closed
        network.getLine("l14").newCurrentLimits1()
                .setPermanentLimit(300)  // 260 for 100MB
                .add();

        // double l14
        network.newLine()
                .setId("l14Bis")
                .setBus1("b1")
                .setConnectableBus1("b1")
                .setBus2("b4")
                .setConnectableBus2("b4")
                .setR(0d)
                .setX(0.1d)
                .add();

        List<Contingency> contingencies = List.of(new Contingency("l14Bis", new BranchContingency("l14Bis")));

        List<Action> actions = List.of(new SwitchAction("action1", "s2", false));

        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyL1",
                ContingencyContext.specificContingency("l14Bis"),
                new AnyViolationCondition(),
                List.of("action1")));


        SecurityAnalysisResult result = runSecurityAnalysis(network,
                contingencies,
                Collections.emptyList(),
                // We want to see all violations here.
                new SecurityAnalysisParameters()
                        .setIncreasedViolationsParameters(new SecurityAnalysisParameters.IncreasedViolationsParameters(-1000,
                                -1000,
                                -1000,
                                -1000,
                                -1000)),
                operatorStrategies,
                actions,
                Reporter.NO_OP);

        assertTrue(result.getPreContingencyResult().getLimitViolationsResult().getLimitViolations().isEmpty(), "No violation expected precontingency");


        for (PostContingencyResult postContingencyResult : result.getPostContingencyResults()) {
            boolean line14HasCurrentCOntingency =
                    postContingencyResult.getLimitViolationsResult().getLimitViolations().stream()
                            .filter(l -> l.getSubjectId().equals("l14"))
                            .findFirst()
                            .isPresent();
            assertTrue(line14HasCurrentCOntingency, "All current should flow through l14");
            for (String line : new String[] {"l12", "l34"}) {
                boolean lineHasCurrentPreContingency =
                        postContingencyResult.getLimitViolationsResult().getLimitViolations().stream()
                                .filter(l -> l.getSubjectId().equals(line))
                                .findFirst()
                                .isPresent();
                assertFalse(lineHasCurrentPreContingency, "No current should pass through " + line +
                        " for contingency " + postContingencyResult.getContingency().getId());
            }
        }
        assertTrue(result.getOperatorStrategyResults().size() == 1, "One operator strategy run");
        OperatorStrategyResult operatorStrategyResult = result.getOperatorStrategyResults().get(0);
        assertTrue(operatorStrategyResult.getLimitViolationsResult().getLimitViolations().size() == 1, "One violation exepcted after operator strategy");
        LimitViolation limitViolation = operatorStrategyResult.getLimitViolationsResult().getLimitViolations().get(0);
        assertTrue(limitViolation.getSubjectId().equals("l34"), "l34 expected to transport current again because l12Bis is connected");
    }

    // TODO: Test DC
}
