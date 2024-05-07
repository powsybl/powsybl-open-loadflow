/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfAsymBus;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at gmail.com>}
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
public abstract class AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm extends AbstractAsymmetricalBranchFlowEquationTerm {

    static final String UNKNOWN_VAR = "Unknown variable: ";
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
    protected final TwoSides termSide;
    protected final SequenceType sequenceType;
    protected final AsymBusVariableType variableTypeBus1;
    protected final AsymBusVariableType variableTypeBus2;

    public final LfBranch branch;
    public final LfBus bus1;
    public final LfBus bus2;
    protected final LfAsymBus asymBus1;
    protected final LfAsymBus asymBus2;

    protected AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                                      ComplexPart complexPart, TwoSides termSide, SequenceType sequenceType) {
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

        this.asymBus1 = bus1.getAsym();
        this.asymBus2 = bus2.getAsym();

        // fetching the type of variables connecting bus1
        variableTypeBus1 = getAsymBusVariableType(bus1, asymBus1);
        variableTypeBus2 = getAsymBusVariableType(bus2, asymBus2);

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

    protected static AsymBusVariableType getAsymBusVariableType(LfBus bus, LfAsymBus asymBus) {
        AsymBusVariableType tmpVariableTypeBus = AsymBusVariableType.WYE;
        if (asymBus.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            tmpVariableTypeBus = AsymBusVariableType.DELTA;
            if (asymBus.getNbMissingPhases() > 0) {
                throw new IllegalStateException("Case with missing phase and Delta type variables not yet handled at bus : " + bus.getId());
            }
        }
        return tmpVariableTypeBus;
    }

    protected static SequenceType getSequenceType(Variable<AcVariableType> variable) {
        return switch (variable.getType()) {
            case BUS_V, BUS_PHI -> SequenceType.POSITIVE;
            case BUS_V_NEGATIVE, BUS_PHI_NEGATIVE -> SequenceType.NEGATIVE;
            case BUS_V_ZERO, BUS_PHI_ZERO -> SequenceType.ZERO;
            default -> throw new IllegalStateException("Unknown variable: " + variable);
        };
    }

    protected static boolean isPhase(Variable<AcVariableType> variable) {
        return switch (variable.getType()) {
            case BUS_PHI, BUS_PHI_NEGATIVE, BUS_PHI_ZERO -> true;
            default -> false;
        };
    }

    protected double v(SequenceType g, TwoSides i) {
        if (variableTypeBus1 == AsymBusVariableType.DELTA && g == SequenceType.ZERO) {
            //zero sequence called on a delta side
            return 0.;
        }

        // building missing sequences if one phase is disconnected
        double vZero = 0.;
        double vPositive;
        double vNegative = 0.;

        if (i == TwoSides.ONE) {
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

        return switch (g) {
            case ZERO -> vZero;
            case POSITIVE -> vPositive;
            case NEGATIVE -> vNegative;
        };
    }

    protected double ph(SequenceType g, TwoSides i) {
        if (variableTypeBus1 == AsymBusVariableType.DELTA && i == TwoSides.ONE && g == SequenceType.ZERO) {
            //zero sequence called on a delta side
            return 0.;
        }

        if (variableTypeBus2 == AsymBusVariableType.DELTA && i == TwoSides.TWO && g == SequenceType.ZERO) {
            //zero sequence called on a delta side
            return 0.;
        }

        // buildong missing sequences if one phase is disconnected
        double phZero = 0.;
        double phPositive;
        double phNegative = 0.;

        if (i == TwoSides.ONE) {
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

        return switch (g) {
            case ZERO -> phZero;
            case POSITIVE -> phPositive;
            case NEGATIVE -> phNegative;
        };
    }

    protected double r1() {
        return 1;
    }

    protected double a1() {
        return 0;
    }

    protected double r(TwoSides i) {
        return i == TwoSides.ONE ? r1() : 1.;
    }

    protected double a(TwoSides i) {
        return i == TwoSides.ONE ? a1() : A2;
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    public TwoSides getSide(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var) || variable.equals(v1VarZero) || variable.equals(v1VarNegative)
                || variable.equals(ph1Var) || variable.equals(ph1VarZero) || variable.equals(ph1VarNegative)) {
            return TwoSides.ONE;
        } else if (variable.equals(v2Var) || variable.equals(v2VarZero) || variable.equals(v2VarNegative)
                || variable.equals(ph2Var) || variable.equals(ph2VarZero) || variable.equals(ph2VarNegative)) {
            return TwoSides.TWO;
        } else {
            throw new IllegalStateException(UNKNOWN_VAR);
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
