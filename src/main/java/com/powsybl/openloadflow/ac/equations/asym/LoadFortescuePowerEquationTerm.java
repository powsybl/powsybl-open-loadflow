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
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfAsymLoad;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.network.extensions.StepType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LoadFortescuePowerEquationTerm extends AbstractAsymmetricalLoadTerm {
    public LoadFortescuePowerEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType, LegConnectionType loadConnectionType) {
        super(bus, variableSet, complexPart, sequenceType, loadConnectionType);
        Complex s0 = new Complex(bus.getLoadTargetP(), bus.getLoadTargetQ());
        Complex sa = s0;
        Complex sb = s0;
        Complex sc = s0;

        LfAsymLoad asymLoad;
        if (loadConnectionType == LegConnectionType.DELTA) {
            asymLoad = asymBus.getLoadDelta0();
        } else {
            asymLoad = asymBus.getLoadWye0();
        }

        this.sabc = getSabc(sa, sb, sc, asymLoad);

    }

    public static double pq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType,
                            ComplexMatrix v0V1V2, LegConnectionType loadConnectionType, ComplexMatrix sabc) {
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

        LfAsymBus asymBus = bus.getAsym();
        AsymBusVariableType busVariableType = asymBus.getAsymBusVariableType();

        // Build of Sabc/3 vector
        DenseMatrix mSabc3 = ComplexMatrix.getMatrixScaled(sabc, 1. / 3.).getRealCartesianMatrix();
        DenseMatrix mVabc = Fortescue.createMatrix().times(v0V1V2.getRealCartesianMatrix()).toDense(); // vector build with cartesian values of complex abc voltages
        ComplexMatrix vabc = ComplexMatrix.getComplexMatrixFromRealCartesian(mVabc);

        // build  1/Vabc square matrix
        DenseMatrix mInvVabc = getSquareInverseFromVector(bus, asymBus, vabc);

        if (loadConnectionType == LegConnectionType.DELTA && busVariableType == AsymBusVariableType.WYE) {
            if (asymBus.getNbMissingPhases() > 0) {
                throw new IllegalStateException("Delta load with phase disconnection not yet handled at bus : " + bus.getId());
            }
            mInvVabc = getSquareInverseFromVector(bus, asymBus, getDeltaVabcFromVabc(vabc));
        }

        // build Vfortescue square matrix
        DenseMatrix mSquareVFortescue = getSquareMatrixFromVector(v0V1V2);

        DenseMatrix m0T0 = mInvVabc.times(mSabc3);
        DenseMatrix m1T0 = m0T0;
        if (loadConnectionType == LegConnectionType.DELTA) {
            m1T0 = complexMatrixP(StepType.STEP_DOWN).getRealCartesianMatrix().times(m0T0);
        }
        ComplexMatrix mIfortescueConjugate = ComplexMatrix.getComplexMatrixFromRealCartesian(Fortescue.createMatrix().times(m1T0));
        ComplexMatrix mSfortescue = ComplexMatrix.getComplexMatrixFromRealCartesian(mSquareVFortescue.times(mIfortescueConjugate.getRealCartesianMatrix())); //  term T0 = Sfortescue

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? mIfortescueConjugate.getTerm(1, 1).getReal() : -mIfortescueConjugate.getTerm(1, 1).getImaginary(); // IxZero or IyZero

            case POSITIVE:
                // check if positive sequence is modelled as P,Q or Ix,Iy
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? mIfortescueConjugate.getTerm(2, 1).getReal() : -mIfortescueConjugate.getTerm(2, 1).getImaginary(); // IxZero or IyZero
                } else {
                    return complexPart == ComplexPart.REAL ? mSfortescue.getTerm(2, 1).getReal() : mSfortescue.getTerm(2, 1).getImaginary(); // Ppositive or Qpositive
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? mIfortescueConjugate.getTerm(3, 1).getReal() : -mIfortescueConjugate.getTerm(3, 1).getImaginary(); // IxNegative or IyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    public static double dpq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType, ComplexMatrix v0V1V2, ComplexMatrix dv0V1V2, LegConnectionType loadConnectionType, ComplexMatrix sabc) {
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

        LfAsymBus asymBus = bus.getAsym();
        AsymBusVariableType busVariableType = asymBus.getAsymBusVariableType();

        // computation of dV0/dx , dV1/dx, dV2/dx
        DenseMatrix mVabc = Fortescue.createMatrix().times(v0V1V2.getRealCartesianMatrix()).toDense(); // vector build with cartesian values of complex abc voltages
        DenseMatrix mSabc3 = ComplexMatrix.getMatrixScaled(sabc, 1. / 3.).getRealCartesianMatrix();
        ComplexMatrix vabc = ComplexMatrix.getComplexMatrixFromRealCartesian(mVabc);

        // build of 1/Vabc square matrix
        DenseMatrix mInvVabc = getSquareInverseFromVector(bus, asymBus, vabc);
        if (loadConnectionType == LegConnectionType.DELTA && busVariableType == AsymBusVariableType.WYE) {
            if (asymBus.getNbMissingPhases() > 0) {
                throw new IllegalStateException("Delta load with phase disconnection not yet handled at bus : " + bus.getId());
            }
            mInvVabc = getSquareInverseFromVector(bus, asymBus, getDeltaVabcFromVabc(vabc));
        }

        // build of derivative fortescue voltage square matrix
        DenseMatrix mdVSquare = getSquareMatrixFromVector(dv0V1V2);

        // computation of vector = term T1:
        DenseMatrix m0T1 = mInvVabc.times(mSabc3);

        DenseMatrix m1T1 = m0T1;
        if (loadConnectionType == LegConnectionType.DELTA) {
            m1T1 = complexMatrixP(StepType.STEP_DOWN).getRealCartesianMatrix().times(m0T1);
        }
        DenseMatrix m2T1 = Fortescue.createMatrix().times(m1T1);
        ComplexMatrix mT1 = ComplexMatrix.getComplexMatrixFromRealCartesian(mdVSquare.times(m2T1));

        // build Vfortescue square matrix
        DenseMatrix mSquareVFortescue = getSquareMatrixFromVector(v0V1V2);

        // build of -1/3.Sabc square matrix
        DenseMatrix mMinusSabc3Square = getSquareMatrixFromVector(ComplexMatrix.getMatrixScaled(sabc, -1. / 3.));

        // computation of vector = term T2:
        DenseMatrix m0T2 = Fortescue.createMatrix().times(dv0V1V2.getRealCartesianMatrix());
        DenseMatrix m1T2 = mInvVabc.times(m0T2);
        DenseMatrix m2T2 = mInvVabc.times(m1T2);
        DenseMatrix m3T2 = mMinusSabc3Square.times(m2T2);
        DenseMatrix m4T2 = m3T2;
        if (loadConnectionType == LegConnectionType.DELTA && busVariableType == AsymBusVariableType.DELTA) {
            m4T2 = complexMatrixP(StepType.STEP_DOWN).getRealCartesianMatrix().times(m3T2);
        }
        ComplexMatrix mdIFortescueConjugate = ComplexMatrix.getComplexMatrixFromRealCartesian(Fortescue.createMatrix().times(m4T2));
        ComplexMatrix mT2 = ComplexMatrix.getComplexMatrixFromRealCartesian(mSquareVFortescue.times(mdIFortescueConjugate.getRealCartesianMatrix()));

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? mdIFortescueConjugate.getTerm(1, 1).getReal() : -mdIFortescueConjugate.getTerm(1, 1).getImaginary(); // dIxZero or dIyZero

            case POSITIVE:
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? mdIFortescueConjugate.getTerm(2, 1).getReal() : -mdIFortescueConjugate.getTerm(2, 1).getImaginary(); // dIxPositive or dIyPositive
                } else {
                    return complexPart == ComplexPart.REAL ? mT1.getTerm(2, 1).getReal() + mT2.getTerm(2, 1).getReal() : mT1.getTerm(2, 1).getImaginary() + mT2.getTerm(2, 1).getImaginary(); // dPpositive or dQpositive
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? mdIFortescueConjugate.getTerm(3, 1).getReal() : -mdIFortescueConjugate.getTerm(3, 1).getImaginary(); // dIxNegative or dIyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    @Override
    public double eval() {
        return pq(element, complexPart, sequenceType, getVfortescue(), loadConnectionType, sabc);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return dpq(element, complexPart, sequenceType, getVfortescue(), getdVfortescue(variable), loadConnectionType, sabc);
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

    public static DenseMatrix getSquareInverseFromVector(LfBus bus, LfAsymBus asymBus, ComplexMatrix m) {
        double epsilon = 0.00000001;

        if (asymBus.getNbMissingPhases() > 0 && asymBus.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            throw new IllegalStateException("Load with delta variables and missing phases not yet handled at bus : " + bus.getId());
        }

        String cantBuildLoad = " is null at bus : " + bus.getId() + " : cannot build load model";

        ComplexMatrix invVabc = new ComplexMatrix(3, 1);

        if (asymBus.isHasPhaseA()) {
            if (m.getTerm(1, 1).abs() < epsilon) {
                throw new IllegalStateException("Va" + cantBuildLoad);
            } else {
                invVabc.set(1, 1, m.getTerm(1, 1).reciprocal());
            }
        }

        if (asymBus.isHasPhaseB()) {
            if (m.getTerm(2, 1).abs() < epsilon) {
                throw new IllegalStateException("Vb" + cantBuildLoad);
            } else {
                invVabc.set(2, 1, m.getTerm(2, 1).reciprocal());
            }
        }

        if (asymBus.isHasPhaseC()) {
            if (m.getTerm(3, 1).abs() < epsilon) {
                throw new IllegalStateException("Vc" + cantBuildLoad);
            } else {
                invVabc.set(3, 1, m.getTerm(3, 1).reciprocal());
            }
        }

        return getSquareMatrixFromVector(invVabc);
    }

    public static ComplexMatrix getDeltaVabcFromVabc(ComplexMatrix vabc) {
        ComplexMatrix vDelta = new ComplexMatrix(3, 1);
        vDelta.set(1, 1, vabc.getTerm(1, 1).add(vabc.getTerm(2, 1).multiply(-1.))); // Vab = va - Vb
        vDelta.set(2, 1, vabc.getTerm(2, 1).add(vabc.getTerm(3, 1).multiply(-1.))); // Vbc = vb - Vc
        vDelta.set(3, 1, vabc.getTerm(3, 1).add(vabc.getTerm(1, 1).multiply(-1.))); // Vca = vc - Va
        return vDelta;
    }

    public static DenseMatrix getSquareMatrixFromVector(ComplexMatrix m1m2m3Vector) {
        // from input complex vector: m = [m1;m2;m3] we build:
        //
        //      [m1x -m1y  0    0    0    0 ]
        //      [m1y  m1x  0    0    0    0 ]
        //      [ 0    0  m2x -m2y   0    0 ]                           [m1  0   0]
        //  m = [ 0    0  m2y  m2x   0    0 ]  equivalent in complex to [ 0  m2  0]
        //      [ 0    0   0    0   m3x -m3y]                           [ 0  0  m3]
        //      [ 0    0   0    0   m3y  m3x]
        //

        ComplexMatrix m;
        m = new ComplexMatrix(3, 3);
        m.set(1, 1, m1m2m3Vector.getTerm(1, 1));
        m.set(2, 2, m1m2m3Vector.getTerm(2, 1));
        m.set(3, 3, m1m2m3Vector.getTerm(3, 1));
        return m.getRealCartesianMatrix();
    }

    public static ComplexMatrix complexMatrixP(StepType stepLegConnectionType) {
        ComplexMatrix complexMatrix = ComplexMatrix.complexMatrixIdentity(3);

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
