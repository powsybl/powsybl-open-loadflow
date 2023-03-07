package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.xml.test.MetrixTutorialSixBusesFactory;

public class MetrixTutorialSixBusesSecurityAnalysisFactory extends AbstractLoadFlowNetworkFactory {

    public enum MetrixViolations {
        CURRENT,
        VOLTAGE,
        ACTIVE_POWER,
        APPARENT_POWER
    }

    public static Network create(MetrixViolations metrixViolation) {
        switch (metrixViolation) {
            case CURRENT:
            case ACTIVE_POWER:
            case APPARENT_POWER:
                return createWithCurrentActivePowerApparentPowerViolation(metrixViolation);
            case VOLTAGE:
                return createWithVoltageViolation();
            default:
                throw new PowsyblException("metrixViolation not defined");
        }
    }

    public static Network createWithCurrentActivePowerApparentPowerViolation(MetrixViolations metrixViolation) {
        Network network = MetrixTutorialSixBusesFactory.create();
        network.getGenerator("SO_G2").setTargetP(680.0);
        String[] idLines = {"NO_N_1", "NO_N_2", "S_SE_1", "S_SE_2", "S_SO_1", "S_SO_2", "SO_NO_1", "SO_NO_2", "NE_N_1", "NE_N_2", "SE_NE_1", "SE_NE_2"};
        for (String idLine : idLines) {
            network.getLine(idLine).setR(0.2);
            switch (metrixViolation) {
                case CURRENT:
                    network.getLine(idLine).newCurrentLimits1().setPermanentLimit(400.0).add();
                    network.getLine(idLine).newCurrentLimits2().setPermanentLimit(400.0).add();
                    break;
                case ACTIVE_POWER:
                    network.getLine(idLine).newActivePowerLimits1().setPermanentLimit(300.0).add();
                    network.getLine(idLine).newActivePowerLimits2().setPermanentLimit(300.0).add();
                    break;
                case APPARENT_POWER:
                    network.getLine(idLine).newApparentPowerLimits1().setPermanentLimit(300.0).add();
                    network.getLine(idLine).newApparentPowerLimits2().setPermanentLimit(300.0).add();
                    break;
            }
        }
        String[] idSpecialLines = {"S_SO_1", "S_SO_2"};
        for (String idLine : idSpecialLines) {
            network.getLine(idLine).setR(0.1);
            switch (metrixViolation) {
                case CURRENT:
                    network.getLine(idLine).newCurrentLimits1().setPermanentLimit(350.0).add();
                    network.getLine(idLine).newCurrentLimits2().setPermanentLimit(350.0).add();
                    break;
                case ACTIVE_POWER:
                    network.getLine(idLine).newActivePowerLimits1().setPermanentLimit(250.0).add();
                    network.getLine(idLine).newActivePowerLimits2().setPermanentLimit(250.0).add();
                    break;
                case APPARENT_POWER:
                    network.getLine(idLine).newApparentPowerLimits1().setPermanentLimit(250.0).add();
                    network.getLine(idLine).newApparentPowerLimits2().setPermanentLimit(250.0).add();
                    break;
            }
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
}
