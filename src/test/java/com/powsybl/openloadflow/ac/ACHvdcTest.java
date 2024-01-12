package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ACHvdcTest {

    @Test
    void testHvdcDisconnectLine() {
        Network network = HvdcNetworkFactory.createHvdcLinkedByTwoLinesWithGeneratorAndLoad();
        LoadFlowParameters parameters = new LoadFlowParameters();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertTrue(network.getHvdcConverterStation("cs3").getTerminal().getP() < -190, "HVDC link expected to deliver power to b3");
        assertTrue(network.getGenerator("g1").getTerminal().getP() < -300, "Generator expected to deliver enough power for the load");
        assertTrue(network.getGenerator("g1").getTerminal().getP() > -310, "Power loss should  be realistic");

        Line l34 = network.getLine("l34");
        l34.getTerminals().stream().forEach(Terminal::disconnect);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isPartiallyConverged()); // disconnected line does not converge.... and this is reported..

        // For Debug - display active power injections at each bus
        network.getBusBreakerView().getBusStream().forEach(b ->
                b.getConnectedTerminalStream().forEach(t -> System.out.println(b.getId() + ": " + t.getP())));

       //  assertTrue(network.getHvdcConverterStation("cs3").getTerminal().getP() == 0, "HVDC should not deliver power to disconected line");  // No power expected.. Disconnected
        assertTrue(network.getGenerator("g1").getTerminal().getP() < -300, "Generator expected to deliver enough power for the load");
        assertTrue(network.getGenerator("g1").getTerminal().getP() > -310, "Power loss should  be realistic");

    }
}
