/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.Side;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public abstract class AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm extends AbstractAsymmetricalBranchFlowEquationTerm {

    // positive
    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> v2Var;

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    // negative
    protected final Variable<AcVariableType> v1VarNegative;

    protected final Variable<AcVariableType> v2VarNegative;

    protected final Variable<AcVariableType> ph1VarNegative;

    protected final Variable<AcVariableType> ph2VarNegative;

    // zero
    protected final Variable<AcVariableType> v1VarZero;

    protected final Variable<AcVariableType> v2VarZero;

    protected final Variable<AcVariableType> ph1VarZero;

    protected final Variable<AcVariableType> ph2VarZero;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected final ComplexPart complexPart;
    protected final Side termSide;
    protected final SequenceType sequenceType;
    protected final AsymBusVariableType variableTypeBus1;
    protected final AsymBusVariableType variableTypeBus2;

    public final LfBranch branch;
    public final LfBus bus1;
    public final LfBus bus2;
    protected final LfAsymBus asymBus1;
    protected final LfAsymBus asymBus2;

    protected AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                                      ComplexPart complexPart, Side termSide, SequenceType sequenceType) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        this.complexPart = Objects.requireNonNull(complexPart);
        this.termSide = Objects.requireNonNull(termSide);
        this.sequenceType = Objects.requireNonNull(sequenceType);

        this.branch = branch;
        this.bus1 = bus1;
        this.bus2 = bus2;

        // fetching the type of variables connecting bus1
        AsymBusVariableType tmpVariableTypeBus1 = AsymBusVariableType.WYE;
        asymBus1 = bus1.getAsym();
        if (asymBus1.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            tmpVariableTypeBus1 = AsymBusVariableType.DELTA;
            if (asymBus1.getNbMissingPhases() > 0) {
                throw new IllegalStateException("Case with missing phase and Delta type variables not yet handled at bus : " + bus1.getId());
            }
        }
        variableTypeBus1 = tmpVariableTypeBus1;

        // fetching the type of variables connecting bus2
        AsymBusVariableType tmpVariableTypeBus2 = AsymBusVariableType.WYE;
        asymBus2 = bus2.getAsym();
        if (asymBus2.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            tmpVariableTypeBus2 = AsymBusVariableType.DELTA;
            if (asymBus2.getNbMissingPhases() > 0) {
                throw new IllegalStateException("Case with missing phase and Delta type variables not yet handled at bus : " + bus2.getId());
            }
        }
        variableTypeBus2 = tmpVariableTypeBus2;

        int nbPhases1 = asymBus1.getNbExistingPhases();
        int nbPhases2 = asymBus2.getNbExistingPhases();

        v1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V);
        v2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V);
        ph1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI);
        variables.add(v1Var);
        variables.add(v2Var);
        variables.add(ph1Var);
        variables.add(ph2Var);

        if (variableTypeBus1 == AsymBusVariableType.DELTA) {
            v1VarNegative = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V_NEGATIVE);
            ph1VarNegative = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
            variables.add(v1VarNegative);
            variables.add(ph1VarNegative);
            v1VarZero = null;
            ph1VarZero = null;
            // missing phases not yet handled in delta config
        } else {
            // Wye config
            if (nbPhases1 == 3) {
                v1VarNegative = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V_NEGATIVE);
                ph1VarNegative = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
                variables.add(v1VarNegative);
                variables.add(ph1VarNegative);
            } else {
                v1VarNegative = null;
                ph1VarNegative = null;
            }
            if (nbPhases1 > 1) {
                v1VarZero = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_V_ZERO);
                ph1VarZero = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI_ZERO);
                variables.add(v1VarZero);
                variables.add(ph1VarZero);
            } else {
                v1VarZero = null;
                ph1VarZero = null;
            }

        }

        if (variableTypeBus2 == AsymBusVariableType.DELTA) {
            v2VarNegative = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V_NEGATIVE);
            ph2VarNegative = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
            variables.add(v2VarNegative);
            variables.add(ph2VarNegative);
            v2VarZero = null;
            ph2VarZero = null;
            // missing phases not yet handled in delta config
        } else {
            // Wye config
            if (nbPhases2 == 3) {
                v2VarNegative = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V_NEGATIVE);
                ph2VarNegative = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI_NEGATIVE);
                variables.add(v2VarNegative);
                variables.add(ph2VarNegative);
            } else {
                v2VarNegative = null;
                ph2VarNegative = null;
            }

            if (nbPhases2 > 1) {
                v2VarZero = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_V_ZERO);
                ph2VarZero = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI_ZERO);
                variables.add(v2VarZero);
                variables.add(ph2VarZero);
            } else {
                v2VarZero = null;
                ph2VarZero = null;
            }

        }
    }

    protected static SequenceType getSequenceType(Variable<AcVariableType> variable) {
        switch (variable.getType()) {
            case BUS_V:
            case BUS_PHI:
                return SequenceType.POSITIVE;

            case BUS_V_NEGATIVE:
            case BUS_PHI_NEGATIVE:
                return SequenceType.NEGATIVE;

            case BUS_V_ZERO:
            case BUS_PHI_ZERO:
                return SequenceType.ZERO;

            default:
                throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    protected static boolean isPhase(Variable<AcVariableType> variable) {
        switch (variable.getType()) {
            case BUS_PHI:
            case BUS_PHI_NEGATIVE:
            case BUS_PHI_ZERO:
                return true;
            default:
                return false;
        }
    }

    protected double v(SequenceType g, Side i) {

        if (variableTypeBus1 == AsymBusVariableType.DELTA && i == Side.ONE && g == SequenceType.ZERO) {
            //zero sequence called on a delta side
            return 0.;
        }

        if (variableTypeBus2 == AsymBusVariableType.DELTA && i == Side.TWO && g == SequenceType.ZERO) {
            //zero sequence called on a delta side
            return 0.;
        }

        // buildong missing sequences if one phase is disconnected
        double vZero = 0.;
        double vPositive;
        double vNegative = 0.;

        if (i == Side.ONE) {
            vPositive = sv.get(v1Var.getRow());
            if (v1VarZero != null) {
                vZero = sv.get(v1VarZero.getRow());
            }
            if (v1VarNegative != null) {
                vNegative = sv.get(v1VarNegative.getRow());
            }
        } else {
            vPositive = sv.get(v2Var.getRow());
            if (v2VarZero != null) {
                vZero = sv.get(v2VarZero.getRow());
            }
            if (v2VarNegative != null) {
                vNegative = sv.get(v2VarNegative.getRow());
            }
        }

        switch (g) {
            case ZERO:
                return vZero;

            case POSITIVE:
                return vPositive;

            case NEGATIVE:
                return vNegative;

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    protected double ph(SequenceType g, Side i) {

        if (variableTypeBus1 == AsymBusVariableType.DELTA && i == Side.ONE && g == SequenceType.ZERO) {
            //zero sequence called on a delta side
            return 0.;
        }

        if (variableTypeBus2 == AsymBusVariableType.DELTA && i == Side.TWO && g == SequenceType.ZERO) {
            //zero sequence called on a delta side
            return 0.;
        }

        // buildong missing sequences if one phase is disconnected
        double phZero = 0.;
        double phPositive;
        double phNegative = 0.;

        if (i == Side.ONE) {
            phPositive = sv.get(ph1Var.getRow());
            if (v1VarZero != null) {
                phZero = sv.get(ph1VarZero.getRow());
            }
            if (v1VarNegative != null) {
                phNegative = sv.get(ph1VarNegative.getRow());
            }

        } else {
            phPositive = sv.get(ph2Var.getRow());
            if (v2VarZero != null) {
                phZero = sv.get(ph2VarZero.getRow());
            }
            if (v2VarNegative != null) {
                phNegative = sv.get(ph2VarNegative.getRow());
            }
        }

        switch (g) {
            case ZERO:
                return phZero;

            case POSITIVE:
                return phPositive;

            case NEGATIVE:
                return phNegative;

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    protected double r1() {
        return 1;
    }

    protected double a1() {
        return 0;
    }

    protected double r(Side i) {
        return i == Side.ONE ? r1() : 1.;
    }

    protected double a(Side i) {
        return i == Side.ONE ? a1() : A2;
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    public Side getSide(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var) || variable.equals(v1VarZero) || variable.equals(v1VarNegative)
                || variable.equals(ph1Var) || variable.equals(ph1VarZero) || variable.equals(ph1VarNegative)) {
            return Side.ONE;
        } else if (variable.equals(v2Var) || variable.equals(v2VarZero) || variable.equals(v2VarNegative)
                || variable.equals(ph2Var) || variable.equals(ph2VarZero) || variable.equals(ph2VarNegative)) {
            return Side.TWO;
        } else {
            throw new IllegalStateException("Unknown variable type");
        }
    }

    public int getNbPhases() {
        int nbPhases = 0;
        if (asymBus1.isHasPhaseA() && asymBus2.isHasPhaseA()) {
            nbPhases++;
        }
        if (asymBus1.isHasPhaseB() && asymBus2.isHasPhaseB()) {
            nbPhases++;
        }
        if (asymBus1.isHasPhaseC() && asymBus2.isHasPhaseC()) {
            nbPhases++;
        }
        return nbPhases;
    }
}
