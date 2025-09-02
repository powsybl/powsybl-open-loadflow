package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.*;

public final class WriteTests {
    private WriteTests(){
    }

    public static void printTests(Network network){
        for(Bus bus : network.getBusBreakerView().getBuses()){
            System.out.println(String.format("Bus %s = network.getBusBreakerView().getBus(\"%s\");", bus.getId(), bus.getId()));
            System.out.println(String.format("assertVoltageEquals(%f, %s);", bus.getV(),  bus.getId()));
            System.out.println(String.format("assertAngleEquals(%f, %s);", bus.getAngle(),  bus.getId()));
            System.out.println();
        }

        for(DcNode dcNode : network.getDcNodes()){
            if(!Double.isNaN(dcNode.getV())){
                System.out.println(String.format("DcNode %s = network.getDcNode(\"%s\");", dcNode.getId(), dcNode.getId()));
                System.out.println(String.format("assertVoltageEquals(%f, %s);", dcNode.getV(), dcNode.getId()));
                System.out.println();
            }
        }

        for(Generator generator : network.getGenerators()){
            System.out.println(String.format("Generator %s = network.getGenerator(\"%s\");", generator.getId(), generator.getId()));
            System.out.println(String.format("assertActivePowerEquals(%f, %s.getTerminal());", generator.getTerminal().getP(),  generator.getId()));
            System.out.println(String.format("assertReactivePowerEquals(%f, %s.getTerminal());", generator.getTerminal().getQ(),  generator.getId()));
            System.out.println();
        }

        for(Line line : network.getLines()){
            System.out.println(String.format("Line %s = network.getLine(\"%s\");", line.getId(), line.getId()));
            System.out.println(String.format("assertActivePowerEquals(%f, %s.getTerminal1());", line.getTerminal1().getP(),  line.getId()));
            System.out.println(String.format("assertReactivePowerEquals(%f, %s.getTerminal1());", line.getTerminal1().getQ(),  line.getId()));
            System.out.println(String.format("assertActivePowerEquals(%f, %s.getTerminal2());", line.getTerminal2().getP(),  line.getId()));
            System.out.println(String.format("assertReactivePowerEquals(%f, %s.getTerminal2());", line.getTerminal2().getQ(),  line.getId()));
            System.out.println();
        }

        for(DcLine dcLine : network.getDcLines()){
            System.out.println(String.format("DcLine %s = network.getDcLine(\"%s\");", dcLine.getId(), dcLine.getId()));
            if(!Double.isNaN(dcLine.getDcTerminal1().getP())) {
                System.out.println(String.format("assertDcPowerEquals(%f, %s.getDcTerminal1());", dcLine.getDcTerminal1().getP(), dcLine.getId()));
            }
            if(!Double.isNaN(dcLine.getDcTerminal2().getP())) {
                System.out.println(String.format("assertDcPowerEquals(%f, %s.getDcTerminal2());", dcLine.getDcTerminal2().getP(), dcLine.getId()));
            }
            System.out.println();
        }
    }
}
