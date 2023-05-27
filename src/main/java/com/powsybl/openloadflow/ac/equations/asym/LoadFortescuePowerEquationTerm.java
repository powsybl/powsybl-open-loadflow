/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadFortescuePowerEquationTerm extends AbstractElementEquationTerm<LfBus, AcVariableType, AcEquationType> {

    // positive
    protected final Variable<AcVariableType> vVar;

    protected final Variable<AcVariableType> phVar;

    // negative
    protected final Variable<AcVariableType> vVarNegative;

    protected final Variable<AcVariableType> phVarNegative;

    // zero
    protected final Variable<AcVariableType> vVarZero;

    protected final Variable<AcVariableType> phVarZero;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    private final ComplexPart complexPart;

    private final Fortescue.SequenceType sequenceType;

    public LoadFortescuePowerEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType) {
        super(bus);
        Objects.requireNonNull(variableSet);
        this.complexPart = Objects.requireNonNull(complexPart);
        this.sequenceType = Objects.requireNonNull(sequenceType);

        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        phVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI);

        vVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_NEGATIVE);
        phVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_NEGATIVE);

        vVarZero = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_ZERO);
        phVarZero = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_ZERO);

        variables.add(vVar);
        variables.add(phVar);
        variables.add(vVarNegative);
        variables.add(phVarNegative);
        variables.add(vVarZero);
        variables.add(phVarZero);
    }

    public double ph(Fortescue.SequenceType g) {
        switch (g) {
            case ZERO:
                return sv.get(phVarZero.getRow());

            case POSITIVE:
                return sv.get(phVar.getRow());

            case NEGATIVE:
                return sv.get(phVarNegative.getRow());

            default:
                throw new IllegalStateException("Unknown Phi variable at bus: " + element.getId());
        }
    }

    public double v(Fortescue.SequenceType g) {
        switch (g) {
            case ZERO:
                return sv.get(vVarZero.getRow());

            case POSITIVE:
                return sv.get(vVar.getRow());

            case NEGATIVE:
                return sv.get(vVarNegative.getRow());

            default:
                throw new IllegalStateException("Unknown V variable at bus: " + element.getId());
        }
    }

    public static double pq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType, double vZero, double phZero, double vPositive, double phPositive, double vNegative, double phNegative) {
        // We use the formula with complex matrices:
        //
        // [So]    [Vo  0   0]              [1/Va  0  0]   [Sa]
        // [Sd] =  [0  Vd   0]. 1/3 . [F] . [0  1/Vb  0] . [Sb]
        // [Si]    [0   0  Vi]              [0   0 1/Vc]   [Sc]
        //                      <------------------------------>
        //                                 term (Ifortescue)*
        //         <------------------------------------------->
        //                    term Sfortescue

        LfAsymBus asymBus = bus.getAsym();
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        // Build of Sabc/3 vector
        DenseMatrix mSabc3 = getCartesianMatrix(asymBus.getPa() / 3, asymBus.getQa() / 3, asymBus.getPb() / 3, asymBus.getQb() / 3, asymBus.getPc() / 3, asymBus.getQc() / 3, true);

        Vector2D positiveSequence = Fortescue.getCartesianFromPolar(vPositive, phPositive);
        Vector2D zeroSequence = Fortescue.getCartesianFromPolar(vZero, phZero);
        Vector2D negativeSequence = Fortescue.getCartesianFromPolar(vNegative, phNegative);

        DenseMatrix mVfortescue = getCartesianMatrix(zeroSequence.getX(), zeroSequence.getY(), positiveSequence.getX(), positiveSequence.getY(), negativeSequence.getX(), negativeSequence.getY(), true); // vector build with cartesian values (Vx,Vy) of complex fortescue voltages
        DenseMatrix mVabc = Fortescue.createMatrix().times(mVfortescue).toDense(); // vector build with cartesian values of complex abc voltages

        // build  1/Vabc square matrix
        DenseMatrix mInvVabc = getInvVabcSquare(bus, mVabc.get(0, 0), mVabc.get(1, 0), mVabc.get(2, 0), mVabc.get(3, 0), mVabc.get(4, 0), mVabc.get(5, 0));

        // build Vfortescue square matrix
        DenseMatrix mSquareVFortescue = getCartesianMatrix(mVfortescue.get(0, 0), mVfortescue.get(1, 0), mVfortescue.get(2, 0), mVfortescue.get(3, 0), mVfortescue.get(4, 0), mVfortescue.get(5, 0), false);

        DenseMatrix m0T0 = mInvVabc.times(mSabc3);
        DenseMatrix mIfortescueConjugate = Fortescue.createMatrix().times(m0T0);
        DenseMatrix mSfortescue = mSquareVFortescue.times(mIfortescueConjugate); //  term T0 = Sfortescue

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? mIfortescueConjugate.get(0, 0) : -mIfortescueConjugate.get(1, 0); // IxZero or IyZero

            case POSITIVE:
                return complexPart == ComplexPart.REAL ? mSfortescue.get(2, 0) : mSfortescue.get(3, 0); // Ppositive or Qpositive

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? mIfortescueConjugate.get(4, 0) : -mIfortescueConjugate.get(5, 0); // IxNegative or IyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    public static double dpq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType, Variable<AcVariableType> derVariable, double vo, double pho, double vd, double phd, double vi, double phi) {
        // We derivate the PQ formula with complex matrices:
        //
        //    [So]              [dVo/dx  0   0]         [1/Va  0  0]   [Sa]        [Vo  0  0]                [Sa  0   0]   [1/Va  0  0]   [1/Va  0  0]         [dV0/dx]
        // d( [Sd] )/dx = 1/3 . [0  dVd/dx   0] . [F] . [0  1/Vb  0] . [Sb]    +   [0  Vd  0] . [F] .(-1/3). [0   Sb  0] . [0  1/Vb  0] . [0  1/Vb  0] . [F] . [dVd/dx]
        //    [Si]              [0   0  dVi/dx]         [0   0 1/Vc]   [Sc]        [0   0 Vi]                [0   0  Sc]   [0   0 1/Vc]   [0   0 1/Vc]         [dVi/dx]
        //                  <-------------------------------------------->                     <----------------------------------------------------------------------->
        //                                      term T1                                                           term (dIfortescue)*
        //                                                                         <----------------------------------------------------------------------------------->
        //                                                                                                          term T2

        LfAsymBus asymBus = bus.getAsym();
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        // computation of dV0/dx , dV1/dx, dV2/dx
        double dV0x = 0;
        double dV0y = 0;
        double dV1x = 0;
        double dV1y = 0;
        double dV2x = 0;
        double dV2y = 0;
        if (derVariable.getType() == AcVariableType.BUS_V) {
            dV1x = Math.cos(phd);
            dV1y = Math.sin(phd);
        } else if (derVariable.getType() == AcVariableType.BUS_V_ZERO) {
            dV0x = Math.cos(pho);
            dV0y = Math.sin(pho);
        } else if (derVariable.getType() == AcVariableType.BUS_V_NEGATIVE) {
            dV2x = Math.cos(phi);
            dV2y = Math.sin(phi);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI) {
            dV1x = vd * -Math.sin(phd);
            dV1y = vd * Math.cos(phd);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_ZERO) {
            dV0x = vo * -Math.sin(pho);
            dV0y = vo * Math.cos(pho);
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_NEGATIVE) {
            dV2x = vi * -Math.sin(phi);
            dV2y = vi * Math.cos(phi);
        } else {
            throw new IllegalStateException("Unknown derivation variable: " + derVariable + " at bus : " + bus.getId());
        }

        // build of voltage vectors
        Vector2D positiveComponent = Fortescue.getCartesianFromPolar(vd, phd);
        Vector2D zeroComponent = Fortescue.getCartesianFromPolar(vo, pho);
        Vector2D negativeComponent = Fortescue.getCartesianFromPolar(vi, phi);

        DenseMatrix mVfortescue = getCartesianMatrix(zeroComponent.getX(), zeroComponent.getY(), positiveComponent.getX(), positiveComponent.getY(), negativeComponent.getX(), negativeComponent.getY(), true); // vector build with cartesian values of complex fortescue voltages
        DenseMatrix mVabc = Fortescue.createMatrix().times(mVfortescue).toDense(); // vector build with cartesian values of complex abc voltages

        // build of Sabc vector
        DenseMatrix mSabc3 = getCartesianMatrix(asymBus.getPa() / 3, asymBus.getQa() / 3, asymBus.getPb() / 3, asymBus.getQb() / 3, asymBus.getPc() / 3, asymBus.getQc() / 3, true);

        // build of 1/Vabc square matrix
        DenseMatrix mInvVabc = getInvVabcSquare(bus, mVabc.get(0, 0), mVabc.get(1, 0), mVabc.get(2, 0), mVabc.get(3, 0), mVabc.get(4, 0), mVabc.get(5, 0));

        // build of derivative fortescue voltage square matrix
        DenseMatrix mdVSquare = getCartesianMatrix(dV0x, dV0y, dV1x, dV1y, dV2x, dV2y, false);

        // computation of vector = term T1:
        DenseMatrix m0T1 = mInvVabc.times(mSabc3);
        DenseMatrix m1T1 = Fortescue.createMatrix().times(m0T1);
        DenseMatrix mT1 = mdVSquare.times(m1T1);

        // build Vfortescue square matrix
        DenseMatrix mSquareVFortescue = getCartesianMatrix(mVfortescue.get(0, 0), mVfortescue.get(1, 0), mVfortescue.get(2, 0), mVfortescue.get(3, 0), mVfortescue.get(4, 0), mVfortescue.get(5, 0), false);

        // build of -1/3.Sabc square matrix
        DenseMatrix mMinusSabc3Square = getCartesianMatrix(-asymBus.getPa() / 3, -asymBus.getQa() / 3, -asymBus.getPb() / 3, -asymBus.getQb() / 3, -asymBus.getPc() / 3, -asymBus.getQc() / 3, false);

        // buils of fortescue derivative vector
        DenseMatrix mdV = getCartesianMatrix(dV0x, dV0y, dV1x, dV1y, dV2x, dV2y, true);

        // computation of vector = term T2:
        DenseMatrix m0T2 = Fortescue.createMatrix().times(mdV);
        DenseMatrix m1T2 = mInvVabc.times(m0T2);
        DenseMatrix m2T2 = mInvVabc.times(m1T2);
        DenseMatrix m3T2 = mMinusSabc3Square.times(m2T2);
        DenseMatrix mdIFortescueConjugate = Fortescue.createMatrix().times(m3T2);
        DenseMatrix mT2 = mSquareVFortescue.times(mdIFortescueConjugate);

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? mdIFortescueConjugate.get(0, 0) : -mdIFortescueConjugate.get(1, 0); // dIxZero or dIyZero

            case POSITIVE:
                return complexPart == ComplexPart.REAL ? mT1.get(2, 0) + mT2.get(2, 0) : mT1.get(3, 0) + mT2.get(3, 0); // dPpositive or dQpositive

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? mdIFortescueConjugate.get(4, 0) : -mdIFortescueConjugate.get(5, 0); // dIxNegative or dIyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    @Override
    public double eval() {
        return pq(element, complexPart, sequenceType,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE));
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return dpq(element, complexPart, sequenceType, variable,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE));
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    public static DenseMatrix getInvVabcSquare(LfBus bus, double vAx, double vAy, double vBx, double vBy, double vCx, double vCy) {
        double epsilon = 0.00000001;
        double vAcongVa = vAx * vAx + vAy * vAy;
        String cantBuildLoad = " is null at bus : " + bus.getId() + " : cannot build load model";
        if (vAcongVa < epsilon) {
            throw new IllegalStateException("Va" + cantBuildLoad);
        }
        double vBcongVb = vBx * vBx + vBy * vBy;
        if (vBcongVb < epsilon) {
            throw new IllegalStateException("Vb" + cantBuildLoad);
        }
        double vCcongVc = vCx * vCx + vCy * vCy;
        if (vCcongVc < epsilon) {
            throw new IllegalStateException("Vc" + cantBuildLoad);
        }
        double invVax = vAx / vAcongVa;
        double invVay = -vAy / vAcongVa;
        double invVbx = vBx / vBcongVb;
        double invVby = -vBy / vBcongVb;
        double invVcx = vCx / vCcongVc;
        double invVcy = -vCy / vCcongVc;

        return getCartesianMatrix(invVax, invVay, invVbx, invVby, invVcx, invVcy, false);
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
