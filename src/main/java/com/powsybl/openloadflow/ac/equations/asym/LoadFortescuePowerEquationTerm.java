/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymBus;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.network.extensions.StepType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadFortescuePowerEquationTerm extends AbstractAsymmetricalLoad {
    public LoadFortescuePowerEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType) {
        super(bus, variableSet, complexPart, sequenceType);
    }

    public static double pq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType,
                            double vZero, double phZero, double vPositive, double phPositive, double vNegative, double phNegative) {
        // We use the formula with complex matrices:
        //
        // Case of a Wye load connected to a Wye bus :
        //
        // [So]    [Vo  0   0]              [1/Va  0  0]   [Sa]
        // [Sd] =  [0  Vd   0]. 1/3 . [F] . [0  1/Vb  0] . [Sb]
        // [Si]    [0   0  Vi]              [0   0 1/Vc]   [Sc]
        //                      <------------------------------>
        //                                 term (Ifortescue)*
        //         <------------------------------------------->
        //                    term Sfortescue

        // Case of a Delta load connected to a Delta Bus
        //
        // [So]    [Vo  0   0]                    [1/Vab  0  0]   [Sab]
        // [Sd] =  [0  Vd   0]. 1/3 . [F] . [P] . [0  1/Vbc  0] . [Sbc]
        // [Si]    [0   0  Vi]                    [0   0 1/Vca]   [Sca]
        //                      <------------------------------------->
        //                                 term (Ifortescue)*
        //         <-------------------------------------------------->
        //                    term Sfortescue

        // Case of a Delta load connected to a Wye Bus
        // We suppose that provided input data is Sab, Sbc, Sca and carried respectively through attributes (Pa,Qa), (Pb,Qb), (Pc,Qc)
        //
        // [So]    [Vo  0   0]                    [1/(Va-Vb)  0       0   ]   [Sab]
        // [Sd] =  [0  Vd   0]. 1/3 . [F] . [P] . [   0   1/(Vb-Vc)   0   ] . [Sbc]
        // [Si]    [0   0  Vi]                    [   0       0  1/(Vc-Va)]   [Sca]
        //                      <-------------------------------------------------->
        //                                 term (Ifortescue)*
        //         <--------------------------------------------------------------->
        //                    term Sfortescue

        // We name [P] as complex matrix :
        //       [ 1  0 -1]
        // [P] = [-1  1  0]
        //       [ 0 -1  1]

        AsymBus asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        LegConnectionType loadConnectionType = asymBus.getLoadConnectionType();
        AsymBusVariableType busVariableType = asymBus.getAsymBusVariableType();

        // Build of Sabc/3 vector
        DenseMatrix mSabc3 = getCartesianMatrix(asymBus.getPa() / 3, asymBus.getQa() / 3, asymBus.getPb() / 3, asymBus.getQb() / 3, asymBus.getPc() / 3, asymBus.getQc() / 3, true);

        Vector2D positiveSequence = Fortescue.getCartesianFromPolar(vPositive, phPositive);
        Vector2D zeroSequence = Fortescue.getCartesianFromPolar(vZero, phZero);
        Vector2D negativeSequence = Fortescue.getCartesianFromPolar(vNegative, phNegative);

        DenseMatrix mVfortescue = getCartesianMatrix(zeroSequence.getX(), zeroSequence.getY(), positiveSequence.getX(), positiveSequence.getY(), negativeSequence.getX(), negativeSequence.getY(), true); // vector build with cartesian values (Vx,Vy) of complex fortescue voltages
        DenseMatrix mVabc = Fortescue.createMatrix().times(mVfortescue).toDense(); // vector build with cartesian values of complex abc voltages

        // build  1/Vabc square matrix
        DenseMatrix mInvVabc = getInvVabcSquare(bus, asymBus, mVabc.get(0, 0), mVabc.get(1, 0), mVabc.get(2, 0), mVabc.get(3, 0), mVabc.get(4, 0), mVabc.get(5, 0));

        if (loadConnectionType == LegConnectionType.DELTA && busVariableType == AsymBusVariableType.WYE) {
            if (asymBus.getNbExistingPhases() > 0) {
                throw new IllegalStateException("Delta load with phase disconnection not yet handled at bus : " + bus.getId());
            }
            mInvVabc = getInvVabcSquare(bus, asymBus, mVabc.get(0, 0) - mVabc.get(2, 0), mVabc.get(1, 0) - mVabc.get(3, 0), mVabc.get(2, 0) - mVabc.get(4, 0), mVabc.get(3, 0) - mVabc.get(5, 0), mVabc.get(4, 0) - mVabc.get(0, 0), mVabc.get(5, 0) - mVabc.get(1, 0));
        }

        // build Vfortescue square matrix
        DenseMatrix mSquareVFortescue = getCartesianMatrix(mVfortescue.get(0, 0), mVfortescue.get(1, 0), mVfortescue.get(2, 0), mVfortescue.get(3, 0), mVfortescue.get(4, 0), mVfortescue.get(5, 0), false);

        DenseMatrix m0T0 = mInvVabc.times(mSabc3);
        DenseMatrix m1T0 = m0T0;
        if (loadConnectionType == LegConnectionType.DELTA) {
            m1T0 = complexMatrixP(StepType.STEP_DOWN).getRealCartesianMatrix().times(m0T0);
        }
        DenseMatrix mIfortescueConjugate = Fortescue.createMatrix().times(m1T0);
        DenseMatrix mSfortescue = mSquareVFortescue.times(mIfortescueConjugate); //  term T0 = Sfortescue

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? mIfortescueConjugate.get(0, 0) : -mIfortescueConjugate.get(1, 0); // IxZero or IyZero

            case POSITIVE:
                // check if positive sequence is modelled as P,Q or Ix,Iy
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? mIfortescueConjugate.get(2, 0) : -mIfortescueConjugate.get(3, 0); // IxZero or IyZero
                } else {
                    return complexPart == ComplexPart.REAL ? mSfortescue.get(2, 0) : mSfortescue.get(3, 0); // Ppositive or Qpositive
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? mIfortescueConjugate.get(4, 0) : -mIfortescueConjugate.get(5, 0); // IxNegative or IyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    public static double dpq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType, Variable<AcVariableType> derVariable, double vo, double pho, double vd, double phd, double vi, double phi) {
        // We derivate the PQ formula with complex matrices:

        // Wye Load with Wye variables at bus
        //
        //    [So]              [dVo/dx  0   0]         [1/Va  0  0]   [Sa]        [Vo  0  0]                [Sa  0   0]   [1/Va  0  0]   [1/Va  0  0]         [dV0/dx]
        // d( [Sd] )/dx = 1/3 . [0  dVd/dx   0] . [F] . [0  1/Vb  0] . [Sb]    +   [0  Vd  0] . [F] .(-1/3). [0   Sb  0] . [0  1/Vb  0] . [0  1/Vb  0] . [F] . [dVd/dx]
        //    [Si]              [0   0  dVi/dx]         [0   0 1/Vc]   [Sc]        [0   0 Vi]                [0   0  Sc]   [0   0 1/Vc]   [0   0 1/Vc]         [dVi/dx]
        //                  <-------------------------------------------->                     <----------------------------------------------------------------------->
        //                                      term T1                                                           term (dIfortescue)*
        //                                                                         <----------------------------------------------------------------------------------->
        //                                                                                                          term T2

        // Delta Load with Wye variables at bus
        //    [So]              [dVo/dx  0   0]               [1/(Va-Vb)  0       0   ]   [Sab]        [Vo  0  0]                      [Sab  0   0]   [1/(Va-Vb)  0       0   ]   [1/(Va-Vb)  0       0   ]               [dV0/dx]
        // d( [Sd] )/dx = 1/3 . [0  dVd/dx   0] . [F] . [P] . [   0   1/(Vb-Vc)   0   ] . [Sbc]    +   [0  Vd  0] . [F] . [P] .(-1/3). [0   Sbc  0] . [   0   1/(Vb-Vc)   0   ] . [   0   1/(Vb-Vc)   0   ] . t[P]. [F] . [dVd/dx]
        //    [Si]              [0   0  dVi/dx]               [   0       0  1/(Vc-Va)]   [Sca]        [0   0 Vi]                      [0   0  Sca]   [   0       0  1/(Vc-Va)]   [   0       0  1/(Vc-Va)]               [dVi/dx]
        //                  <----------------------------------------------------------------->                    <-------------------------------------------------------------------------------------------------------->
        //                                      term T1                                                                            term (dIfortescue)*
        //                                                                                             <-------------------------------------------------------------------------------------------------------------------->
        //                                                                                                                                 term T2

        //
        // Delta Load with Delta variables at bus
        //    [So]              [dVo/dx  0   0]         [ 1  0 -1 ]   [1/Vab  0  0]   [Sab]        [Vo  0  0]                [ 1  0 -1 ]   [Sab  0   0]   [1/Vab  0  0]   [1/Va  0  0]         [dV0/dx]
        // d( [Sd] )/dx = 1/3 . [0  dVd/dx   0] . [F] . [-1  1  0 ] . [0  1/Vbc  0] . [Sbc]    +   [0  Vd  0] . [F] .(-1/3). [-1  1  0 ] . [0   Sbc  0] . [0  1/Vbc  0] . [0  1/Vb  0] . [F] . [dVd/dx]
        //    [Si]              [0   0  dVi/dx]         [ 0 -1  1 ]   [0   0 1/Vca]   [Sca]        [0   0 Vi]                [ 0 -1  1 ]   [0   0  Sca]   [0   0 1/Vca]   [0   0 1/Vc]         [dVi/dx]
        //                  <------------------------------------------------------------->                     <------------------------------------------------------------------------------------->
        //                                      term T1                                                                                             term (dIfortescue)*
        //                                                                                         <-------------------------------------------------------------------------------------------------->
        //                                                                                                                               term T2

        AsymBus asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        LegConnectionType loadConnectionType = asymBus.getLoadConnectionType();
        AsymBusVariableType busVariableType = asymBus.getAsymBusVariableType();

        ComplexMatrix v0V1V2 = AbstractAsymmetricalLoad.getdVvector(bus, busVariableType, derVariable, vo, pho, vd, phd, vi, phi);
        // computation of dV0/dx , dV1/dx, dV2/dx
        Complex dV0 = v0V1V2.getTerm(1, 1);
        Complex dV1 = v0V1V2.getTerm(2, 1);
        Complex dV2 = v0V1V2.getTerm(3, 1);

        // build of voltage vectors
        Vector2D positiveComponent = Fortescue.getCartesianFromPolar(vd, phd);
        Vector2D zeroComponent = Fortescue.getCartesianFromPolar(vo, pho);
        Vector2D negativeComponent = Fortescue.getCartesianFromPolar(vi, phi);

        DenseMatrix mVfortescue = getCartesianMatrix(zeroComponent.getX(), zeroComponent.getY(), positiveComponent.getX(), positiveComponent.getY(), negativeComponent.getX(), negativeComponent.getY(), true); // vector build with cartesian values of complex fortescue voltages
        DenseMatrix mVabc = Fortescue.createMatrix().times(mVfortescue).toDense(); // vector build with cartesian values of complex abc voltages

        // build of Sabc vector
        DenseMatrix mSabc3 = getCartesianMatrix(asymBus.getPa() / 3, asymBus.getQa() / 3, asymBus.getPb() / 3, asymBus.getQb() / 3, asymBus.getPc() / 3, asymBus.getQc() / 3, true);

        // build of 1/Vabc square matrix
        DenseMatrix mInvVabc = getInvVabcSquare(bus, asymBus, mVabc.get(0, 0), mVabc.get(1, 0), mVabc.get(2, 0), mVabc.get(3, 0), mVabc.get(4, 0), mVabc.get(5, 0));
        if (loadConnectionType == LegConnectionType.DELTA && busVariableType == AsymBusVariableType.WYE) {
            if (asymBus.getNbExistingPhases() > 0) {
                throw new IllegalStateException("Delta load with phase disconnection not yet handled at bus : " + bus.getId());
            }
            mInvVabc = getInvVabcSquare(bus, asymBus, mVabc.get(0, 0) - mVabc.get(2, 0), mVabc.get(1, 0) - mVabc.get(3, 0), mVabc.get(2, 0) - mVabc.get(4, 0), mVabc.get(3, 0) - mVabc.get(5, 0), mVabc.get(4, 0) - mVabc.get(0, 0), mVabc.get(5, 0) - mVabc.get(1, 0));
        }

        // build of derivative fortescue voltage square matrix
        DenseMatrix mdVSquare = getCartesianMatrix(dV0.getReal(), dV0.getImaginary(), dV1.getReal(), dV1.getImaginary(), dV2.getReal(), dV2.getImaginary(), false);

        // computation of vector = term T1:
        DenseMatrix m0T1 = mInvVabc.times(mSabc3);

        DenseMatrix m1T1 = m0T1;
        if (loadConnectionType == LegConnectionType.DELTA) {
            m1T1 = complexMatrixP(StepType.STEP_DOWN).getRealCartesianMatrix().times(m0T1);
        }
        DenseMatrix m2T1 = Fortescue.createMatrix().times(m1T1);
        DenseMatrix mT1 = mdVSquare.times(m2T1);

        // build Vfortescue square matrix
        DenseMatrix mSquareVFortescue = getCartesianMatrix(mVfortescue.get(0, 0), mVfortescue.get(1, 0), mVfortescue.get(2, 0), mVfortescue.get(3, 0), mVfortescue.get(4, 0), mVfortescue.get(5, 0), false);

        // build of -1/3.Sabc square matrix
        DenseMatrix mMinusSabc3Square = getCartesianMatrix(-asymBus.getPa() / 3, -asymBus.getQa() / 3, -asymBus.getPb() / 3, -asymBus.getQb() / 3, -asymBus.getPc() / 3, -asymBus.getQc() / 3, false);

        // buils of fortescue derivative vector
        DenseMatrix mdV = getCartesianMatrix(dV0.getReal(), dV0.getImaginary(), dV1.getReal(), dV1.getImaginary(), dV2.getReal(), dV2.getImaginary(), true);

        // computation of vector = term T2:
        DenseMatrix m0T2 = Fortescue.createMatrix().times(mdV);
        DenseMatrix m1T2 = mInvVabc.times(m0T2);
        DenseMatrix m2T2 = mInvVabc.times(m1T2);
        DenseMatrix m3T2 = mMinusSabc3Square.times(m2T2);
        DenseMatrix m4T2 = m3T2;
        if (loadConnectionType == LegConnectionType.DELTA && busVariableType == AsymBusVariableType.DELTA) {
            m4T2 = complexMatrixP(StepType.STEP_DOWN).getRealCartesianMatrix().times(m3T2);
        }
        DenseMatrix mdIFortescueConjugate = Fortescue.createMatrix().times(m4T2);
        DenseMatrix mT2 = mSquareVFortescue.times(mdIFortescueConjugate);

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? mdIFortescueConjugate.get(0, 0) : -mdIFortescueConjugate.get(1, 0); // dIxZero or dIyZero

            case POSITIVE:
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? mdIFortescueConjugate.get(2, 0) : -mdIFortescueConjugate.get(3, 0); // dIxPositive or dIyPositive
                } else {
                    return complexPart == ComplexPart.REAL ? mT1.get(2, 0) + mT2.get(2, 0) : mT1.get(3, 0) + mT2.get(3, 0); // dPpositive or dQpositive
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? mdIFortescueConjugate.get(4, 0) : -mdIFortescueConjugate.get(5, 0); // dIxNegative or dIyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    @Override
    public double eval() {

        double pq;
        pq = pq(element, complexPart, sequenceType,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE));
        return pq;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {

        double deriv = dpq(element, complexPart, sequenceType, variable,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE));
        return deriv;
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

    public static DenseMatrix getInvVabcSquare(LfBus bus, AsymBus asymBus, double vAx, double vAy, double vBx, double vBy, double vCx, double vCy) {
        double epsilon = 0.00000001;

        if (asymBus.getNbExistingPhases() > 0 && asymBus.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            throw new IllegalStateException("Load with delta variables and missing phases not yet handled at bus : " + bus.getId());
        }

        Complex vA = new Complex(vAx, vAy);
        Complex vB = new Complex(vBx, vBy);
        Complex vC = new Complex(vCx, vCy);

        String cantBuildLoad = " is null at bus : " + bus.getId() + " : cannot build load model";

        Complex invVa = new Complex(0., 0.);
        Complex invVb = new Complex(0., 0.);
        Complex invVc = new Complex(0., 0.);

        if (asymBus.isHasPhaseA()) {
            if (vA.abs() < epsilon) {
                throw new IllegalStateException("Va" + cantBuildLoad);
            } else {
                invVa = vA.reciprocal();
            }
        }

        if (asymBus.isHasPhaseB()) {
            if (vB.abs() < epsilon) {
                throw new IllegalStateException("Vb" + cantBuildLoad);
            } else {
                invVb = vB.reciprocal();
            }
        }

        if (asymBus.isHasPhaseC()) {
            if (vC.abs() < epsilon) {
                throw new IllegalStateException("Vc" + cantBuildLoad);
            } else {
                invVc = vC.reciprocal();
            }
        }

        return getCartesianMatrix(invVa.getReal(), invVa.getImaginary(), invVb.getReal(), invVb.getImaginary(), invVc.getReal(), invVc.getImaginary(), false);
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

    // Test
    public static ComplexMatrix complexMatrixP(StepType stepLegConnectionType) {
        ComplexMatrix complexMatrix = ComplexMatrix.complexMatrixIdentity(3);

        // Test artificial invertability with epsilon
        //complexMatrix.set(1, 1, new Complex(1. + EPSILON_LEAK, 0.));
        //complexMatrix.set(3, 3, new Complex(1. - EPSILON_LEAK, 0.));

        Complex mOne = new Complex(-1., 0.);
        if (stepLegConnectionType == StepType.STEP_DOWN) {
            // Step-down configuration
            //       [ 1  0 -1]
            // [P] = [-1  1  0]
            //       [ 0 -1  1]
            complexMatrix.set(1, 3, new Complex(-1., 0.));
            complexMatrix.set(2, 1, mOne);
            complexMatrix.set(3, 2, new Complex(-1., 0.));
        } else {
            // Step-up configuration
            //       [ 1 -1  0 ]
            // [P] = [ 0  1 -1 ]
            //       [-1  0  1 ]
            complexMatrix.set(1, 2, new Complex(-1., 0.));
            complexMatrix.set(2, 3, mOne);
            complexMatrix.set(3, 1, new Complex(-1., 0.));
        }

        return complexMatrix;
    }

}
