package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;

public final class WriteTests {
    private WriteTests() {
    }

    public static void printTests(Network network) {
        for (Bus bus : network.getBusBreakerView().getBuses()) {
            System.out.printf("Bus %s = network.getBusBreakerView().getBus(\"%s\");%n", bus.getId(), bus.getId());
            System.out.printf("assertVoltageEquals(%f, %s);%n", bus.getV(), bus.getId());
            System.out.printf("assertAngleEquals(%f, %s);%n", bus.getAngle(), bus.getId());
            System.out.println();
        }

        for (DcNode dcNode : network.getDcNodes()) {
            if (!Double.isNaN(dcNode.getV())) {
                System.out.printf("DcNode %s = network.getDcNode(\"%s\");%n", dcNode.getId(), dcNode.getId());
                System.out.printf("assertVoltageEquals(%f, %s);%n", dcNode.getV(), dcNode.getId());
                System.out.println();
            }
        }

        for (Generator generator : network.getGenerators()) {
            System.out.printf("Generator %s = network.getGenerator(\"%s\");%n", generator.getId(), generator.getId());
            System.out.printf("assertActivePowerEquals(%f, %s.getTerminal());%n", generator.getTerminal().getP(), generator.getId());
            System.out.printf("assertReactivePowerEquals(%f, %s.getTerminal());%n", generator.getTerminal().getQ(), generator.getId());
            System.out.println();
        }

        for (VoltageSourceConverter converter : network.getVoltageSourceConverters()) {
            System.out.printf("VoltageSourceConverter %s = network.getVoltageSourceConverter(\"%s\");%n", converter.getId(), converter.getId());
            System.out.printf("assertActivePowerEquals(%f, %s.getTerminal1());%n", converter.getTerminal1().getP(), converter.getId());
            System.out.printf("assertReactivePowerEquals(%f, %s.getTerminal1());%n", converter.getTerminal1().getQ(), converter.getId());
            System.out.printf("assertDcPowerEquals(%f, %s.getDcTerminal1());%n", converter.getDcTerminal1().getP(), converter.getId());
            System.out.printf("assertDcPowerEquals(%f, %s.getDcTerminal2());%n", converter.getDcTerminal2().getP(), converter.getId());
            System.out.println();
        }

        for (Line line : network.getLines()) {
            System.out.printf("Line %s = network.getLine(\"%s\");%n", line.getId(), line.getId());
            System.out.printf("assertActivePowerEquals(%f, %s.getTerminal1());%n", line.getTerminal1().getP(), line.getId());
            System.out.printf("assertReactivePowerEquals(%f, %s.getTerminal1());%n", line.getTerminal1().getQ(), line.getId());
            System.out.printf("assertActivePowerEquals(%f, %s.getTerminal2());%n", line.getTerminal2().getP(), line.getId());
            System.out.printf("assertReactivePowerEquals(%f, %s.getTerminal2());%n", line.getTerminal2().getQ(), line.getId());
            System.out.println();
        }

        for (DcLine dcLine : network.getDcLines()) {
            System.out.printf("DcLine %s = network.getDcLine(\"%s\");%n", dcLine.getId(), dcLine.getId());
            if (!Double.isNaN(dcLine.getDcTerminal1().getP())) {
                System.out.printf("assertDcPowerEquals(%f, %s.getDcTerminal1());%n", dcLine.getDcTerminal1().getP(), dcLine.getId());
            }
            if (!Double.isNaN(dcLine.getDcTerminal2().getP())) {
                System.out.printf("assertDcPowerEquals(%f, %s.getDcTerminal2());%n", dcLine.getDcTerminal2().getP(), dcLine.getId());
            }
            System.out.println();
        }
    }

