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
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;
import net.jafama.FastMath;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
public class ShuntFortescueIxEquationTerm extends AbstractShuntFortescueCurrentEquationTerm {

    public ShuntFortescueIxEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, Fortescue.SequenceType sequenceType) {
        super(bus, variableSet, sequenceType);
    }

    /**
     * By definition:
     *  I is the current flowing out of the node in the shunt equipment
     *  I = y.V with y = g+jb
     *  Therefore Ix + jIy = g.Vx - b.Vy + j(g.Vy + b.Vx)
     *  then Ix = g.Vmagnitude.cos(theta) - b.Vmagnitude.sin(theta)
     */
    private static double ix(double v, double phi, double g, double b) {
        return g * v * FastMath.cos(phi) - b * v * FastMath.sin(phi);
    }

    private static double dixdv(double phi, double g, double b) {
        return g * FastMath.cos(phi) - b * FastMath.sin(phi);
    }

    private static double dixdph(double v, double phi, double g, double b) {
        return -g * v * FastMath.sin(phi) - b * v * FastMath.cos(phi);
    }

    @Override
    public double eval() {
        return ix(v(), ph(), g(), b());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dixdv(ph(), g(), b());
        } else if (variable.equals(phVar)) {
            return dixdph(v(), ph(), g(), b());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "ac_ix_fortescue_shunt";
    }
}
