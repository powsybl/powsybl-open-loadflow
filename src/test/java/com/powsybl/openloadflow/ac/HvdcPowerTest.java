package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HvdcPowerTest {

    @Test
    public void testHvdcPowerACEmulation() {
        Network network = HvdcNetworkFactory.createHvdcLinkedByTwoLinesWithGeneratorAndLoad(HvdcConverterStation.HvdcType.VSC,
                HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER);
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setHvdcAcEmulation(true);

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        // TODO: In AC Emulation HVDC seems to have a gain , not a loss...
        double bugInACEmulation = 5;
        System.out.println("Power in HVDC input: " + network.getHvdcConverterStation("cs2").getTerminal().getP());
        System.out.println("Power in HVDC output: " + network.getHvdcConverterStation("cs3").getTerminal().getP());
        System.out.println("Power provided by Generator: " + network.getGenerator("g1").getTerminal().getP());
        System.out.println("Power received by load: " + network.getLoad("l4").getTerminal().getP());

        double almostOne = 0.99d;

        assertTrue(Math.abs(network.getGenerator("g1").getTerminal().getP()) * almostOne >
                        Math.abs(network.getLoad("l4").getTerminal().getP()),
                "The generator should produce more energy than the energy received by the load");
    }
}
