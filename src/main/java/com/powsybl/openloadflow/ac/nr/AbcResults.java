package com.powsybl.openloadflow.ac.nr;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.extensions.AsymBranch;
//import com.powsybl.openloadflow.network.extensions.AsymBus;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import java.util.HashMap;
import java.util.Map;

public class AbcResults {

    // busses
    public Map<LfBus, Complex> busVa = new HashMap<>();
    public Map<LfBus, Complex> busVb = new HashMap<>();
    public Map<LfBus, Complex> busVc = new HashMap<>();

    // branches
    public Map<LfBranch, DenseMatrix> branchI1abc = new HashMap<>();
    public Map<LfBranch, DenseMatrix> branchI1fort = new HashMap<>();

    public Map<LfBranch, DenseMatrix> branchI2abc = new HashMap<>();
    public Map<LfBranch, DenseMatrix> branchI2fort = new HashMap<>();

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

        //Pair<Double, Double> vZero = new Pair<>(0., 0.);
        //Pair<Double, Double> vNegative = new Pair<>(0., 0.);
        System.out.println("---------- BUS " + bus.getId() + "------------------------");
        boolean isFortescue = true;
        boolean hasPhaseA = true;
        boolean hasPhaseB = true;
        boolean hasPhaseC = true;
        LfAsymBus asymBus = bus.getAsym();
        //AsymBus asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus != null) {
            phHomo = asymBus.getAngleZ();
            vHomo = asymBus.getVz();
            phInv = asymBus.getAngleN();
            vInv = asymBus.getVn();
            if (!asymBus.isFortescueRepresentation()) {
                isFortescue = false;
            }
            hasPhaseA = asymBus.isHasPhaseA();
            hasPhaseB = asymBus.isHasPhaseB();
            hasPhaseC = asymBus.isHasPhaseC();
            System.out.println(" zero = " + vHomo + " (" + asymBus.getAngleZ());
            System.out.println(" posi = " + v + " (" + bus.getAngle());
            System.out.println(" nega = " + vInv + " (" + asymBus.getAngleN());

            //vZero = asymBus.getCompleteVandTheta(Fortescue.SequenceType.ZERO, vHomo, phHomo, v, ph, vInv, phInv, asymBus.isHasPhaseA(), asymBus.isHasPhaseB(), asymBus.isHasPhaseC(), asymBus.getAsymBusVariableType());
            //vNegative = asymBus.getCompleteVandTheta(Fortescue.SequenceType.NEGATIVE, vHomo, phHomo, v, ph, vInv, phInv, asymBus.isHasPhaseA(), asymBus.isHasPhaseB(), asymBus.isHasPhaseC(), asymBus.getAsymBusVariableType());

