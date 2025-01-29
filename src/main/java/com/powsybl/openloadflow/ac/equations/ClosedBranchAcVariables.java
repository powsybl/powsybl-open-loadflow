/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Derivative;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class ClosedBranchAcVariables {

    private final Variable<AcVariableType> v1Var;

    private final Variable<AcVariableType> v2Var;

    private final Variable<AcVariableType> ph1Var;

    private final Variable<AcVariableType> ph2Var;

    private final Variable<AcVariableType> a1Var;

    private final Variable<AcVariableType> r1Var;

    private final List<Variable<AcVariableType>> variables = new ArrayList<>();

    public ClosedBranchAcVariables(int branchNum, int bus1Num, int bus2Num, VariableSet<AcVariableType> variableSet,
                                   boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        Objects.requireNonNull(variableSet);
        Objects.requireNonNull(sequenceType);
        AcVariableType vType = getVoltageMagnitudeType(sequenceType);
        AcVariableType angleType = getVoltageAngleType(sequenceType);
        v1Var = variableSet.getVariable(bus1Num, vType);
        v2Var = variableSet.getVariable(bus2Num, vType);
        ph1Var = variableSet.getVariable(bus1Num, angleType);
        ph2Var = variableSet.getVariable(bus2Num, angleType);
        a1Var = deriveA1 ? variableSet.getVariable(branchNum, AcVariableType.BRANCH_ALPHA1) : null;
        r1Var = deriveR1 ? variableSet.getVariable(branchNum, AcVariableType.BRANCH_RHO1) : null;
        variables.add(v1Var);
        variables.add(v2Var);
        variables.add(ph1Var);
        variables.add(ph2Var);
        if (a1Var != null) {
            variables.add(a1Var);
        }
        if (r1Var != null) {
            variables.add(r1Var);
        }
    }

    public Variable<AcVariableType> getV1Var() {
        return v1Var;
    }

    public Variable<AcVariableType> getV2Var() {
        return v2Var;
    }

    public Variable<AcVariableType> getPh1Var() {
        return ph1Var;
    }

    public Variable<AcVariableType> getPh2Var() {
        return ph2Var;
    }

    public Variable<AcVariableType> getA1Var() {
        return a1Var;
    }

    public Variable<AcVariableType> getR1Var() {
        return r1Var;
    }

    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    public List<Derivative<AcVariableType>> getDerivatives() {
        List<Derivative<AcVariableType>> derivatives = new ArrayList<>(6);
        derivatives.add(new Derivative<>(v1Var, 0));
        derivatives.add(new Derivative<>(v2Var, 1));
        derivatives.add(new Derivative<>(ph1Var, 2));
        derivatives.add(new Derivative<>(ph2Var, 3));
        if (a1Var != null) {
            derivatives.add(new Derivative<>(a1Var, 4));
        }
        if (r1Var != null) {
            derivatives.add(new Derivative<>(r1Var, 5));
        }
        return derivatives;
    }

    public static AcVariableType getVoltageMagnitudeType(Fortescue.SequenceType sequenceType) {
        return switch (sequenceType) {
            case POSITIVE -> AcVariableType.BUS_V;
            case NEGATIVE -> AcVariableType.BUS_V_NEGATIVE;
            case ZERO -> AcVariableType.BUS_V_ZERO;
        };
    }

    public static AcVariableType getVoltageAngleType(Fortescue.SequenceType sequenceType) {
        return switch (sequenceType) {
            case POSITIVE -> AcVariableType.BUS_PHI;
            case NEGATIVE -> AcVariableType.BUS_PHI_NEGATIVE;
            case ZERO -> AcVariableType.BUS_PHI_ZERO;
        };
    }
}
