/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public abstract class AbstractShuntFortescueEquationTerm extends AbstractElementEquationTerm<LfBus, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> vVar;

    protected final Variable<AcVariableType> phVar;

    protected final Fortescue.SequenceType sequenceType;

    protected AbstractShuntFortescueEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        super(bus);
        Objects.requireNonNull(variableSet);
        this.sequenceType = Objects.requireNonNull(sequenceType);
        AcVariableType vType;
        AcVariableType phType;
        switch (sequenceType) {
            case ZERO:
                vType = AcVariableType.BUS_V_ZERO;
                phType = AcVariableType.BUS_PHI_ZERO;
                break;

            case NEGATIVE:
                vType = AcVariableType.BUS_V_NEGATIVE;
                phType = AcVariableType.BUS_PHI_NEGATIVE;
                break;

            default:
                throw new IllegalStateException("Unknown or unadapted sequence type " + sequenceType);
        }
        vVar = variableSet.getVariable(bus.getNum(), vType);
        phVar = variableSet.getVariable(bus.getNum(), phType);
    }

    protected double v() {
        return sv.get(vVar.getRow());
    }

    protected double ph() {
        return sv.get(phVar.getRow());
    }

    protected double b() {
        return sequenceType == Fortescue.SequenceType.ZERO ? element.getAsym().getBzEquiv() : element.getAsym().getBnEquiv();
    }

    protected double g() {
        return sequenceType == Fortescue.SequenceType.ZERO ? element.getAsym().getGzEquiv() : element.getAsym().getGnEquiv();
    }
}
