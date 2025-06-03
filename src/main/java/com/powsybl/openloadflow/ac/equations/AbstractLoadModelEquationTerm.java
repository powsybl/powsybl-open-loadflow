/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfLoad;
import com.powsybl.openloadflow.network.LfLoadModel;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractLoadModelEquationTerm extends AbstractElementEquationTerm<LfBus, AcVariableType, AcEquationType> {

    protected final LfLoadModel loadModel;

    protected final LfLoad load;

    private final Variable<AcVariableType> vVar;

    private final List<Variable<AcVariableType>> variables;

    protected AbstractLoadModelEquationTerm(LfBus bus, LfLoadModel loadModel, LfLoad load, VariableSet<AcVariableType> variableSet) {
        super(bus);
        this.loadModel = Objects.requireNonNull(loadModel);
        this.load = Objects.requireNonNull(load);
        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        variables = List.of(vVar);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    private double v() {
        return sv.get(vVar.getRow());
    }

    protected abstract Collection<LfLoadModel.ExpTerm> getExpTerms();

    protected abstract double getTarget();

    @Override
    public double eval() {
        double value = 0;
        double v = v();
        for (LfLoadModel.ExpTerm expTerm : getExpTerms()) {
            if (expTerm.n() != 0) {
                value += expTerm.c() * Math.pow(v, expTerm.n());
            }
        }
        return value * getTarget();
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        double value = 0;
        double v = v();
        for (LfLoadModel.ExpTerm expTerm : getExpTerms()) {
            if (expTerm.n() != 0) {
                value += expTerm.c() * expTerm.n() * Math.pow(v, expTerm.n() - 1);
            }
        }
        return value * getTarget();
    }
}
