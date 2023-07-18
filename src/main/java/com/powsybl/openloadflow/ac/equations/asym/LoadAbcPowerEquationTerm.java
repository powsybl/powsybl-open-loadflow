package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;

import java.util.Objects;

public class LoadAbcPowerEquationTerm extends AsymmetricalLoadTerm {

    public static final double EPSILON = 0.000000001;

    public LoadAbcPowerEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType, LegConnectionType loadConnectionType) {
        super(bus, variableSet, complexPart, sequenceType, loadConnectionType);
        Objects.requireNonNull(variableSet);

        setSabc();
    }

    public static double pq(LfBus bus, ComplexPart complexPart, Fortescue.SequenceType sequenceType, ComplexMatrix v0V1V2,
                            Variable<AcVariableType> vVarZero, Variable<AcVariableType> vVarNegative, ComplexMatrix sabc, LegConnectionType loadConnectionType, boolean computeDerivative, ComplexMatrix dv0V1V2) {
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

        // For derivation we have:
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

        if (loadConnectionType == LegConnectionType.DELTA) {
            throw new IllegalStateException("ABC load with delta load connection not yet handled at bus " + bus.getId());
        }

        if (busVariableType != AsymBusVariableType.WYE) {
            throw new IllegalStateException("ABC load with delta variables  not yet handled at bus " + bus.getId());
        }

        Complex sA = sabc.getTerm(1, 1);
        Complex sB = sabc.getTerm(2, 1);
        Complex sC = sabc.getTerm(3, 1);

        Complex vPositiveComplex = v0V1V2.getTerm(2, 1);
        Complex invVpositive = vPositiveComplex.reciprocal();
        Complex invVnegative = getVfortescueInverse(bus, vVarNegative, v0V1V2.getTerm(3, 1));
        Complex invVzero = getVfortescueInverse(bus, vVarZero, v0V1V2.getTerm(1, 1));

        boolean hasPhaseA = asymBus.isHasPhaseA();
        boolean hasPhaseB = asymBus.isHasPhaseB();
        boolean hasPhaseC = asymBus.isHasPhaseC();

        Complex iZero = new Complex(0., 0.);
        Complex iPosi;
        Complex iNega = new Complex(0., 0.);

        if (computeDerivative) {
            // if this boolean is true, we use the derivative of V to compute the derivative of I, the rest of the formula is unchanged
            // computation of dV0/dx , dV1/dx, dV2/dx
            Complex dV0 = dv0V1V2.getTerm(1, 1);
            Complex dV1 = dv0V1V2.getTerm(2, 1);
            Complex dV2 = dv0V1V2.getTerm(3, 1);

            // computation of d(1/V0)/dx , d(1/V1)/dx, d(1/V2)/dx
            invVzero = invVzero.multiply(invVzero).multiply(dV0).multiply(-1.);
            invVpositive = invVpositive.multiply(invVpositive).multiply(dV1).multiply(-1.);
            invVnegative = invVnegative.multiply(invVnegative).multiply(dV2).multiply(-1.);
        }

        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            iZero = (invVzero.multiply(sA)).conjugate();
            iPosi = (invVpositive.multiply(sB)).conjugate();
            iNega = (invVnegative.multiply(sC)).conjugate();
        } else if (hasPhaseB && hasPhaseC) {
            iZero = (invVzero.multiply(sB)).conjugate();
            iPosi = (invVpositive.multiply(sC)).conjugate();
        } else if (hasPhaseA && hasPhaseC) {
            iZero = (invVzero.multiply(sA)).conjugate();
            iPosi = (invVnegative.multiply(sC)).conjugate();
        } else if (hasPhaseA && hasPhaseB) {
            iZero = (invVzero.multiply(sA)).conjugate();
            iPosi = (invVpositive.multiply(sB)).conjugate();
        } else if (hasPhaseA) {
            iPosi = (invVpositive.multiply(sA)).conjugate();
        } else if (hasPhaseB) {
            iPosi = (invVpositive.multiply(sB)).conjugate();
        } else if (hasPhaseC) {
            iPosi = (invVpositive.multiply(sC)).conjugate();
        } else {
            throw new IllegalStateException("Phase config not handled at bus : " + bus.getId());
        }

        return getIvalue(bus, complexPart, sequenceType, iZero, iPosi, iNega);
    }

    public static Complex getVfortescueInverse(LfBus bus, Variable<AcVariableType> vVar, Complex vComplex) {
        Complex invV = new Complex(0., 0.);
        if (vVar != null) {
            if (vComplex.abs() > EPSILON) {
                invV = vComplex.reciprocal();
            } else {
                throw new IllegalStateException("ABC load could not be computed because of zero voltage of Zero sequence value at bus : " + bus.getId());
            }
        }
        return invV;
    }

    @Override
    public double eval() {
        return pq(element, complexPart, sequenceType, getVfortescue(), vVarZero, vVarNegative, sabc, loadConnectionType, false, null);

    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return pq(element, complexPart, sequenceType, getVfortescue(), vVarZero, vVarNegative, sabc, loadConnectionType, true, getdVfortescue(variable));
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

}
