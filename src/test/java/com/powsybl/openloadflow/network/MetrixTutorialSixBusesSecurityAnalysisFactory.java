package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Line;
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
        Network network = MetrixTutorialSixBusesFactory.create();

        String[] idSpecialLines;
        double specialR;
        double normalLineLimit;
        double specialLineLimit;

        switch (metrixViolation) {
            case CURRENT: {
                idSpecialLines = new String[]{"S_SO_1", "S_SO_2"};
                specialR = 0.2;
                normalLineLimit = 400.0;
                specialLineLimit = 350.0;
                network.getGenerator("SO_G2").setTargetP(680.0);
                break; }
            case ACTIVE_POWER:
            case APPARENT_POWER: {
                idSpecialLines = new String[]{"S_SO_1", "S_SO_2"};
                specialR = 0.2;
                normalLineLimit = 300.0;
                specialLineLimit = 250.0;
                network.getGenerator("SO_G2").setTargetP(680.0);
                break; }
            case VOLTAGE: {
                idSpecialLines = new String[]{"S_SE_1", "S_SE_2", "SE_NE_1", "SE_NE_2"};
                specialR = 90;
                normalLineLimit = 1000.0;
                specialLineLimit = 2000.0;
                network.getGenerator("SO_G2").setTargetP(120.0);
                network.getGenerator("N_G").setVoltageRegulatorOn(false).setTargetP(600.0);
                network.getGenerator("SE_G").setVoltageRegulatorOn(false).setTargetP(50.0);
                network.getLoad("SE_L1").setP0(1500);
                break; }
            default:
                throw new PowsyblException("metrixViolation not defined");
        }

        // change resistance and add limits to all lines
        for (Line line : network.getLines()) {
            line.setR(0.2);
            addLineLimits(line, normalLineLimit, metrixViolation);
        }

        // change resistance and add limits to some lines
        for (String idSpecialLine : idSpecialLines) {
            Line line = network.getLine(idSpecialLine);
            line.setR(specialR);
            addLineLimits(line, specialLineLimit, metrixViolation);
        }

        TwoWindingsTransformer transfo = network.getTwoWindingsTransformer("NE_NO_1");
        transfo.setR(0.1);

        return network;
    }

    private static void addLineLimits(Line line, double limit, MetrixViolations metrixViolation) {
        switch (metrixViolation) {
            case CURRENT:
            case VOLTAGE:
                line.newCurrentLimits1().setPermanentLimit(limit).add();
                line.newCurrentLimits2().setPermanentLimit(limit).add();
                break;
            case ACTIVE_POWER:
                line.newActivePowerLimits1().setPermanentLimit(limit).add();
                line.newActivePowerLimits2().setPermanentLimit(limit).add();
                break;
            case APPARENT_POWER:
                line.newApparentPowerLimits1().setPermanentLimit(limit).add();
                line.newApparentPowerLimits2().setPermanentLimit(limit).add();
                break;
        }
    }
}
