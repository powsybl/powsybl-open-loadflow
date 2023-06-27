package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymBus;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

abstract class AbstractAsymmetricalLoad extends AbstractElementEquationTerm<LfBus, AcVariableType, AcEquationType> {

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

    protected final ComplexPart complexPart;

    protected final Fortescue.SequenceType sequenceType;

    private final LegConnectionType loadConnectionType; // how 3 phase loads are connected between each other

    private final AsymBusVariableType busVariableType; // variables available at bus (Wye variables, Va, Vb, Vc or Delta variables : Vab, Vbc and Vca = -Vab - Vbc)

    private final AsymBus asymBus;

    public AbstractAsymmetricalLoad(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType) {
        super(bus);
        Objects.requireNonNull(variableSet);
        this.complexPart = Objects.requireNonNull(complexPart);
        this.sequenceType = Objects.requireNonNull(sequenceType);
        this.asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        this.loadConnectionType = Objects.requireNonNull(asymBus.getLoadConnectionType());
        this.busVariableType = Objects.requireNonNull(asymBus.getAsymBusVariableType());

        int nbPhases = 3 - asymBus.getNbExistingPhases();

        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        phVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI);

        variables.add(vVar);
        variables.add(phVar);

        if (busVariableType == AsymBusVariableType.WYE) {
            if (nbPhases == 3) {
                vVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_NEGATIVE);
                phVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
                variables.add(vVarNegative);
                variables.add(phVarNegative);
            } else {
                vVarNegative = null;
                phVarNegative = null;
            }

            if (nbPhases > 1) {
                vVarZero = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_ZERO);
                phVarZero = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_ZERO);
                variables.add(vVarZero);
                variables.add(phVarZero);
            } else {
                vVarZero = null;
                phVarZero = null;
            }
        } else {
            // delta config
            vVarZero = null;
            phVarZero = null;

            vVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_NEGATIVE);
            phVarNegative = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
            //throw new IllegalStateException("Delta variables not yet handled for Load with ABC bus: " + bus.getId());
        }
    }

    public double ph(Fortescue.SequenceType g) {
        switch (g) {
            case ZERO:
                if (phVarZero == null) {
                    return 0.;
                }
                return sv.get(phVarZero.getRow());

            case POSITIVE:
                return sv.get(phVar.getRow());

            case NEGATIVE:
                if (phVarNegative == null) {
                    return 0.;
                }
                return sv.get(phVarNegative.getRow());

            default:
                throw new IllegalStateException("Unknown Phi variable at bus: " + element.getId());
        }
    }

    public double v(Fortescue.SequenceType g) {
        switch (g) {
            case ZERO:
                if (vVarZero == null) {
                    return 0.;
                }
                return sv.get(vVarZero.getRow());

            case POSITIVE:
                return sv.get(vVar.getRow());

            case NEGATIVE:
                if (vVarNegative == null) {
                    return 0.;
                }
                return sv.get(vVarNegative.getRow());

            default:
                throw new IllegalStateException("Unknown V variable at bus: " + element.getId());
        }
    }

    protected static ComplexMatrix getdVvector(LfBus bus, AsymBusVariableType busVariableType, Variable<AcVariableType> derVariable, double vo, double pho, double vd, double phd, double vi, double phi) {

        // computation of dV0/dx , dV1/dx, dV2/dx
        Complex dV0 = new Complex(0., 0.);
        Complex dV1 = new Complex(0., 0.);
        Complex dV2 = new Complex(0., 0.);

        if (derVariable.getType() == AcVariableType.BUS_V) {
            dV1 = new Complex(Math.cos(phd), Math.sin(phd));
        } else if (derVariable.getType() == AcVariableType.BUS_V_ZERO) {
            dV0 = new Complex(Math.cos(pho), Math.sin(pho));
            if (busVariableType == AsymBusVariableType.DELTA) {
                dV0 = new Complex(0., 0.);
            }
        } else if (derVariable.getType() == AcVariableType.BUS_V_NEGATIVE) {
            dV2 = new Complex(Math.cos(phi), Math.sin(phi));
        } else if (derVariable.getType() == AcVariableType.BUS_PHI) {
            dV1 = new Complex(vd * -Math.sin(phd), vd * Math.cos(phd));
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_ZERO) {
            dV0 = new Complex(vo * -Math.sin(pho), vo * Math.cos(pho));
            if (busVariableType == AsymBusVariableType.DELTA) {
                dV0 = new Complex(0., 0.);
            }
        } else if (derVariable.getType() == AcVariableType.BUS_PHI_NEGATIVE) {
            dV2 = new Complex(vi * -Math.sin(phi), vi * Math.cos(phi));
        } else {
            throw new IllegalStateException("Unknown derivation variable: " + derVariable + " at bus : " + bus.getId());
        }

        ComplexMatrix v0V1V2 = new ComplexMatrix(3, 1);
        v0V1V2.set(1, 1, dV0);
        v0V1V2.set(2, 1, dV1);
        v0V1V2.set(3, 1, dV2);

        return v0V1V2;
    }

    @Override
    public double eval() {
        return 0.;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return 0.;
    }

    @Override
    public String getName() {
        return "ac_pq_fortescue_load";
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