            //System.out.println(" Final zero = " + vZero.getFirst() + " (" + vZero.getSecond());
            //System.out.println(" Final posi = " + v + " (" + bus.getAngle());
            //System.out.println(" Final nega = " + vNegative.getFirst() + " (" + vNegative.getSecond());
        }

        // [G1]   [ 1  1  1 ]   [Gh]
        // [G2] = [ 1  a²  a] * [Gd]
        // [G3]   [ 1  a  a²]   [Gi]
        MatrixFactory matrixFactory = new DenseMatrixFactory();

        /*Vector2D directComponent = Fortescue.getCartesianFromPolar(v, ph);
        Vector2D homopolarComponent = Fortescue.getCartesianFromPolar(vHomo, phHomo);
        Vector2D inversComponent = Fortescue.getCartesianFromPolar(vInv, phInv);*/
        Vector2D directComponent = Fortescue.getCartesianFromPolar(v, ph);
        Vector2D homopolarComponent = Fortescue.getCartesianFromPolar(vHomo, phHomo);
        Vector2D inversComponent = Fortescue.getCartesianFromPolar(vInv, phInv);

        Matrix mGfortescue = matrixFactory.create(6, 1, 6);
        mGfortescue.add(0, 0, homopolarComponent.getX());
        mGfortescue.add(1, 0, homopolarComponent.getY());
        mGfortescue.add(2, 0, directComponent.getX());
        mGfortescue.add(3, 0, directComponent.getY());
        mGfortescue.add(4, 0, inversComponent.getX());
        mGfortescue.add(5, 0, inversComponent.getY());

        DenseMatrix mGphase = Fortescue.createMatrix().times(mGfortescue).toDense();

        // used for computing currents
        if (isFortescue) {
            busVa.put(bus, new Complex(mGphase.get(0, 0), mGphase.get(1, 0)));
            busVb.put(bus, new Complex(mGphase.get(2, 0), mGphase.get(3, 0)));
            busVc.put(bus, new Complex(mGphase.get(4, 0), mGphase.get(5, 0)));
        } else {
            if (hasPhaseA && hasPhaseB && hasPhaseC) {
                busVa.put(bus, new Complex(homopolarComponent.getX(), homopolarComponent.getY()));
                busVb.put(bus, new Complex(directComponent.getX(), directComponent.getY()));
                busVc.put(bus, new Complex(inversComponent.getX(), inversComponent.getY()));
            } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
                busVb.put(bus, new Complex(homopolarComponent.getX(), homopolarComponent.getY()));
                busVc.put(bus, new Complex(directComponent.getX(), directComponent.getY()));
                busVa.put(bus, new Complex(0., 0.));
            } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
                busVa.put(bus, new Complex(homopolarComponent.getX(), homopolarComponent.getY()));
                busVc.put(bus, new Complex(directComponent.getX(), directComponent.getY()));
                busVb.put(bus, new Complex(0., 0.));
            } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
                busVa.put(bus, new Complex(homopolarComponent.getX(), homopolarComponent.getY()));
                busVb.put(bus, new Complex(directComponent.getX(), directComponent.getY()));
                busVc.put(bus, new Complex(0., 0.));
            } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
                busVa.put(bus, new Complex(0., 0.));
                busVc.put(bus, new Complex(directComponent.getX(), directComponent.getY()));
                busVb.put(bus, new Complex(0., 0.));
            } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
                busVb.put(bus, new Complex(0., 0.));
                busVa.put(bus, new Complex(directComponent.getX(), directComponent.getY()));
                busVc.put(bus, new Complex(0., 0.));
            } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
                busVa.put(bus, new Complex(0., 0.));
                busVb.put(bus, new Complex(directComponent.getX(), directComponent.getY()));
                busVc.put(bus, new Complex(0., 0.));
            }
        }

        //printVector(mGfortescue.toDense(), "mVfortescue " + bus.getId() + " = ", false);

        double vnom = 1.0;
        //double Vnom = bus.getNominalV();

        System.out.println(" PHASE A = " + busVa.get(bus).abs() * vnom + " (" + Math.toDegrees(busVa.get(bus).getArgument()));
        System.out.println(" PHASE B = " + busVb.get(bus).abs() * vnom + " (" + Math.toDegrees(busVb.get(bus).getArgument()));
        System.out.println(" PHASE C = " + busVc.get(bus).abs() * vnom + " (" + Math.toDegrees(busVc.get(bus).getArgument()));
    }

    public void addBranchAbcResult(LfBranch branch) {

        System.out.println(" ---------------- ABC result for branch = " + branch.getId());

        AsymBranch asymBranch = (AsymBranch) branch.getProperty(AsymBranch.PROPERTY_ASYMMETRICAL);
        if (asymBranch == null) {
            return;
            //System.out.println("Disymmetry detected  for branch : " + branch.getId() + " = " + disconnectionAsymmetry);
        }

        //Line line = (Line) branch.getOriginalIds()
        ComplexMatrix yabc = asymBranch.getYabc();
        if (yabc == null) {
            return;
        }

        LfBus bus1 = branch.getBus1();
        if (bus1 == null) {
            return;
        }

        LfAsymBus asymBus1 = bus1.getAsym();
        //AsymBus asymBus1 = (AsymBus) bus1.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus1 == null) {
            return;
        }

        double v1 = bus1.getV();
        double ph1 = bus1.getAngle();
        double v1Homo = asymBus1.getVz();
        double ph1Homo = asymBus1.getAngleZ();
        double v1Inv = asymBus1.getVn();
        double ph1Inv = asymBus1.getAngleN();

        LfBus bus2 = branch.getBus2();
        if (bus2 == null) {
            return;
        }

        LfAsymBus asymBus2 = bus2.getAsym();
        //AsymBus asymBus2 = (AsymBus) bus2.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus2 == null) {
            return;
        }

        double v2 = bus2.getV();
        double ph2 = bus2.getAngle();
        double v2Homo = asymBus2.getVz();
        double ph2Homo = asymBus2.getAngleZ();
        double v2Inv = asymBus2.getVn();
        double ph2Inv = asymBus2.getAngleN();

        /*Vector2D bus1zero = Fortescue.getCartesianFromPolar(v1Homo, ph1Homo);
        Vector2D bus1Positive = Fortescue.getCartesianFromPolar(v1, ph1);
        Vector2D bus1Negative = Fortescue.getCartesianFromPolar(v1Inv, ph1Inv);

        Vector2D bus2zero = Fortescue.getCartesianFromPolar(v2Homo, ph2Homo);
        Vector2D bus2Positive = Fortescue.getCartesianFromPolar(v2, ph2);
        Vector2D bus2Negative = Fortescue.getCartesianFromPolar(v2Inv, ph2Inv);

        ComplexMatrix v1V2pu = new ComplexMatrix(6, 1);
        v1V2pu.set(1, 1, new Complex(bus1zero.getX(), bus1zero.getY()));
        v1V2pu.set(2, 1, new Complex(bus1Positive.getX(), bus1Positive.getY()));
        v1V2pu.set(3, 1, new Complex(bus1Negative.getX(), bus1Negative.getY()));
        v1V2pu.set(4, 1, new Complex(bus2zero.getX(), bus2zero.getY()));
        v1V2pu.set(5, 1, new Complex(bus2Positive.getX(), bus2Positive.getY()));
        v1V2pu.set(6, 1, new Complex(bus2Negative.getX(), bus2Negative.getY()));
        */

        Complex va1 = busVa.get(bus1);
        Complex vb1 = busVb.get(bus1);
        Complex vc1 = busVc.get(bus1);

        Complex va2 = busVa.get(bus2);
        Complex vb2 = busVb.get(bus2);
        Complex vc2 = busVc.get(bus2);

        ComplexMatrix v1V2pu = new ComplexMatrix(6, 1);
        v1V2pu.set(1, 1, new Complex(va1.getReal(), va1.getImaginary()));
        v1V2pu.set(2, 1, new Complex(vb1.getReal(), vb1.getImaginary()));
        v1V2pu.set(3, 1, new Complex(vc1.getReal(), vc1.getImaginary()));
        v1V2pu.set(4, 1, new Complex(va2.getReal(), va2.getImaginary()));
        v1V2pu.set(5, 1, new Complex(vb2.getReal(), vb2.getImaginary()));
        v1V2pu.set(6, 1, new Complex(vc2.getReal(), vc2.getImaginary()));

        /*System.out.println("---- yabc -------------------------------");
        ComplexMatrix.printComplexMatrix(yabc);
        System.out.println("---- End -------------------------------");

        System.out.println("---- V1V2 pu -------------------------------");
        ComplexMatrix.printComplexMatrix(v1V2pu);
        System.out.println("---- End -------------------------------");*/

        DenseMatrix i1I2Real = yabc.getRealCartesianMatrix().times(v1V2pu.getRealCartesianMatrix());

        ComplexMatrix i1I2pu = ComplexMatrix.getComplexMatrixFromRealCartesian(i1I2Real);

        ComplexMatrix i1Pu = new ComplexMatrix(3, 1);
        i1Pu.set(1, 1, i1I2pu.getTerm(1, 1));
        i1Pu.set(2, 1, i1I2pu.getTerm(2, 1));
        i1Pu.set(3, 1, i1I2pu.getTerm(3, 1));

        //ComplexMatrix i1PuAbc = new ComplexMatrix(3, 1);
        //ComplexMatrix i2PuAbc = new ComplexMatrix(3, 1);

        ComplexMatrix i2Pu = new ComplexMatrix(3, 1);
        i2Pu.set(1, 1, i1I2pu.getTerm(4, 1));
        i2Pu.set(2, 1, i1I2pu.getTerm(5, 1));
        i2Pu.set(3, 1, i1I2pu.getTerm(6, 1));
