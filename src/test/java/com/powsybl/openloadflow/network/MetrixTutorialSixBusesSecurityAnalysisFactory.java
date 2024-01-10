package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.serde.test.MetrixTutorialSixBusesFactory;

import java.util.Set;

public class MetrixTutorialSixBusesSecurityAnalysisFactory extends AbstractLoadFlowNetworkFactory {

    public static Network createWithCurrentLimits() {
        Network network = MetrixTutorialSixBusesFactory.create();

        Set<String> lowLimitLines = Set.of("S_SO_1", "S_SO_2");
        network.getGenerator("SO_G2")
                .setTargetP(680.0);

        // change resistance
        for (Line line : network.getLines()) {
            line.setR(0.2);
            if (lowLimitLines.contains(line.getId())) {
                line.newCurrentLimits1().setPermanentLimit(350.0).add();
                line.newCurrentLimits2().setPermanentLimit(350.0).add();
            } else {
                line.newCurrentLimits1().setPermanentLimit(400.0).add();
                line.newCurrentLimits2().setPermanentLimit(400.0).add();
            }
        }

        network.getTwoWindingsTransformer("NE_NO_1").setR(0.1);
        return network;
    }

    public static Network createWithCurrentLimits2() {
        Network network = MetrixTutorialSixBusesFactory.create();
        network.getVoltageLevel("SE_poste").setLowVoltageLimit(375);

        Set<String> specialLimitLines = Set.of("S_SE_1", "S_SE_2", "SE_NE_1", "SE_NE_2");
        network.getGenerator("SO_G2")
                .setTargetP(120.0);
        network.getGenerator("N_G")
                .setVoltageRegulatorOn(false)
                .setTargetP(600.0);
        network.getGenerator("SE_G")
                .setVoltageRegulatorOn(false)
                .setTargetP(50.0);
        network.getLoad("SE_L1")
                .setP0(300.0);

        // change resistance
        for (Line line : network.getLines()) {
            if (specialLimitLines.contains(line.getId())) {
                line.setR(90.0);
                line.newCurrentLimits1().setPermanentLimit(2000.0).add();
                line.newCurrentLimits2().setPermanentLimit(2000.0).add();
            } else {
                line.setR(0.2);
                line.newCurrentLimits1().setPermanentLimit(1000.0).add();
                line.newCurrentLimits2().setPermanentLimit(1000.0).add();
            }
        }

        network.getTwoWindingsTransformer("NE_NO_1").setR(0.1);
        return network;
    }

    public static Network createWithActivePowerLimits() {
        Network network = MetrixTutorialSixBusesFactory.create();

        network.getGenerator("SO_G2")
                .setTargetP(680.0);
        Set<String> lowLimitLines = Set.of("S_SO_1", "S_SO_2");
        // change resistance and add limits to all lines
        for (Line line : network.getLines()) {
            line.setR(0.2);
            if (lowLimitLines.contains(line.getId())) {
                line.newActivePowerLimits1().setPermanentLimit(250.0).add();
                line.newActivePowerLimits2().setPermanentLimit(250.0).add();
            } else {
                line.newActivePowerLimits1().setPermanentLimit(300.0).add();
                line.newActivePowerLimits2().setPermanentLimit(300.0).add();
            }
        }
        network.getTwoWindingsTransformer("NE_NO_1")
                .setR(0.1);

        return network;
    }

    public static Network createWithApparentPowerLimits() {
        Network network = MetrixTutorialSixBusesFactory.create();

        network.getGenerator("SO_G2")
                .setTargetP(680.0);
        Set<String> lowLimitLines = Set.of("S_SO_1", "S_SO_2");

        for (Line line : network.getLines()) {
            line.setR(0.2);
            if (lowLimitLines.contains(line.getId())) {
                line.newApparentPowerLimits1().setPermanentLimit(250.0).add();
                line.newApparentPowerLimits2().setPermanentLimit(250.0).add();
            } else {
                line.newApparentPowerLimits1().setPermanentLimit(300.0).add();
                line.newApparentPowerLimits2().setPermanentLimit(300.0).add();
            }
        }
        network.getTwoWindingsTransformer("NE_NO_1")
                .setR(0.1);
        return network;
    }
}
