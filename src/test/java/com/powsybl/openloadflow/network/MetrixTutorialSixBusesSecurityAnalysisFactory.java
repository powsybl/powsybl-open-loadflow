package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.xml.test.MetrixTutorialSixBusesFactory;

public class MetrixTutorialSixBusesSecurityAnalysisFactory extends AbstractLoadFlowNetworkFactory {

    public static Network createWithCurrentViolation() {
        Network network = MetrixTutorialSixBusesFactory.create();
        network.getGenerator("SO_G2").setTargetP(680.0);
        String[] idLines = {"NO_N_1", "NO_N_2", "S_SE_1", "S_SE_2", "S_SO_1", "S_SO_2", "SO_NO_1", "SO_NO_2", "NE_N_1", "NE_N_2", "SE_NE_1", "SE_NE_2"};
        for (String idLine : idLines) {
            network.getLine(idLine).setR(0.2);
            network.getLine(idLine).newCurrentLimits1().setPermanentLimit(400.0).add();
            network.getLine(idLine).newCurrentLimits2().setPermanentLimit(400.0).add();
        }
        String[] idSpecialLines = {"S_SO_1", "S_SO_2"};
        for (String idLine : idSpecialLines) {
            network.getLine(idLine).setR(0.1);
            network.getLine(idLine).newCurrentLimits1().setPermanentLimit(350.0).add();
            network.getLine(idLine).newCurrentLimits2().setPermanentLimit(350.0).add();
        }
        TwoWindingsTransformer transfo = network.getTwoWindingsTransformer("NE_NO_1");
        transfo.setR(0.1);
        return network;
    }

    public static Network createWithVoltageViolation() {
        Network network = MetrixTutorialSixBusesFactory.create();
        network.getGenerator("SO_G2").setTargetP(120.0);
        network.getGenerator("N_G").setVoltageRegulatorOn(false).setTargetP(600.0);
        network.getGenerator("SE_G").setVoltageRegulatorOn(false).setTargetP(50.0);
        network.getLoad("SE_L1").setP0(1500);
        String[] idLines = {"NO_N_1", "NO_N_2", "S_SE_1", "S_SE_2", "S_SO_1", "S_SO_2", "SO_NO_1", "SO_NO_2", "NE_N_1", "NE_N_2", "SE_NE_1", "SE_NE_2"};

        for (String idLine : idLines) {
            network.getLine(idLine).setR(0.2);
            network.getLine(idLine).newCurrentLimits1().setPermanentLimit(1000.0).add();
            network.getLine(idLine).newCurrentLimits2().setPermanentLimit(1000.0).add();
        }
        String[] idSpecialLines = {"S_SE_1", "S_SE_2", "SE_NE_1", "SE_NE_2"};
        for (String idLine : idSpecialLines) {
            network.getLine(idLine).setR(90);
            network.getLine(idLine).newCurrentLimits1().setPermanentLimit(2000.0).add();
            network.getLine(idLine).newCurrentLimits2().setPermanentLimit(2000.0).add();
        }
        TwoWindingsTransformer transfo = network.getTwoWindingsTransformer("NE_NO_1");
        transfo.setR(0.1);

        return network;
    }

    public static Network createWithActivePowerViolation() {
        Network network = MetrixTutorialSixBusesFactory.create();
        network.getGenerator("SO_G2").setTargetP(680.0);
        String[] idLines = {"NO_N_1", "NO_N_2", "S_SE_1", "S_SE_2", "S_SO_1", "S_SO_2", "SO_NO_1", "SO_NO_2", "NE_N_1", "NE_N_2", "SE_NE_1", "SE_NE_2"};

        for (String idLine : idLines) {
            network.getLine(idLine).setR(0.2);
            network.getLine(idLine).newActivePowerLimits1().setPermanentLimit(300.0).add();
            network.getLine(idLine).newActivePowerLimits2().setPermanentLimit(300.0).add();
        }
        String[] idSpecialLines = {"S_SO_1", "S_SO_2"};
        for (String idLine : idSpecialLines) {
            network.getLine(idLine).setR(0.1);
            network.getLine(idLine).newActivePowerLimits1().setPermanentLimit(250.0).add();
            network.getLine(idLine).newActivePowerLimits2().setPermanentLimit(250.0).add();
        }
        TwoWindingsTransformer transfo = network.getTwoWindingsTransformer("NE_NO_1");
        transfo.setR(0.1);

        return network;
    }

    public static Network createWithApparentPowerViolation() {
        Network network = MetrixTutorialSixBusesFactory.create();
        network.getGenerator("SO_G2").setTargetP(680.0);
        String[] idLines = {"NO_N_1", "NO_N_2", "S_SE_1", "S_SE_2", "S_SO_1", "S_SO_2", "SO_NO_1", "SO_NO_2", "NE_N_1", "NE_N_2", "SE_NE_1", "SE_NE_2"};

        for (String idLine : idLines) {
            network.getLine(idLine).setR(0.2);
            network.getLine(idLine).newApparentPowerLimits1().setPermanentLimit(300.0).add();
            network.getLine(idLine).newApparentPowerLimits2().setPermanentLimit(300.0).add();
        }
        String[] idSpecialLines = {"S_SO_1", "S_SO_2"};
        for (String idLine : idSpecialLines) {
            network.getLine(idLine).setR(0.1);
            network.getLine(idLine).newApparentPowerLimits1().setPermanentLimit(250.0).add();
            network.getLine(idLine).newApparentPowerLimits2().setPermanentLimit(250.0).add();
        }
        TwoWindingsTransformer transfo = network.getTwoWindingsTransformer("NE_NO_1");
        transfo.setR(0.1);

        return network;
    }
}
