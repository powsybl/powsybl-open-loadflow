/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AbstractClosedBranchAcFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractAsymmetricalClosedBranchFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    protected AbstractAsymmetricalClosedBranchFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                               boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, sequenceType);
    }

    private static AcVariableType getVoltageMagnitudeType(Fortescue.SequenceType sequenceType) {
        return switch (sequenceType) {
            case POSITIVE -> AcVariableType.BUS_V;
            case NEGATIVE -> AcVariableType.BUS_V_NEGATIVE;
            case ZERO -> AcVariableType.BUS_V_ZERO;
        };
    }

    private static AcVariableType getVoltageAngleType(Fortescue.SequenceType sequenceType) {
        return switch (sequenceType) {
            case POSITIVE -> AcVariableType.BUS_PHI;
            case NEGATIVE -> AcVariableType.BUS_PHI_NEGATIVE;
            case ZERO -> AcVariableType.BUS_PHI_ZERO;
        };
    }

    protected static Variable<AcVariableType> createAsymVar(LfBus bus, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType,
                                                            AcVariableType vType) {
        // if one side is DELTA, asym zero variables shouldn't be called here
        if (sequenceType == Fortescue.SequenceType.POSITIVE) {
            return variableSet.getVariable(bus.getNum(), vType);
        } else {
            return (bus.getAsym().getAsymBusVariableType() == AsymBusVariableType.WYE || sequenceType != Fortescue.SequenceType.ZERO) ? variableSet.getVariable(bus.getNum(), vType) : null;
        }
    }

    @Override
    protected Variable<AcVariableType> createV1Var(LfBus bus1, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        return createAsymVar(bus1, variableSet, sequenceType, getVoltageMagnitudeType(sequenceType));
    }

    @Override
    protected Variable<AcVariableType> createPh1Var(LfBus bus1, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        return createAsymVar(bus1, variableSet, sequenceType, getVoltageAngleType(sequenceType));
    }

    @Override
    protected Variable<AcVariableType> createV2Var(LfBus bus2, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        return createAsymVar(bus2, variableSet, sequenceType, getVoltageMagnitudeType(sequenceType));
    }

    @Override
    protected Variable<AcVariableType> createPh2Var(LfBus bus2, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        return createAsymVar(bus2, variableSet, sequenceType, getVoltageAngleType(sequenceType));
    }

    public static DenseMatrix getCartesianVoltageVector(double v1, double ph1, double v2, double ph2) {
        DenseMatrix mV = new DenseMatrix(4, 1);
        mV.add(0, 0, v1 * FastMath.cos(ph1));
        mV.add(1, 0, v1 * FastMath.sin(ph1));
        mV.add(2, 0, v2 * FastMath.cos(ph2));
        mV.add(3, 0, v2 * FastMath.sin(ph2));

        return mV;
    }

    public DenseMatrix getdVdx(Variable<AcVariableType> variable) {
        double dv1x = 0;
        double dv1y = 0;
        double dv2x = 0;
        double dv2y = 0;
        if (variable.equals(v1Var)) {
            dv1x = FastMath.cos(ph1());
            dv1y = FastMath.sin(ph1());
        } else if (variable.equals(v2Var)) {
            dv2x = FastMath.cos(ph2());
            dv2y = FastMath.sin(ph2());
        } else if (variable.equals(ph1Var)) {
            dv1x = -v1() * FastMath.sin(ph1());
            dv1y = v1() * FastMath.cos(ph1());
        } else if (variable.equals(ph2Var)) {
            dv2x = -v2() * FastMath.sin(ph2());
            dv2y = v2() * FastMath.cos(ph2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }

        DenseMatrix mdV = new DenseMatrix(4, 1);
        mdV.add(0, 0, dv1x);
        mdV.add(1, 0, dv1y);
        mdV.add(2, 0, dv2x);
        mdV.add(3, 0, dv2y);

        return mdV;
    }
}
