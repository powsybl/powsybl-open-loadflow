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
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.Side;
import com.powsybl.openloadflow.network.extensions.AsymBus;
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
    protected final TwoSides side;
    protected final SequenceType sequenceType;
    protected final AsymBusVariableType variableTypeBus1;
    protected final AsymBusVariableType variableTypeBus2;

    protected final boolean hasPhaseA1;
    protected final boolean hasPhaseB1;
    protected final boolean hasPhaseC1;

    protected final boolean hasPhaseA2;
    protected final boolean hasPhaseB2;
    protected final boolean hasPhaseC2;

    public final LfBranch branch;
    public final LfBus bus1;
    public final LfBus bus2;

    protected AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                                      ComplexPart complexPart, TwoSides side, SequenceType sequenceType) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);
        this.complexPart = Objects.requireNonNull(complexPart);
        this.side = Objects.requireNonNull(side);
        this.sequenceType = Objects.requireNonNull(sequenceType);

        this.branch = branch;
        this.bus1 = bus1;
        this.bus2 = bus2;

        // fetching the type of variables connecting bus1
        AsymBusVariableType tmpVariableTypeBus1 = AsymBusVariableType.WYE;
        AsymBus asymBus1 = (AsymBus) bus1.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);

        if (asymBus1.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            tmpVariableTypeBus1 = AsymBusVariableType.DELTA;
            if (asymBus1.getNbExistingPhases() > 0) {
                throw new IllegalStateException("Case with missing phase and Delta type variables not yet handled at bus : " + bus1.getId());
            }
        }
        variableTypeBus1 = tmpVariableTypeBus1;

        // fetching the type of variables connecting bus2
        AsymBusVariableType tmpVariableTypeBus2 = AsymBusVariableType.WYE;
        AsymBus asymBus2 = (AsymBus) bus2.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus2.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            tmpVariableTypeBus2 = AsymBusVariableType.DELTA;
            if (asymBus2.getNbExistingPhases() > 0) {
                throw new IllegalStateException("Case with missing phase and Delta type variables not yet handled at bus : " + bus2.getId());
            }
        }
        variableTypeBus2 = tmpVariableTypeBus2;

        hasPhaseA1 = asymBus1.isHasPhaseA();
        hasPhaseB1 = asymBus1.isHasPhaseB();
        hasPhaseC1 = asymBus1.isHasPhaseC();

        hasPhaseA2 = asymBus2.isHasPhaseA();
        hasPhaseB2 = asymBus2.isHasPhaseB();
        hasPhaseC2 = asymBus2.isHasPhaseC();

        int nbPhases1 = 0;
        if (hasPhaseA1) {
            nbPhases1++;
        }
        if (hasPhaseB1) {
            nbPhases1++;
        }
        if (hasPhaseC1) {
            nbPhases1++;
        }

        int nbPhases2 = 0;
        if (hasPhaseA2) {
            nbPhases2++;
        }
        if (hasPhaseB2) {
            nbPhases2++;
        }
        if (hasPhaseC2) {
            nbPhases2++;
        }

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
        double vPositive = 0.;
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
        double phPositive = 0.;
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
            throw new IllegalStateException("Unknown variable type");
        }
    }

    public int getNbPhases() {
        int nbPhases = 0;
        if (hasPhaseA1 && hasPhaseA2) {
            nbPhases++;
        }
        if (hasPhaseB1 && hasPhaseB2) {
            nbPhases++;
        }
        if (hasPhaseC1 && hasPhaseC2) {
            nbPhases++;
        }
        return nbPhases;
    }
}