    public static void printValues(Network network) {
        for (Bus bus : network.getBusBreakerView().getBuses()) {
            System.out.printf("Bus %s%n", bus.getId());
            System.out.printf("V = %f%n", bus.getV());
            System.out.printf("phi = %f%n", bus.getAngle());
            System.out.println();
        }

        for (DcNode dcNode : network.getDcNodes()) {
            if (!Double.isNaN(dcNode.getV())) {
                System.out.printf("DcNode %s%n", dcNode.getId());
                System.out.printf("V = %f%n", dcNode.getV());
                System.out.println();
            }
        }

        for (Generator generator : network.getGenerators()) {
            System.out.printf("Generator %s%n", generator.getId());
            System.out.printf("P = %f%n", generator.getTerminal().getP());
            System.out.printf("Q = %f%n", generator.getTerminal().getQ());
            System.out.println();
        }

        for (VoltageSourceConverter converter : network.getVoltageSourceConverters()) {
            System.out.printf("Converter %s%n", converter.getId());
            System.out.printf("Pac = %f%n", converter.getTerminal1().getP());
            System.out.printf("Qac = %f%n", converter.getTerminal1().getQ());
            System.out.printf("Pdc1 = %f%n", converter.getDcTerminal1().getP());
            System.out.printf("Pdc2 = %f%n", converter.getDcTerminal2().getP());
            System.out.printf("Idc1 = %f%n", converter.getDcTerminal1().getI());
            System.out.printf("Idc2 = %f%n", converter.getDcTerminal2().getI());
            System.out.println();
        }

        for (Line line : network.getLines()) {
            System.out.printf("Line %s %n", line.getId());
            System.out.printf("P1 = %f%n", line.getTerminal1().getP());
            System.out.printf("Q1 = %f%n", line.getTerminal1().getQ());
            System.out.printf("P2 = %f%n", line.getTerminal2().getP());
            System.out.printf("Q2 = %f%n", line.getTerminal2().getQ());
            System.out.println();
        }

        for (DcLine dcLine : network.getDcLines()) {
            System.out.printf("DcLine %s%n", dcLine.getId());
            if (!Double.isNaN(dcLine.getDcTerminal1().getP())) {
                System.out.printf("P = %f%n", dcLine.getDcTerminal1().getP());
            }
            if (!Double.isNaN(dcLine.getDcTerminal2().getP())) {
                System.out.printf("P = %f%n", dcLine.getDcTerminal2().getP());
            }
            System.out.println();
        }
    }

    public static void printValues(LfNetwork network) {
        for (LfBus bus : network.getBuses()) {
            System.out.printf("Bus %s%n", bus.getId());
            System.out.printf("V = %f%n", bus.getV());
            System.out.printf("phi = %f%n", bus.getAngle());
            System.out.println();
        }

        for (LfDcNode dcNode : network.getDcNodes()) {
            if (!Double.isNaN(dcNode.getV())) {
                System.out.printf("DcNode %s%n", dcNode.getId());
                System.out.printf("V = %f%n", dcNode.getV());
                System.out.println();
            }
        }

        for (LfVoltageSourceConverter converter : network.getVoltageSourceConverters()) {
            System.out.printf("Converter %s%n", converter.getId());
            System.out.printf("Pac = %f%n", converter.getCalculatedPac().eval());
            System.out.printf("Qac = %f%n", converter.getCalculatedQac().eval());
            System.out.printf("Iac = %f%n", converter.getCalculatedIconv().eval());
            System.out.println();
        }

        for (LfBranch branch : network.getBranches()) {
            System.out.printf("Line %s %n", branch.getId());
            System.out.printf("P1 = %f%n", branch.getP1().eval());
            System.out.printf("Q1 = %f%n", branch.getQ1().eval());
            System.out.printf("P2 = %f%n", branch.getP2().eval());
            System.out.printf("Q2 = %f%n", branch.getQ2().eval());
            System.out.println();
        }

        for (LfDcLine dcLine : network.getDcLines()) {
            System.out.printf("DcLine %s%n", dcLine.getId());
            if (!Double.isNaN(dcLine.getI1().eval())) {
                System.out.printf("I = %f%n", dcLine.getI1().eval());
            }
            if (!Double.isNaN(dcLine.getI2().eval())) {
                System.out.printf("I = %f%n", dcLine.getI2().eval());
            }
            System.out.println();
        }
    }
}
