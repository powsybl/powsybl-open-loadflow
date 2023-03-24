package com.powsybl.openloadflow.ac.nr;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.Extensions.AsymBus;
import com.powsybl.openloadflow.network.Extensions.AsymLine;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class AbcResults {

    // busses
    public Map<LfBus, Double> busPhaseA = new HashMap<>();
    public Map<LfBus, Double> busPhaseB = new HashMap<>();
    public Map<LfBus, Double> busPhaseC = new HashMap<>();

    public Map<LfBus, Double> busVa = new HashMap<>();
    public Map<LfBus, Double> busVb = new HashMap<>();
    public Map<LfBus, Double> busVc = new HashMap<>();

    public Map<LfBus, Double> busVax = new HashMap<>();
    public Map<LfBus, Double> busVbx = new HashMap<>();
    public Map<LfBus, Double> busVcx = new HashMap<>();

    public Map<LfBus, Double> busVay = new HashMap<>();
    public Map<LfBus, Double> busVby = new HashMap<>();
    public Map<LfBus, Double> busVcy = new HashMap<>();

    // branches
    public Map<LfBranch, DenseMatrix> branchI1abc = new HashMap<>();
    public Map<LfBranch, DenseMatrix> branchI1fort = new HashMap<>();
    public Map<LfBranch, DenseMatrix> branchI1fortCalc = new HashMap<>();

    public Map<LfBranch, DenseMatrix> branchI2abc = new HashMap<>();
    public Map<LfBranch, DenseMatrix> branchI2fort = new HashMap<>();
    public Map<LfBranch, DenseMatrix> branchI2fortCalc = new HashMap<>();

    public Map<LfBranch, DenseMatrix> branchS1abc = new HashMap<>();
    public Map<LfBranch, DenseMatrix> branchS1fort = new HashMap<>();

    public Map<LfBranch, DenseMatrix> branchS2abc = new HashMap<>();
    public Map<LfBranch, DenseMatrix> branchS2fort = new HashMap<>();

    // sums
    public Map<LfBus, DenseMatrix> sumIfort = new HashMap<>();
    public Map<LfBus, DenseMatrix> sumIabc = new HashMap<>();

    AbcResults() {
    }

    public void fillAbcBussesResults(LfNetwork network) {
        for (LfBus bus : network.getBuses()) {
            addBusAbcResult(bus);
        }
    }

    public void fillAbcBranchesResults(LfNetwork network) {
        for (LfBranch branch : network.getBranches()) {
            addBranchAbcResult(branch);
        }
    }

    public void addBusAbcResult(LfBus bus) {
        double v = bus.getV();
        double ph = bus.getAngle();
        double vHomo = 0;
        double phHomo = 0;
        double vInv = 0;
        double phInv = 0;
        AsymBus asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus != null) {
            phHomo = asymBus.getAngleHompolar();
            vHomo = asymBus.getvHomopolar();
            phInv = asymBus.getAngleInverse();
            vInv = asymBus.getvInverse();
            System.out.println("NEW>>>>>>>> " + bus.getId() + " H = " + vHomo + " (" + asymBus.getAngleHompolar());
            System.out.println("NEW>>>>>>>> " + bus.getId() + " D = " + v + " (" + bus.getAngle());
            System.out.println("NEW>>>>>>>> " + bus.getId() + " I = " + vInv + " (" + asymBus.getAngleInverse());
        }

        // [G1]   [ 1  1  1 ]   [Gh]
        // [G2] = [ 1  a²  a] * [Gd]
        // [G3]   [ 1  a  a²]   [Gi]
        MatrixFactory matrixFactory = new DenseMatrixFactory();

        Pair<Double, Double> directComponent = Fortescue.getCartesianFromPolar(v, ph);
        Pair<Double, Double> homopolarComponent = Fortescue.getCartesianFromPolar(vHomo, phHomo);
        Pair<Double, Double> inversComponent = Fortescue.getCartesianFromPolar(vInv, phInv);

        Matrix mGfortescue = matrixFactory.create(6, 1, 6);
        mGfortescue.add(0, 0, homopolarComponent.getKey());
        mGfortescue.add(1, 0, homopolarComponent.getValue());
        mGfortescue.add(2, 0, directComponent.getKey());
        mGfortescue.add(3, 0, directComponent.getValue());
        mGfortescue.add(4, 0, inversComponent.getKey());
        mGfortescue.add(5, 0, inversComponent.getValue());

        DenseMatrix mGphase = Fortescue.getFortescueMatrix().times(mGfortescue).toDense();

        // used for computing currents
        busVax.put(bus, mGphase.get(0, 0));
        busVay.put(bus, mGphase.get(1, 0));
        busVbx.put(bus, mGphase.get(2, 0));
        busVby.put(bus, mGphase.get(3, 0));
        busVcx.put(bus, mGphase.get(4, 0));
        busVcy.put(bus, mGphase.get(5, 0));

        Pair<Double, Double> phase1 = Fortescue.getPolarFromCartesian(mGphase.get(0, 0), mGphase.get(1, 0));
        Pair<Double, Double> phase2 = Fortescue.getPolarFromCartesian(mGphase.get(2, 0), mGphase.get(3, 0));
        Pair<Double, Double> phase3 = Fortescue.getPolarFromCartesian(mGphase.get(4, 0), mGphase.get(5, 0));

        busVa.put(bus, phase1.getKey());
        busPhaseA.put(bus, phase1.getValue());
        busVb.put(bus, phase2.getKey());
        busPhaseB.put(bus, phase2.getValue());
        busVc.put(bus, phase3.getKey());
        busPhaseC.put(bus, phase3.getValue());

        System.out.println("---------- BUS " + bus.getId());
        printVector(mGfortescue.toDense(), "mVfortescue " + bus.getId() + " = ", false);

        System.out.println("NEW>>>>>>>> " + bus.getId() + " PHASE A = " + phase1.getKey() + " (" + phase1.getValue());
        System.out.println("NEW>>>>>>>> " + bus.getId() + " PHASE B = " + phase2.getKey() + " (" + phase2.getValue());
        System.out.println("NEW>>>>>>>> " + bus.getId() + " PHASE C = " + phase3.getKey() + " (" + phase3.getValue());
    }

    public void addBranchAbcResult(LfBranch branch) {
        // make the difference if it is a coupled line or not
        // we suggest to compute currents using ABC and fortescue coordinates and check if this is consistent

        // check the existence of an extension
        AsymLine asymLine = (AsymLine) branch.getProperty(AsymLine.PROPERTY_ASYMMETRICAL);
        boolean disconnectionAsymmetry = false;
        if (asymLine != null) {
            disconnectionAsymmetry = asymLine.isDisconnectionAsymmetryDetected();
            //System.out.println("Disymmetry detected  for branch : " + branch.getId() + " = " + disconnectionAsymmetry);
        } else {
            //System.out.println("No disymmetry detected  for branch : " + branch.getId() + " with no asym extension");
        }

        LfBus bus1 = branch.getBus1();
        double v1 = 0;
        double ph1 = 0;
        double v1Homo = 0;
        double ph1Homo = 0;
        double v1Inv = 0;
        double ph1Inv = 0;
        AsymBus asymBus1 = null;
        if (bus1 != null) {
            v1 = bus1.getV();
            ph1 = bus1.getAngle();
            asymBus1 = (AsymBus) bus1.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        }

        if (asymBus1 != null) {
            ph1Homo = asymBus1.getAngleHompolar();
            v1Homo = asymBus1.getvHomopolar();
            ph1Inv = asymBus1.getAngleInverse();
            v1Inv = asymBus1.getvInverse();
        }

        LfBus bus2 = branch.getBus2();
        double v2 = 0;
        double ph2 = 0;
        double v2Homo = 0;
        double ph2Homo = 0;
        double v2Inv = 0;
        double ph2Inv = 0;

        AsymBus asymBus2 = null;
        if (bus2 != null) {
            v2 = bus2.getV();
            ph2 = bus2.getAngle();
            asymBus2 = (AsymBus) bus2.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        }
        if (asymBus2 != null) {

            ph2Homo = asymBus2.getAngleHompolar();
            v2Homo = asymBus2.getvHomopolar();
            ph2Inv = asymBus2.getAngleInverse();
            v2Inv = asymBus2.getvInverse();
        }

        PiModel piModel = branch.getPiModel();
        double r = piModel.getR();
        double x = piModel.getX();
        double g12 = 0.;
        double b12 = 0.;
        if (Math.abs(x) > 0.000001 || Math.abs(r) > 0.000001) {
            g12 = r / (r * r + x * x);
            b12 = -x / (r * r + x * x);
        }

        double g21 = g12;
        double b21 = b12;

        System.out.println("g12 = " + g12);
        System.out.println("b12 = " + b12);

        double g1 = piModel.getG1();
        double g2 = piModel.getG2();
        double b1 = piModel.getB1();
        double b2 = piModel.getB2();
        // TODO : integrate complex rho in the admittance matrix when transformers will be implemented

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        DenseMatrix mVabc = matrixFactory.create(12, 1, 12).toDense();
        if (bus1 != null && bus2 != null) {
            mVabc.add(0, 0, busVax.get(bus1));
            mVabc.add(1, 0, busVay.get(bus1));
            mVabc.add(2, 0, busVbx.get(bus1));
            mVabc.add(3, 0, busVby.get(bus1));
            mVabc.add(4, 0, busVcx.get(bus1));
            mVabc.add(5, 0, busVcy.get(bus1));
            mVabc.add(6, 0, busVax.get(bus2));
            mVabc.add(7, 0, busVay.get(bus2));
            mVabc.add(8, 0, busVbx.get(bus2));
            mVabc.add(9, 0, busVby.get(bus2));
            mVabc.add(10, 0, busVcx.get(bus2));
            mVabc.add(11, 0, busVcy.get(bus2));
        }

        if (!disconnectionAsymmetry) {
            // no assymmetry is detected with this line, we handle the equations as decoupled
            // In the case of a line, we build the admittance matrix which is supposed to be the same for each phase or each sequence
            DenseMatrix mY = new DenseMatrix(4, 4);
            mY.add(0, 0, g1 + g12);
            mY.add(1, 0, b1 + b12);
            mY.add(2, 0, -g21);
            mY.add(3, 0, -b21);

            mY.add(0, 1, -b1 - b12);
            mY.add(1, 1, g1 + g12);
            mY.add(2, 1, b21);
            mY.add(3, 1, -g21);

            mY.add(0, 2, -g12);
            mY.add(1, 2, -b12);
            mY.add(2, 2, g2 + g21);
            mY.add(3, 2, b2 + b21);

            mY.add(0, 3, b12);
            mY.add(1, 3, -g12);
            mY.add(2, 3, -b2 - b21);
            mY.add(3, 3, g2 + g21);

            DenseMatrix mVh = new DenseMatrix(4, 1);
            mVh.add(0, 0, Fortescue.getCartesianFromPolar(v1Homo, ph1Homo).getKey());
            mVh.add(1, 0, Fortescue.getCartesianFromPolar(v1Homo, ph1Homo).getValue());
            mVh.add(2, 0, Fortescue.getCartesianFromPolar(v2Homo, ph2Homo).getKey());
            mVh.add(3, 0, Fortescue.getCartesianFromPolar(v2Homo, ph2Homo).getValue());

            DenseMatrix mVd = new DenseMatrix(4, 1);
            mVd.add(0, 0, Fortescue.getCartesianFromPolar(v1, ph1).getKey());
            mVd.add(1, 0, Fortescue.getCartesianFromPolar(v1, ph1).getValue());
            mVd.add(2, 0, Fortescue.getCartesianFromPolar(v2, ph2).getKey());
            mVd.add(3, 0, Fortescue.getCartesianFromPolar(v2, ph2).getValue());

            DenseMatrix mVi = new DenseMatrix(4, 1);
            mVi.add(0, 0, Fortescue.getCartesianFromPolar(v1Inv, ph1Inv).getKey());
            mVi.add(1, 0, Fortescue.getCartesianFromPolar(v1Inv, ph1Inv).getValue());
            mVi.add(2, 0, Fortescue.getCartesianFromPolar(v2Inv, ph2Inv).getKey());
            mVi.add(3, 0, Fortescue.getCartesianFromPolar(v2Inv, ph2Inv).getValue());

            DenseMatrix mIh = mY.times(mVh);
            DenseMatrix mId = mY.times(mVd);
            DenseMatrix mIi = mY.times(mVi);

            DenseMatrix mI1fortescue = matrixFactory.create(6, 1, 6).toDense();
            mI1fortescue.add(0, 0, mIh.get(0, 0));
            mI1fortescue.add(1, 0, mIh.get(1, 0));
            mI1fortescue.add(2, 0, mId.get(0, 0));
            mI1fortescue.add(3, 0, mId.get(1, 0));
            mI1fortescue.add(4, 0, mIi.get(0, 0));
            mI1fortescue.add(5, 0, mIi.get(1, 0));

            DenseMatrix mI2fortescue = matrixFactory.create(6, 1, 6).toDense();
            mI2fortescue.add(0, 0, mIh.get(2, 0));
            mI2fortescue.add(1, 0, mIh.get(3, 0));
            mI2fortescue.add(2, 0, mId.get(2, 0));
            mI2fortescue.add(3, 0, mId.get(3, 0));
            mI2fortescue.add(4, 0, mIi.get(2, 0));
            mI2fortescue.add(5, 0, mIi.get(3, 0));

            DenseMatrix mI1abcCalculated = Fortescue.getFortescueMatrix().times(mI1fortescue);
            DenseMatrix mI2abcCalculated = Fortescue.getFortescueMatrix().times(mI2fortescue);

            DenseMatrix mS1Fortescue = matrixFactory.create(6, 1, 6).toDense();
            mS1Fortescue.add(0, 0, mI1fortescue.get(0, 0) * mVh.get(0, 0) + mI1fortescue.get(1, 0) * mVh.get(1, 0));
            mS1Fortescue.add(1, 0, -mI1fortescue.get(1, 0) * mVh.get(0, 0) + mI1fortescue.get(0, 0) * mVh.get(1, 0));
            mS1Fortescue.add(2, 0, mI1fortescue.get(2, 0) * mVd.get(0, 0) + mI1fortescue.get(3, 0) * mVd.get(1, 0));
            mS1Fortescue.add(3, 0, -mI1fortescue.get(3, 0) * mVd.get(0, 0) + mI1fortescue.get(2, 0) * mVd.get(1, 0));
            mS1Fortescue.add(4, 0, mI1fortescue.get(4, 0) * mVi.get(0, 0) + mI1fortescue.get(5, 0) * mVi.get(1, 0));
            mS1Fortescue.add(5, 0, -mI1fortescue.get(5, 0) * mVi.get(0, 0) + mI1fortescue.get(4, 0) * mVi.get(1, 0));

            DenseMatrix mS2Fortescue = matrixFactory.create(6, 1, 6).toDense();
            mS2Fortescue.add(0, 0, mI2fortescue.get(0, 0) * mVh.get(2, 0) + mI2fortescue.get(1, 0) * mVh.get(3, 0));
            mS2Fortescue.add(1, 0, -mI2fortescue.get(1, 0) * mVh.get(2, 0) + mI2fortescue.get(0, 0) * mVh.get(3, 0));
            mS2Fortescue.add(2, 0, mI2fortescue.get(2, 0) * mVd.get(2, 0) + mI2fortescue.get(3, 0) * mVd.get(3, 0));
            mS2Fortescue.add(3, 0, -mI2fortescue.get(3, 0) * mVd.get(2, 0) + mI2fortescue.get(2, 0) * mVd.get(3, 0));
            mS2Fortescue.add(4, 0, mI2fortescue.get(4, 0) * mVi.get(2, 0) + mI2fortescue.get(5, 0) * mVi.get(3, 0));
            mS2Fortescue.add(5, 0, -mI2fortescue.get(5, 0) * mVi.get(2, 0) + mI2fortescue.get(4, 0) * mVi.get(3, 0));

            //System.out.println("---------- BRANCH " + branch.getId());
            /*System.out.println("V1dx = " + mVd.get(0, 0));
            System.out.println("V1dy = " + mVd.get(1, 0));
            System.out.println("I1dx = " + mI1fortescue.get(2, 0));
            System.out.println("I1dy = " + mI1fortescue.get(3, 0));
            System.out.println("V2dx = " + mVd.get(2, 0));
            System.out.println("V2dy = " + mVd.get(3, 0));
            System.out.println("I2dx = " + mI2fortescue.get(2, 0));
            System.out.println("I2dy = " + mI2fortescue.get(3, 0));*/
            //printVector(mS1Fortescue, "S1fortescueCalc", false);
            //printVector(mS2Fortescue, "S2fortescueCalc", false);

            branchI1abc.put(branch, mI1abcCalculated);
            ///printVector(mI1abc, "I1abc", true);
            //printVector(mI1abcCalculated, "I1abcCalculated", true);
            branchI1fort.put(branch, mI1fortescue);
            ///branchI1fortCalc.put(branch, mI1fortescueCalcultated);
            //printVector(mI1fortescue, "I1fortescue", false);
            ///printVector(mI1fortescueCalcultated, "I1fortescueCalc", false);

            branchI2abc.put(branch, mI2abcCalculated);
            ///printVector(mI2abc, "I2abc", true);
            //printVector(mI2abcCalculated, "I2abcCalculated", true);
            branchI2fort.put(branch, mI2fortescue);
            ///branchI2fortCalc.put(branch, mI2fortescueCalcultated);
            //printVector(mI2fortescue, "I2fortescue", false);
            ///printVector(mI2fortescueCalcultated, "I2fortescueCalc", false);

        } else {
            // assymmetry is detected with this line, we handle the equations as coupled between the different sequences
            // we try to get the Tabc matrix

            Matrix mVfortescue = matrixFactory.create(12, 1, 12);
            mVfortescue.add(0, 0, Fortescue.getCartesianFromPolar(v1Homo, ph1Homo).getKey());
            mVfortescue.add(1, 0, Fortescue.getCartesianFromPolar(v1Homo, ph1Homo).getValue());
            mVfortescue.add(2, 0, Fortescue.getCartesianFromPolar(v1, ph1).getKey());
            mVfortescue.add(3, 0, Fortescue.getCartesianFromPolar(v1, ph1).getValue());
            mVfortescue.add(4, 0, Fortescue.getCartesianFromPolar(v1Inv, ph1Inv).getKey());
            mVfortescue.add(5, 0, Fortescue.getCartesianFromPolar(v1Inv, ph1Inv).getValue());
            mVfortescue.add(6, 0, Fortescue.getCartesianFromPolar(v2Homo, ph2Homo).getKey());
            mVfortescue.add(7, 0, Fortescue.getCartesianFromPolar(v2Homo, ph2Homo).getValue());
            mVfortescue.add(8, 0, Fortescue.getCartesianFromPolar(v2, ph2).getKey());
            mVfortescue.add(9, 0, Fortescue.getCartesianFromPolar(v2, ph2).getValue());
            mVfortescue.add(10, 0, Fortescue.getCartesianFromPolar(v2Inv, ph2Inv).getKey());
            mVfortescue.add(11, 0, Fortescue.getCartesianFromPolar(v2Inv, ph2Inv).getValue());

            DenseMatrix yodi = asymLine.getAdmittanceTerms().getmYodi();
            DenseMatrix mIfortescue = yodi.times(mVfortescue).toDense();

            DenseMatrix mI1fortescue = matrixFactory.create(6, 1, 6).toDense();
            mI1fortescue.add(0, 0, mIfortescue.get(0, 0));
            mI1fortescue.add(1, 0, mIfortescue.get(1, 0));
            mI1fortescue.add(2, 0, mIfortescue.get(2, 0));
            mI1fortescue.add(3, 0, mIfortescue.get(3, 0));
            mI1fortescue.add(4, 0, mIfortescue.get(4, 0));
            mI1fortescue.add(5, 0, mIfortescue.get(5, 0));

            DenseMatrix mI2fortescue = matrixFactory.create(6, 1, 6).toDense();
            mI2fortescue.add(0, 0, mIfortescue.get(6, 0));
            mI2fortescue.add(1, 0, mIfortescue.get(7, 0));
            mI2fortescue.add(2, 0, mIfortescue.get(8, 0));
            mI2fortescue.add(3, 0, mIfortescue.get(9, 0));
            mI2fortescue.add(4, 0, mIfortescue.get(10, 0));
            mI2fortescue.add(5, 0, mIfortescue.get(11, 0));

            DenseMatrix yabc = asymLine.getAdmittanceTerms().getmYabc();
            DenseMatrix mIabc = yabc.times(mVabc).toDense();

            // compute Iodi from Iabc to check differences
            DenseMatrix mI1abc = matrixFactory.create(6, 1, 6).toDense();
            mI1abc.add(0, 0, mIabc.get(0, 0));
            mI1abc.add(1, 0, mIabc.get(1, 0));
            mI1abc.add(2, 0, mIabc.get(2, 0));
            mI1abc.add(3, 0, mIabc.get(3, 0));
            mI1abc.add(4, 0, mIabc.get(4, 0));
            mI1abc.add(5, 0, mIabc.get(5, 0));

            DenseMatrix mI2abc = matrixFactory.create(6, 1, 6).toDense();
            mI2abc.add(0, 0, mIabc.get(6, 0));
            mI2abc.add(1, 0, mIabc.get(7, 0));
            mI2abc.add(2, 0, mIabc.get(8, 0));
            mI2abc.add(3, 0, mIabc.get(9, 0));
            mI2abc.add(4, 0, mIabc.get(10, 0));
            mI2abc.add(5, 0, mIabc.get(11, 0));

            DenseMatrix mI1fortescueCalcultated = Fortescue.getFortescueInverseMatrix().times(mI1abc);
            DenseMatrix mI2fortescueCalcultated = Fortescue.getFortescueInverseMatrix().times(mI2abc);

            DenseMatrix mI1abcCalculated = Fortescue.getFortescueMatrix().times(mI1fortescue);
            DenseMatrix mI2abcCalculated = Fortescue.getFortescueMatrix().times(mI2fortescue);

            System.out.println("---------- BRANCH " + branch.getId());
            branchI1abc.put(branch, mI1abc);
            //printVector(mI1abc, "I1abc", true);
            //printVector(mI1abcCalculated, "I1abcCalculated", true);
            ///branchI1fort.put(branch, mI1fortescue);
            ///branchI1fortCalc.put(branch, mI1fortescueCalcultated);
            //printVector(mI1fortescue, "I1fortescue", false);
            //printVector(mI1fortescueCalcultated, "I1fortescueCalc", false);

            branchI2abc.put(branch, mI2abc);
            //printVector(mI2abc, "I2abc", true);
            //printVector(mI2abcCalculated, "I2abcCalculated", true);
            branchI2fort.put(branch, mI2fortescue);
            branchI2fortCalc.put(branch, mI2fortescueCalcultated);
            //printVector(mI2fortescue, "I2fortescue", false);
            //printVector(mI2fortescueCalcultated, "I2fortescueCalc", false);

        }

        // Powers
        double pA1 = mVabc.get(0, 0) * branchI1abc.get(branch).get(0, 0) + mVabc.get(1, 0) * branchI1abc.get(branch).get(1, 0);
        double qA1 = mVabc.get(1, 0) * branchI1abc.get(branch).get(0, 0) - mVabc.get(0, 0) * branchI1abc.get(branch).get(1, 0);
        double pA2 = mVabc.get(6, 0) * branchI2abc.get(branch).get(0, 0) + mVabc.get(7, 0) * branchI2abc.get(branch).get(1, 0);
        double qA2 = mVabc.get(7, 0) * branchI2abc.get(branch).get(0, 0) - mVabc.get(6, 0) * branchI2abc.get(branch).get(1, 0);

        double pB1 = mVabc.get(2, 0) * branchI1abc.get(branch).get(2, 0) + mVabc.get(3, 0) * branchI1abc.get(branch).get(3, 0);
        double qB1 = mVabc.get(3, 0) * branchI1abc.get(branch).get(2, 0) - mVabc.get(2, 0) * branchI1abc.get(branch).get(3, 0);
        double pB2 = mVabc.get(8, 0) * branchI2abc.get(branch).get(2, 0) + mVabc.get(9, 0) * branchI2abc.get(branch).get(3, 0);
        double qB2 = mVabc.get(9, 0) * branchI2abc.get(branch).get(2, 0) - mVabc.get(8, 0) * branchI2abc.get(branch).get(3, 0);

        double pC1 = mVabc.get(4, 0) * branchI1abc.get(branch).get(4, 0) + mVabc.get(5, 0) * branchI1abc.get(branch).get(5, 0);
        double qC1 = mVabc.get(5, 0) * branchI1abc.get(branch).get(4, 0) - mVabc.get(4, 0) * branchI1abc.get(branch).get(5, 0);
        double pC2 = mVabc.get(10, 0) * branchI2abc.get(branch).get(4, 0) + mVabc.get(11, 0) * branchI2abc.get(branch).get(5, 0);
        double qC2 = mVabc.get(11, 0) * branchI2abc.get(branch).get(4, 0) - mVabc.get(10, 0) * branchI2abc.get(branch).get(5, 0);

        DenseMatrix s1 = matrixFactory.create(6, 1, 6).toDense();
        s1.add(0, 0, pA1);
        s1.add(1, 0, qA1);
        s1.add(2, 0, pB1);
        s1.add(3, 0, qB1);
        s1.add(4, 0, pC1);
        s1.add(5, 0, qC1);

        DenseMatrix s2 = matrixFactory.create(6, 1, 6).toDense();
        s2.add(0, 0, pA2);
        s2.add(1, 0, qA2);
        s2.add(2, 0, pB2);
        s2.add(3, 0, qB2);
        s2.add(4, 0, pC2);
        s2.add(5, 0, qC2);

        branchS1abc.put(branch, s1);
        branchS2abc.put(branch, s2);
        printVector(s1, "S1ABC", true);
        printVector(s2, "S2ABC", true);

    }

    public void getNodalSum(LfNetwork network) {
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null) {
                DenseMatrix bilanI1fortescue;
                if (sumIfort.containsKey(bus1)) {
                    bilanI1fortescue = sumIfort.get(bus1);
                } else {
                    bilanI1fortescue = new DenseMatrix(6, 1);
                    sumIfort.put(bus1, bilanI1fortescue);
                }
                bilanI1fortescue.set(0, 0, branchI1fort.get(branch).get(0, 0) + bilanI1fortescue.get(0, 0));
                bilanI1fortescue.add(1, 0, branchI1fort.get(branch).get(1, 0));
                bilanI1fortescue.add(2, 0, branchI1fort.get(branch).get(2, 0));
                bilanI1fortescue.add(3, 0, branchI1fort.get(branch).get(3, 0));
                bilanI1fortescue.add(4, 0, branchI1fort.get(branch).get(4, 0));
                bilanI1fortescue.add(5, 0, branchI1fort.get(branch).get(5, 0));
            }
            if (bus2 != null) {
                DenseMatrix bilanI2fortescue;
                if (sumIfort.containsKey(bus2)) {
                    bilanI2fortescue = sumIfort.get(bus2);
                } else {
                    bilanI2fortescue = new DenseMatrix(6, 1);
                    sumIfort.put(bus2, bilanI2fortescue);
                }
                bilanI2fortescue.set(0, 0, branchI2fort.get(branch).get(0, 0) + bilanI2fortescue.get(0, 0));
                bilanI2fortescue.add(1, 0, branchI2fort.get(branch).get(1, 0));
                bilanI2fortescue.add(2, 0, branchI2fort.get(branch).get(2, 0));
                bilanI2fortescue.add(3, 0, branchI2fort.get(branch).get(3, 0));
                bilanI2fortescue.add(4, 0, branchI2fort.get(branch).get(4, 0));
                bilanI2fortescue.add(5, 0, branchI2fort.get(branch).get(5, 0));
            }

        }

        // print of checksums
        for (Map.Entry<LfBus, DenseMatrix> entry : sumIfort.entrySet()) {
            LfBus bus = entry.getKey();
            DenseMatrix checksum = entry.getValue();
            System.out.println("---------- BUS " + bus.getId());
            printVector(checksum, "IfortChecksum " + bus.getId() + " = ", false);
        }

    }

    public void printVector(DenseMatrix matrix, String name, boolean isAbc) {
        // Vector [6,1] in input
        System.out.println("OUT>>>>>>>> " + name);
        if (isAbc) {
            System.out.println("OUT>>>>>>>> xA = " + matrix.get(0, 0));
            System.out.println("OUT>>>>>>>> yA = " + matrix.get(1, 0));
            System.out.println("OUT>>>>>>>> xB = " + matrix.get(2, 0));
            System.out.println("OUT>>>>>>>> yB = " + matrix.get(3, 0));
            System.out.println("OUT>>>>>>>> xC = " + matrix.get(4, 0));
            System.out.println("OUT>>>>>>>> yC = " + matrix.get(5, 0));
        } else {
            System.out.println("OUT>>>>>>>> xHom = " + matrix.get(0, 0));
            System.out.println("OUT>>>>>>>> yHom = " + matrix.get(1, 0));
            System.out.println("OUT>>>>>>>> xDir = " + matrix.get(2, 0));
            System.out.println("OUT>>>>>>>> yDir = " + matrix.get(3, 0));
            System.out.println("OUT>>>>>>>> xInv = " + matrix.get(4, 0));
            System.out.println("OUT>>>>>>>> yInv = " + matrix.get(5, 0));
        }

    }

}
