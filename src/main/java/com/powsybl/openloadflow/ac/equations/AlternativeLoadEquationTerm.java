package com.powsybl.openloadflow.ac.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.AbstractNamedEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.Extensions.AsymBus;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AlternativeLoadEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    protected final LfBus bus;

    // direct
    protected final Variable<AcVariableType> vVar;

    protected final Variable<AcVariableType> phVar;

    // inverse
    protected final Variable<AcVariableType> vVarInv;

    protected final Variable<AcVariableType> phVarInv;

    // homopolar
    protected final Variable<AcVariableType> vVarHom;

    protected final Variable<AcVariableType> phVarHom;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    private final boolean isActive; // true if active power asked, false if reactive power asked
    private final int sequenceNum; // 0 = hompolar, 1 = direct, 2 = inverse

    public AlternativeLoadEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, boolean isActive, int sequenceNum) {
        super(true);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);

        this.bus = bus;
        this.isActive = isActive;
        this.sequenceNum = sequenceNum;

        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        phVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI);

        vVarInv = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_INVERSE);
        phVarInv = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_INVERSE);

        vVarHom = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_HOMOPOLAR);
        phVarHom = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_HOMOPOLAR);

        variables.add(vVar);
        variables.add(phVar);
        variables.add(vVarInv);
        variables.add(phVarInv);
        variables.add(vVarHom);
        variables.add(phVarHom);
    }

    public double ph(int g) {
        switch (g) {
            case 0: // homopolar
                return sv.get(phVarHom.getRow());

            case 1: // direct
                return sv.get(phVar.getRow());

            case 2: // inverse
                return sv.get(phVarInv.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    public double v(int g) {
        switch (g) {
            case 0: // homopolar
                return sv.get(vVarHom.getRow());

            case 1: // direct
                return sv.get(vVar.getRow());

            case 2: // inverse
                return sv.get(vVarInv.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    public static double pq(boolean isActive, int sequenceNum, AlternativeLoadEquationTerm eqTerm, double vo, double pho, double vd, double phd, double vi, double phi) {
        /*System.out.println("eval PQ >>>>>>>> vo = " + vo);
        System.out.println("eval PQ >>>>>>>> pho = " + pho);
        System.out.println("eval PQ >>>>>>>> vd = " + vd);
        System.out.println("eval PQ >>>>>>>> phd = " + phd);
        System.out.println("eval PQ >>>>>>>> vi = " + vi);
        System.out.println("eval PQ >>>>>>>> phi = " + phi);*/
        // We use the formula with complex matrices:
        //
        // [So]         [Vo  0   0]         [1/Va  0  0]   [Sa]
        // [Sd] = 1/3 . [0  Vd   0] . [F] . [0  1/Vb  0] . [Sb]
        // [Si]         [0   0  Vi]         [0   0 1/Vc]   [Sc]
        //        <-------------------------------------------->
        //                          term T0

        AsymBus asymBus = (AsymBus) eqTerm.bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + eqTerm.bus.getId());
        }

        // Build of Sabc/3 vector
        DenseMatrix mSabc3 = getCartesianMatrix(asymBus.getPa() / 3, asymBus.getQa() / 3, asymBus.getPb() / 3, asymBus.getQb() / 3, asymBus.getPc() / 3, asymBus.getQc() / 3, true);

        Pair<Double, Double> directComponent = getCartesianFromPolar(vd, phd);
        Pair<Double, Double> homopolarComponent = getCartesianFromPolar(vo, pho);
        Pair<Double, Double> inversComponent = getCartesianFromPolar(vi, phi);

        DenseMatrix mVfortescue = getCartesianMatrix(homopolarComponent.getKey(), homopolarComponent.getValue(), directComponent.getKey(), directComponent.getValue(), inversComponent.getKey(), inversComponent.getValue(), true); // vector build with cartesian values (Vx,Vy) of complex fortescue voltages
        DenseMatrix mVabc = getFortescueMatrix().times(mVfortescue).toDense(); // vector build with cartesian values of complex abc voltages

        // build  1/Vabc square matrix
        DenseMatrix mInvVabc = getInvVabcSquare(mVabc.get(0, 0), mVabc.get(1, 0), mVabc.get(2, 0), mVabc.get(3, 0), mVabc.get(4, 0), mVabc.get(5, 0), eqTerm);

        // build Vfortescue square matrix
        DenseMatrix mSquareVFortescue = getCartesianMatrix(mVfortescue.get(0, 0), mVfortescue.get(1, 0), mVfortescue.get(2, 0), mVfortescue.get(3, 0), mVfortescue.get(4, 0), mVfortescue.get(5, 0), false);

        DenseMatrix m0T0 = mInvVabc.times(mSabc3);
        DenseMatrix m1T0 = getFortescueMatrix().times(m0T0);
        DenseMatrix mSfortescue = mSquareVFortescue.times(m1T0); //  term T0 = Sfortescue

        //String na
        if (isActive && sequenceNum == 0) {
            //System.out.println("OUT>>>>>>>> Po load = " + mSfortescue.get(0, 0));
            return mSfortescue.get(0, 0); // Po
        } else if (!isActive && sequenceNum == 0) {
            //System.out.println("OUT>>>>>>>> Qo load = " + mSfortescue.get(1, 0));
            return mSfortescue.get(1, 0); // Qo
        } else if (isActive && sequenceNum == 1) {
            //System.out.println("OUT>>>>>>>> Pd load = " + mSfortescue.get(2, 0));
            return mSfortescue.get(2, 0); // Pd
        } else if (!isActive && sequenceNum == 1) {
            //System.out.println("OUT>>>>>>>> Qd load = " + mSfortescue.get(3, 0));
            return mSfortescue.get(3, 0); // Qd
        } else if (isActive && sequenceNum == 2) {
            //System.out.println("OUT>>>>>>>> Pi load = " + mSfortescue.get(4, 0));
            return mSfortescue.get(4, 0); // Pi
        } else if (!isActive && sequenceNum == 2) {
            //System.out.println("OUT>>>>>>>> Qi load = " + mSfortescue.get(5, 0));
            return mSfortescue.get(5, 0); // Qi
        } else {
            throw new IllegalStateException("Unknow variable at bus : " + eqTerm.bus.getId());
        }
    }

    public static double dpq(boolean isActive, int sequenceNum, AlternativeLoadEquationTerm eqTerm, Variable<AcVariableType> derVariable, double vo, double pho, double vd, double phd, double vi, double phi) {
        // We derivate the PQ formula with complex matrices:
        //
        //    [So]              [dVo/dx  0   0]         [1/Va  0  0]   [Sa]        [Vo  0  0]         [Sa  0   0]   [1/Va  0  0]   [1/Va  0  0]         [dV0/dx]
        // d( [Sd] )/dx = 1/3 . [0  dVd/dx   0] . [F] . [0  1/Vb  0] . [Sb] -1/3 . [0  Vd  0] . [F] . [0   Sb  0] . [0  1/Vb  0] . [0  1/Vb  0] . [F] . [dVd/dx]
        //    [Si]              [0   0  dVi/dx]         [0   0 1/Vc]   [Sc]        [0   0 Vi]         [0   0  Sc]   [0   0 1/Vc]   [0   0 1/Vc]         [dVi/dx]
        //                  <-------------------------------------------->      <------------------------------------------------------------------------->
        //                                      term T1                                                           term T2

        AsymBus asymBus = (AsymBus) eqTerm.bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + eqTerm.bus.getId());
        }

        // computation of dVo/dx , dVd/dx, dVi/dx
        double dVox = 0;
        double dVoy = 0;
        double dVdx = 0;
        double dVdy = 0;
        double dVix = 0;
        double dViy = 0;
        if (derVariable.getType() == AcVariableType.BUS_V) {
            dVdx = Math.cos(phd);
            dVdy = Math.sin(phd);
        } else if (derVariable.getType() == AcVariableType.BUS_V_HOMOPOLAR) {
            dVox = Math.cos(pho);
            dVoy = Math.sin(pho);
        } else if (derVariable.getType() == AcVariableType.BUS_V_INVERSE) {
            dVix = Math.cos(phi);
            dViy = Math.sin(phi);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI) {
            dVdx = vd * -Math.sin(phd);
            dVdy = vd * Math.cos(phd);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_HOMOPOLAR) {
            dVox = vo * -Math.sin(pho);
            dVoy = vo * Math.cos(pho);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_INVERSE) {
            dVix = vi * -Math.sin(phi);
            dViy = vi * Math.cos(phi);
        } else {
            throw new IllegalStateException("Unknown variable: " + derVariable);
        }

        // build of voltage vectors
        Pair<Double, Double> directComponent = getCartesianFromPolar(vd, phd);
        Pair<Double, Double> homopolarComponent = getCartesianFromPolar(vo, pho);
        Pair<Double, Double> inversComponent = getCartesianFromPolar(vi, phi);
        DenseMatrix mVfortescue = getCartesianMatrix(homopolarComponent.getKey(), homopolarComponent.getValue(), directComponent.getKey(), directComponent.getValue(), inversComponent.getKey(), inversComponent.getValue(), true); // vector build with cartesian values of complex fortescue voltages
        DenseMatrix mVabc = getFortescueMatrix().times(mVfortescue).toDense(); // vector build with cartesian values of complex abc voltages

        // build of Sabc vector
        DenseMatrix mSabc3 = getCartesianMatrix(asymBus.getPa() / 3, asymBus.getQa() / 3, asymBus.getPb() / 3, asymBus.getQb() / 3, asymBus.getPc() / 3, asymBus.getQc() / 3, true);

        // build of 1/Vabc square matrix
        DenseMatrix mInvVabc = getInvVabcSquare(mVabc.get(0, 0), mVabc.get(1, 0), mVabc.get(2, 0), mVabc.get(3, 0), mVabc.get(4, 0), mVabc.get(5, 0), eqTerm);

        // build of derivative fortescue voltage square matrix
        DenseMatrix mdVSquare = getCartesianMatrix(dVox, dVoy, dVdx, dVdy, dVix, dViy, false);

        // computation of vector = term T1:
        DenseMatrix m0T1 = mInvVabc.times(mSabc3);
        DenseMatrix m1T1 = getFortescueMatrix().times(m0T1);
        DenseMatrix mT1 = mdVSquare.times(m1T1);

        // build Vfortescue square matrix
        DenseMatrix mSquareVFortescue = getCartesianMatrix(mVfortescue.get(0, 0), mVfortescue.get(1, 0), mVfortescue.get(2, 0), mVfortescue.get(3, 0), mVfortescue.get(4, 0), mVfortescue.get(5, 0), false);

        // build of -1/3.Sabc square matrix
        DenseMatrix mMinusSabc3Square = getCartesianMatrix(-asymBus.getPa() / 3, -asymBus.getQa() / 3, -asymBus.getPb() / 3, -asymBus.getQb() / 3, -asymBus.getPc() / 3, -asymBus.getQc() / 3, false);

        // buils of fortescue derivative vector
        DenseMatrix mdV = getCartesianMatrix(dVox, dVoy, dVdx, dVdy, dVix, dViy, true);

        // computation of vector = term T2:
        DenseMatrix m0T2 = getFortescueMatrix().times(mdV);
        DenseMatrix m1T2 = mInvVabc.times(m0T2);
        DenseMatrix m2T2 = mInvVabc.times(m1T2);
        DenseMatrix m3T2 = mMinusSabc3Square.times(m2T2);
        DenseMatrix m4T2 = getFortescueMatrix().times(m3T2);
        DenseMatrix mT2 = mSquareVFortescue.times(m4T2);

        if (isActive && sequenceNum == 0) {
            return mT1.get(0, 0) + mT2.get(0, 0); // Po
        } else if (!isActive && sequenceNum == 0) {
            return mT1.get(1, 0) + mT2.get(1, 0); // Qo
        } else if (isActive && sequenceNum == 1) {
            return mT1.get(2, 0) + mT2.get(2, 0); // Pd
        } else if (!isActive && sequenceNum == 1) {
            return mT1.get(3, 0) + mT2.get(3, 0); // Qd
        } else if (isActive && sequenceNum == 2) {
            return mT1.get(4, 0) + mT2.get(4, 0); // Pi
        } else if (!isActive && sequenceNum == 2) {
            return mT1.get(5, 0) + mT2.get(5, 0); // Qi
        } else {
            throw new IllegalStateException("Unknow variable at bus : " + eqTerm.bus.getId());
        }
    }

    @Override
    public double eval() {
        return pq(isActive, sequenceNum, this, v(0), ph(0), v(1), ph(1), v(2), ph(2));
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return dpq(isActive, sequenceNum, this, variable, v(0), ph(0), v(1), ph(1), v(2), ph(2));
    }

    @Override
    protected String getName() {
        return "ac_pq_load";
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BUS;
    } // TODO : check if acceptable

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    @Override
    public int getElementNum() {
        return bus.getNum(); // TODO : check if acceptable
    }

    public static Pair<Double, Double> getCartesianFromPolar(double magnitude, double angle) {
        double xValue = magnitude * Math.cos(angle);
        double yValue = magnitude * Math.sin(angle); // TODO : check radians and degrees
        return new org.apache.commons.math3.util.Pair<>(xValue, yValue);
    }

    public static Pair<Double, Double> getPolarFromCartesian(double xValue, double yValue) {
        double magnitude = Math.sqrt(xValue * xValue + yValue * yValue);
        double phase = Math.atan2(yValue, xValue); // TODO : check radians and degrees
        return new org.apache.commons.math3.util.Pair<>(magnitude, phase);
    }

    public static DenseMatrix getFortescueMatrix() {

        // TODO : mutualize with abc results
        // [G1]   [ 1  1  1 ]   [Gh]
        // [G2] = [ 1  a²  a] * [Gd]
        // [G3]   [ 1  a  a²]   [Gi]
        //Matrix mFortescue = matrixFactory.create(6, 6, 6);
        DenseMatrix mFortescue = new DenseMatrix(6, 6);
        //column 1
        mFortescue.add(0, 0, 1.);
        mFortescue.add(1, 1, 1.);

        mFortescue.add(2, 0, 1.);
        mFortescue.add(3, 1, 1.);

        mFortescue.add(4, 0, 1.);
        mFortescue.add(5, 1, 1.);

        //column 2
        mFortescue.add(0, 2, 1.);
        mFortescue.add(1, 3, 1.);

        mFortescue.add(2, 2, -1. / 2.);
        mFortescue.add(2, 3, Math.sqrt(3.) / 2.);
        mFortescue.add(3, 2, -Math.sqrt(3.) / 2.);
        mFortescue.add(3, 3, -1. / 2.);

        mFortescue.add(4, 2, -1. / 2.);
        mFortescue.add(4, 3, -Math.sqrt(3.) / 2.);
        mFortescue.add(5, 2, Math.sqrt(3.) / 2.);
        mFortescue.add(5, 3, -1. / 2.);

        //column 3
        mFortescue.add(0, 4, 1.);
        mFortescue.add(1, 5, 1.);

        mFortescue.add(2, 4, -1. / 2.);
        mFortescue.add(2, 5, -Math.sqrt(3.) / 2.);
        mFortescue.add(3, 4, Math.sqrt(3.) / 2.);
        mFortescue.add(3, 5, -1. / 2.);

        mFortescue.add(4, 4, -1. / 2.);
        mFortescue.add(4, 5, Math.sqrt(3.) / 2.);
        mFortescue.add(5, 4, -Math.sqrt(3.) / 2.);
        mFortescue.add(5, 5, -1. / 2.);

        return mFortescue.toDense();
    }

    public static DenseMatrix getInvVabcSquare(double vAx, double vAy, double vBx, double vBy, double vCx, double vCy, AlternativeLoadEquationTerm eqTerm) {
        double epsilon = 0.00000001;
        double vAcongVa = vAx * vAx + vAy * vAy;
        if (vAcongVa < epsilon) {
            throw new IllegalStateException("Va is null at bus : " + eqTerm.bus.getId() + " : cannot build load model");
        }
        double vBcongVb = vBx * vBx + vBy * vBy;
        if (vBcongVb < epsilon) {
            throw new IllegalStateException("Vb is null at bus : " + eqTerm.bus.getId() + " : cannot build load model");
        }
        double vCcongVc = vCx * vCx + vCy * vCy;
        if (vCcongVc < epsilon) {
            throw new IllegalStateException("Vc is null at bus : " + eqTerm.bus.getId() + " : cannot build load model");
        }
        double invVax = vAx / vAcongVa;
        double invVay = -vAy / vAcongVa;
        double invVbx = vBx / vBcongVb;
        double invVby = -vBy / vBcongVb;
        double invVcx = vCx / vCcongVc;
        double invVcy = -vCy / vCcongVc;

        DenseMatrix mInvVabc = getCartesianMatrix(invVax, invVay, invVbx, invVby, invVcx, invVcy, false);

        return mInvVabc;
    }

    public static DenseMatrix getCartesianMatrix(double m1x, double m1y, double m2x, double m2y, double m3x, double m3y, boolean isVector) {
        // if this is a vector we build: m = [m1x;m1y;m2x;m2y;m3x;m3y] equivalent in complex to [m1;m2;m3]
        // if not, this is a 6x6 square matrix expected:
        //
        //      [m1x -m1y  0    0    0    0 ]
        //      [m1y  m1x  0    0    0    0 ]
        //      [ 0    0  m2x -m2y   0    0 ]                           [m1  0   0]
        //  m = [ 0    0  m2y  m2x   0    0 ]  equivalent in complex to [ 0  m2  0]
        //      [ 0    0   0    0   m3x -m3y]                           [ 0  0  m3]
        //      [ 0    0   0    0   m3y  m3x]
        //
        DenseMatrix mCartesian;
        if (isVector) {
            mCartesian = new DenseMatrix(6, 1);
            mCartesian.add(0, 0, m1x);
            mCartesian.add(1, 0, m1y);
            mCartesian.add(2, 0, m2x);
            mCartesian.add(3, 0, m2y);
            mCartesian.add(4, 0, m3x);
            mCartesian.add(5, 0, m3y);
        } else {
            mCartesian = new DenseMatrix(6, 6);
            mCartesian.add(0, 0, m1x);
            mCartesian.add(1, 1, m1x);
            mCartesian.add(0, 1, -m1y);
            mCartesian.add(1, 0, m1y);

            mCartesian.add(2, 2, m2x);
            mCartesian.add(3, 3, m2x);
            mCartesian.add(2, 3, -m2y);
            mCartesian.add(3, 2, m2y);

            mCartesian.add(4, 4, m3x);
            mCartesian.add(5, 5, m3x);
            mCartesian.add(4, 5, -m3y);
            mCartesian.add(5, 4, m3y);
        }
        return mCartesian;
    }

}
