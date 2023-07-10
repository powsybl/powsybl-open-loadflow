package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfAsymLoad;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import java.util.Objects;

public class LoadAbcPowerEquationTerm extends AbstractAsymmetricalLoadTerm {

    public static final double EPSILON = 0.000000001;

    public LoadAbcPowerEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType, LegConnectionType loadConnectionType) {
        super(bus, variableSet, complexPart, sequenceType, loadConnectionType);
        Objects.requireNonNull(variableSet);

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
                            double vZero, double phZero, double vPositive, double phPositive, double vNegative, double phNegative,
                            Variable<AcVariableType> vVarZero, Variable<AcVariableType> vVarNegative, ComplexMatrix sabc, LegConnectionType loadConnectionType) {

        // We suppose that input for power load is constant S
        // For each phase we have :
        // S is also S = I* * V  which gives I* = S / V
        //
        // Case of a Wye load connected to a Wye-variables bus :
        //
        // [Ia*]    [Sa/Va]
        // [Ib*] =  [Sb/Vb]
        // [Ic*]    [Sc/Vc]
        //
        // Case of a Delta load connected to a Delta Bus
        //
        // Not yet handled
        //
        // Case of a Delta load connected to a Wye-variables Bus
        //
        // Not yet handled
        //

        LfAsymBus asymBus = bus.getAsym();
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        if (loadConnectionType == LegConnectionType.DELTA) {
            throw new IllegalStateException("ABC Power load with delta load connection not yet handled at bus " + bus.getId());
        }

        AsymBusVariableType busVariableType = asymBus.getAsymBusVariableType();
        if (busVariableType != AsymBusVariableType.WYE) {
            throw new IllegalStateException("ABC Power load with delta variables  not yet handled at bus " + bus.getId());
        }

        Complex sA = sabc.getTerm(1, 1);
        Complex sB = sabc.getTerm(2, 1);
        Complex sC = sabc.getTerm(3, 1);

        Vector2D positiveSequence = Fortescue.getCartesianFromPolar(vPositive, phPositive);
        Vector2D zeroSequence = Fortescue.getCartesianFromPolar(vZero, phZero);
        Vector2D negativeSequence = Fortescue.getCartesianFromPolar(vNegative, phNegative);

        Complex vPositiveComplex = new Complex(positiveSequence.getX(), positiveSequence.getY());
        Complex invVpositive = vPositiveComplex.reciprocal();

        Complex invVnegative = new Complex(0., 0.);
        if (vVarNegative != null) {
            Complex vNegativeComplex = new Complex(negativeSequence.getX(), negativeSequence.getY());
            if (vNegativeComplex.abs() > EPSILON) {
                invVnegative = vNegativeComplex.reciprocal();
            } else {
                throw new IllegalStateException("ABC load could not be computed because of zero voltage of Negative sequence  value at bus : " + bus.getId());
            }
        }

        Complex invVzero = new Complex(0., 0.);
        if (vVarZero != null) {
            Complex vZeroComplex = new Complex(zeroSequence.getX(), zeroSequence.getY());
            if (vZeroComplex.abs() > EPSILON) {
                invVzero = vZeroComplex.reciprocal();
            } else {
                throw new IllegalStateException("ABC load could not be computed because of zero voltage of Zero sequence value at bus : " + bus.getId());
            }
        }

        boolean hasPhaseA = asymBus.isHasPhaseA();
        boolean hasPhaseB = asymBus.isHasPhaseB();
        boolean hasPhaseC = asymBus.isHasPhaseC();

        Complex iZero = new Complex(0., 0.);
        Complex iPosi;
        Complex iNega = new Complex(0., 0.);
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            iZero = (invVzero.multiply(sA)).conjugate();
            iPosi = (invVpositive.multiply(sB)).conjugate();
            iNega = (invVnegative.multiply(sC)).conjugate();
        } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
            iZero = (invVzero.multiply(sB)).conjugate();
            iPosi = (invVpositive.multiply(sC)).conjugate();
        } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
            iZero = (invVzero.multiply(sA)).conjugate();
            iPosi = (invVpositive.multiply(sC)).conjugate();
        } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
            iZero = (invVzero.multiply(sA)).conjugate();
            iPosi = (invVpositive.multiply(sB)).conjugate();
        } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
            iPosi = (invVpositive.multiply(sA)).conjugate();
        } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
            iPosi = (invVpositive.multiply(sB)).conjugate();
        } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
            iPosi = (invVpositive.multiply(sC)).conjugate();
        } else {
            throw new IllegalStateException("Phase config not handled at bus : " + bus.getId());
        }

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? iZero.getReal() : iZero.getImaginary(); // IxZero or IyZero

            case POSITIVE:
                // check if positive sequence is modelled as P,Q or Ix,Iy
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? iPosi.getReal() : iPosi.getImaginary(); // IxZero or IyZero
                } else {
                    throw new IllegalStateException("Load ABC as Power not yet handled : " + bus.getId());
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? iNega.getReal() : iNega.getImaginary(); // IxNegative or IyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    public static double dpq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType, Variable<AcVariableType> derVariable, double vo, double pho, double vd, double phd, double vi, double phi,
                             Variable<AcVariableType> vVarZero, Variable<AcVariableType> vVarNegative, ComplexMatrix sabc, LegConnectionType loadConnectionType) {
        // We suppose that input for power load is constant S
        // For each phase we have :
        // S is also S = I* * V  which gives I* = S / V
        //
        // Case of a Wye load connected to a Wye-variables bus :
        //
        // [dIa*]    [-Sa/Va² * dVa]
        // [dIb*] =  [-Sb/Vb² * dVb]
        // [dIc*]    [-Sc/Vc² * dVc]
        //
        // Case of a Delta load connected to a Delta Bus
        //
        // Not yet handled
        //
        // Case of a Delta load connected to a Wye-variables Bus
        //
        // Not yet handled
        //                                                                                                                        term T2

        LfAsymBus asymBus = bus.getAsym();
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + bus.getId());
        }

        AsymBusVariableType busVariableType = asymBus.getAsymBusVariableType();

        ComplexMatrix dv0V1V2 = AbstractAsymmetricalLoadTerm.getdVvector(bus, busVariableType, derVariable, vo, pho, vd, phd, vi, phi);
        // computation of dV0/dx , dV1/dx, dV2/dx
        Complex dV0 = dv0V1V2.getTerm(1, 1);
        Complex dV1 = dv0V1V2.getTerm(2, 1);
        Complex dV2 = dv0V1V2.getTerm(3, 1);

        if (loadConnectionType == LegConnectionType.DELTA) {
            throw new IllegalStateException("ABC load with delta load connection not yet handled at bus " + bus.getId());
        }

        if (busVariableType != AsymBusVariableType.WYE) {
            throw new IllegalStateException("ABC load with delta variables  not yet handled at bus " + bus.getId());
        }

        Complex sA = sabc.getTerm(1, 1);
        Complex sB = sabc.getTerm(2, 1);
        Complex sC = sabc.getTerm(3, 1);

        Vector2D positiveSequence = Fortescue.getCartesianFromPolar(vd, phd);
        Vector2D zeroSequence = Fortescue.getCartesianFromPolar(vo, pho);
        Vector2D negativeSequence = Fortescue.getCartesianFromPolar(vi, phi);

        Complex vPositiveComplex = new Complex(positiveSequence.getX(), positiveSequence.getY());
        Complex invVpositive = vPositiveComplex.reciprocal();

        Complex invVnegative = new Complex(0., 0.);
        if (vVarNegative != null) {
            Complex vNegativeComplex = new Complex(negativeSequence.getX(), negativeSequence.getY());
            if (vNegativeComplex.abs() > EPSILON) {
                invVnegative = vNegativeComplex.reciprocal();
            } else {
                throw new IllegalStateException("ABC load could not be computed because of zero voltage value of Negative sequence at bus : " + bus.getId());
            }
        }

        Complex invVzero = new Complex(0., 0.);
        if (vVarZero != null) {
            Complex vZeroComplex = new Complex(zeroSequence.getX(), zeroSequence.getY());
            if (vZeroComplex.abs() > EPSILON) {
                invVzero = vZeroComplex.reciprocal();
            } else {
                throw new IllegalStateException("ABC load could not be computed because of zero voltage value of Zero sequence at bus : " + bus.getId());
            }
        }

        boolean hasPhaseA = asymBus.isHasPhaseA();
        boolean hasPhaseB = asymBus.isHasPhaseB();
        boolean hasPhaseC = asymBus.isHasPhaseC();

        Complex diZero = new Complex(0., 0.);
        Complex diPosi;
        Complex diNega = new Complex(0., 0.);

        Complex dinvVzero = invVzero.multiply(invVzero).multiply(dV0).multiply(-1.);
        Complex dinvVPosi = invVpositive.multiply(invVpositive).multiply(dV1).multiply(-1.);
        Complex dinvVNega = invVnegative.multiply(invVnegative).multiply(dV2).multiply(-1.);

        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            diZero = (dinvVzero.multiply(sA)).conjugate();
            diPosi = (dinvVPosi.multiply(sB)).conjugate();
            diNega = (dinvVNega.multiply(sC)).conjugate();
        } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
            diZero = (dinvVzero.multiply(sB)).conjugate();
            diPosi = (dinvVPosi.multiply(sC)).conjugate();
        } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
            diZero = (dinvVzero.multiply(sA)).conjugate();
            diPosi = (dinvVPosi.multiply(sC)).conjugate();
        } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
            diZero = (dinvVzero.multiply(sA)).conjugate();
            diPosi = (dinvVPosi.multiply(sB)).conjugate();
        } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
            diPosi = (dinvVPosi.multiply(sA)).conjugate();
        } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
            diPosi = (dinvVPosi.multiply(sB)).conjugate();
        } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
            diPosi = (dinvVPosi.multiply(sC)).conjugate();
        } else {
            throw new IllegalStateException("Phase config not handled at bus : " + bus.getId());
        }

        switch (sequenceType) {
            case ZERO:
                return complexPart == ComplexPart.REAL ? diZero.getReal() : diZero.getImaginary(); // IxZero or IyZero

            case POSITIVE:
                // check if positive sequence is modelled as P,Q or Ix,Iy
                if (asymBus.isPositiveSequenceAsCurrent()) {
                    return complexPart == ComplexPart.REAL ? diPosi.getReal() : diPosi.getImaginary(); // IxPositive or IyPositive
                } else {
                    throw new IllegalStateException("positive sequence as Power not yet implemented in ABC load : " + bus.getId());
                }

            case NEGATIVE:
                return complexPart == ComplexPart.REAL ? diNega.getReal() : diNega.getImaginary(); // IxNegative or IyNegative

            default:
                throw new IllegalStateException("Unknown sequence at bus : " + bus.getId());
        }
    }

    @Override
    public double eval() {
        return pq(element, complexPart, sequenceType,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE), vVarZero, vVarNegative, sabc, loadConnectionType);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return dpq(element, complexPart, sequenceType, variable,
                v(Fortescue.SequenceType.ZERO), ph(Fortescue.SequenceType.ZERO),
                v(Fortescue.SequenceType.POSITIVE), ph(Fortescue.SequenceType.POSITIVE),
                v(Fortescue.SequenceType.NEGATIVE), ph(Fortescue.SequenceType.NEGATIVE), vVarZero, vVarNegative, sabc, loadConnectionType);
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

}