/*
        boolean isFortescue1 = true;
        // if (!asymBus1.isFortescueRepresentation()) {
        if (false) {
            isFortescue1 = false;
            i1PuAbc = ComplexMatrix.getComplexMatrixFromRealCartesian(Fortescue.createComplexMatrix(false).getRealCartesianMatrix().times(i1Pu.getRealCartesianMatrix()));
        } else {
            boolean pA1 = asymBus1.isHasPhaseA();
            boolean pB1 = asymBus1.isHasPhaseB();
            boolean pC1 = asymBus1.isHasPhaseC();
            if (pA1 && pB1 && pC1) {
                i1PuAbc.set(1, 1, i1Pu.getTerm(1, 1));
                i1PuAbc.set(2, 1, i1Pu.getTerm(2, 1));
                i1PuAbc.set(3, 1, i1Pu.getTerm(3, 1));
            } else if (!pA1 && pB1 && pC1) {
                i1PuAbc.set(1, 1, new Complex(0., 0.));
                i1PuAbc.set(2, 1, i1Pu.getTerm(1, 1));
                i1PuAbc.set(3, 1, i1Pu.getTerm(2, 1));
            } else if (pA1 && !pB1 && pC1) {
                i1PuAbc.set(2, 1, new Complex(0., 0.));
                i1PuAbc.set(1, 1, i1Pu.getTerm(1, 1));
                i1PuAbc.set(3, 1, i1Pu.getTerm(2, 1));
            } else if (pA1 && pB1 && !pC1) {
                i1PuAbc.set(3, 1, new Complex(0., 0.));
                i1PuAbc.set(1, 1, i1Pu.getTerm(1, 1));
                i1PuAbc.set(2, 1, i1Pu.getTerm(2, 1));
            } else if (!pA1 && !pB1 && pC1) {
                i1PuAbc.set(1, 1, new Complex(0., 0.));
                i1PuAbc.set(2, 1, new Complex(0., 0.));
                i1PuAbc.set(3, 1, i1Pu.getTerm(2, 1));
            } else if (pA1 && !pB1 && !pC1) {
                i1PuAbc.set(1, 1, i1Pu.getTerm(2, 1));
                i1PuAbc.set(2, 1, new Complex(0., 0.));
                i1PuAbc.set(3, 1, new Complex(0., 0.));
            } else if (!pA1 && pB1 && !pC1) {
                i1PuAbc.set(1, 1, new Complex(0., 0.));
                i1PuAbc.set(2, 1, i1Pu.getTerm(2, 1));
                i1PuAbc.set(3, 1, new Complex(0., 0.));
            } else {
                throw new IllegalStateException("Unknown phase config ");
            }
        }

        boolean isFortescue2 = true;
        // if (!asymBus2.isFortescueRepresentation()) {
        if (false) {
            isFortescue2 = false;
            i2PuAbc = ComplexMatrix.getComplexMatrixFromRealCartesian(Fortescue.createComplexMatrix(false).getRealCartesianMatrix().times(i2Pu.getRealCartesianMatrix()));
        } else {
            boolean pA2 = asymBus2.isHasPhaseA();
            boolean pB2 = asymBus2.isHasPhaseB();
            boolean pC2 = asymBus2.isHasPhaseC();
            if (pA2 && pB2 && pC2) {
                i2PuAbc.set(1, 1, i2Pu.getTerm(1, 1));
                i2PuAbc.set(2, 1, i2Pu.getTerm(2, 1));
                i2PuAbc.set(3, 1, i2Pu.getTerm(3, 1));
            } else if (!pA2 && pB2 && pC2) {
                i2PuAbc.set(1, 1, new Complex(0., 0.));
                i2PuAbc.set(2, 1, i2Pu.getTerm(1, 1));
                i2PuAbc.set(3, 1, i2Pu.getTerm(2, 1));
            } else if (pA2 && !pB2 && pC2) {
                i2PuAbc.set(2, 1, new Complex(0., 0.));
                i2PuAbc.set(1, 1, i2Pu.getTerm(1, 1));
                i2PuAbc.set(3, 1, i2Pu.getTerm(2, 1));
            } else if (pA2 && pB2 && !pC2) {
                i2PuAbc.set(3, 1, new Complex(0., 0.));
                i2PuAbc.set(1, 1, i2Pu.getTerm(1, 1));
                i2PuAbc.set(2, 1, i2Pu.getTerm(2, 1));
            } else if (!pA2 && !pB2 && pC2) {
                i2PuAbc.set(1, 1, new Complex(0., 0.));
                i2PuAbc.set(2, 1, new Complex(0., 0.));
                i2PuAbc.set(3, 1, i2Pu.getTerm(2, 1));
            } else if (pA2 && !pB2 && !pC2) {
                i2PuAbc.set(1, 1, i2Pu.getTerm(2, 1));
                i2PuAbc.set(2, 1, new Complex(0., 0.));
                i2PuAbc.set(3, 1, new Complex(0., 0.));
            } else if (!pA2 && pB2 && !pC2) {
                i2PuAbc.set(1, 1, new Complex(0., 0.));
                i2PuAbc.set(2, 1, i2Pu.getTerm(2, 1));
                i2PuAbc.set(3, 1, new Complex(0., 0.));
            } else {
                throw new IllegalStateException("Unknown phase config ");
            }
        }
*/
        double ibase1 = 100. / bus1.getNominalV();
        double ibase2 = 100. / bus2.getNominalV();

        ComplexMatrix i1Abc = ComplexMatrix.getMatrixScaled(i1Pu, ibase1);
        ComplexMatrix i2Abc = ComplexMatrix.getMatrixScaled(i2Pu, ibase2);

        /*System.out.println(" IA1 = " + i1Abc.getTerm(1, 1).getReal() + " + j(" + i1Abc.getTerm(1, 1).getImaginary());
        System.out.println(" IB1 = " + i1Abc.getTerm(2, 1).getReal() + " + j(" + i1Abc.getTerm(2, 1).getImaginary());
        System.out.println(" IC1 = " + i1Abc.getTerm(3, 1).getReal() + " + j(" + i1Abc.getTerm(3, 1).getImaginary());

        System.out.println(" IA2 = " + i2Abc.getTerm(1, 1).getReal() + " + j(" + i2Abc.getTerm(1, 1).getImaginary());
        System.out.println(" IB2 = " + i2Abc.getTerm(2, 1).getReal() + " + j(" + i2Abc.getTerm(2, 1).getImaginary());
        System.out.println(" IC2 = " + i2Abc.getTerm(3, 1).getReal() + " + j(" + i2Abc.getTerm(3, 1).getImaginary());*/

        System.out.println(" IA1 = " + i1Abc.getTerm(1, 1).abs() + " + (" + Math.toDegrees(i1Abc.getTerm(1, 1).getArgument()));
        System.out.println(" IB1 = " + i1Abc.getTerm(2, 1).abs() + " + (" + Math.toDegrees(i1Abc.getTerm(2, 1).getArgument()));
        System.out.println(" IC1 = " + i1Abc.getTerm(3, 1).abs() + " + (" + Math.toDegrees(i1Abc.getTerm(3, 1).getArgument()));

        System.out.println(" IA2 = " + i2Abc.getTerm(1, 1).abs() + " + (" + Math.toDegrees(i2Abc.getTerm(1, 1).getArgument()));
        System.out.println(" IB2 = " + i2Abc.getTerm(2, 1).abs() + " + (" + Math.toDegrees(i2Abc.getTerm(2, 1).getArgument()));
        System.out.println(" IC2 = " + i2Abc.getTerm(3, 1).abs() + " + (" + Math.toDegrees(i2Abc.getTerm(3, 1).getArgument()));

        Complex sA1 = busVa.get(bus1).multiply(i1Abc.getTerm(1, 1).conjugate()).multiply(bus1.getNominalV());
        Complex sB1 = busVb.get(bus1).multiply(i1Abc.getTerm(2, 1).conjugate()).multiply(bus1.getNominalV());
        Complex sC1 = busVc.get(bus1).multiply(i1Abc.getTerm(3, 1).conjugate()).multiply(bus1.getNominalV());

        Complex sA2 = busVa.get(bus2).multiply(i2Abc.getTerm(1, 1).conjugate()).multiply(bus2.getNominalV());
        Complex sB2 = busVb.get(bus2).multiply(i2Abc.getTerm(2, 1).conjugate()).multiply(bus2.getNominalV());
        Complex sC2 = busVc.get(bus2).multiply(i2Abc.getTerm(3, 1).conjugate()).multiply(bus2.getNominalV());

        System.out.println(" SA1 = " + sA1.getReal() + " + j(" + sA1.getImaginary());
        System.out.println(" SB1 = " + sB1.getReal() + " + j(" + sB1.getImaginary());
        System.out.println(" SC1 = " + sC1.getReal() + " + j(" + sC1.getImaginary());

        System.out.println(" SA2 = " + sA2.getReal() + " + j(" + sA2.getImaginary());
        System.out.println(" SB2 = " + sB2.getReal() + " + j(" + sB2.getImaginary());
        System.out.println(" SC2 = " + sC2.getReal() + " + j(" + sC2.getImaginary());

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
