/*
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.network.LfHvdc;

import com.powsybl.iidm.network.util.HvdcUtils;

import java.util.List;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public abstract class AbstractHvdcAcEmulationFlowEquationTerm extends AbstractElementEquationTerm<LfHvdc, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> ph1Var;

    protected final Variable<AcVariableType> ph2Var;

    protected final List<Variable<AcVariableType>> variables;

    protected final double k;

    protected final double p0;

    protected final double r;

    protected final double lossFactor1;

    protected final double lossFactor2;

    protected final double pMaxFromCS1toCS2;

    protected final double pMaxFromCS2toCS1;

    protected double frozenP = Double.NaN;

    protected boolean frozen = false;

    protected AbstractHvdcAcEmulationFlowEquationTerm(LfHvdc hvdc, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet) {
        super(hvdc);
        ph1Var = variableSet.getVariable(bus1.getNum(), AcVariableType.BUS_PHI);
        ph2Var = variableSet.getVariable(bus2.getNum(), AcVariableType.BUS_PHI);
        variables = List.of(ph1Var, ph2Var);
        k = hvdc.getDroop() * 180 / Math.PI;
        p0 = hvdc.getP0();
        r = hvdc.getR();
        lossFactor1 = hvdc.getConverterStation1().getLossFactor() / 100;
        lossFactor2 = hvdc.getConverterStation2().getLossFactor() / 100;
        pMaxFromCS1toCS2 = hvdc.getPMaxFromCS1toCS2();
        pMaxFromCS2toCS1 = hvdc.getPMaxFromCS2toCS1();
    }

    protected double rawP(double ph1, double ph2) {
        return p0 + k * (ph1 - ph2);
    }

    protected double boundedP(double rawP) {
        // If there is a maximal active power
        // it is applied at the entry of the controller VSC station
        // on the AC side of the network.
        if (rawP >= 0) {
            return Math.min(rawP, pMaxFromCS1toCS2);
        } else {
            return Math.max(rawP, -pMaxFromCS2toCS1);
        }
    }

    protected double ph1() {
        return sv.get(ph1Var.getRow());
    }

    protected double ph2() {
        return sv.get(ph2Var.getRow());
    }

    protected double getVscLossMultiplier() {
        return (1 - lossFactor1) * (1 - lossFactor2);
    }

    protected double getAbsActivePowerWithLosses(double boundedP, double lossController, double lossNonController) {
        double lineInputPower = (1 - lossController) * Math.abs(boundedP);
        return (1 - lossNonController) * (lineInputPower - getHvdcLineLosses(lineInputPower, r));
    }

    protected static double getHvdcLineLosses(double lineInputPower, double r) {
        return HvdcUtils.getHvdcLineLosses(lineInputPower, 1, r);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    public double freezeFromCurrentAngles() {
        frozen = false; // Make sure P is computed according to angles
        frozenP = isActive() ? eval() : Double.NaN;
        frozen = true;
        return frozenP;
    }

    public void unFreeze() {
        frozen = false;
        frozenP = Double.NaN;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Return the mismatch in angle between a frozen HVDC link and the angle that would be
     * required to get the frozen P
     * @return
     */
    public double getAngleMismatch() {
        if (!frozen || !isActive()) {
            return 0;
        }
        // Temprary unfreeze to get the theorical values and compute the mismatch
        frozen = false;
        double unfrozenP = eval();
        double dpdphi = der(ph1Var);
        // Return to frozen state
        frozen = true;
        if (dpdphi == 0) {
            // In this case the angle is out of operation range
            // We return 0 if frozenP is at PMax (or more) and  otherwise an impossibly large angle
            return Math.abs(frozenP) >= Math.abs(unfrozenP) ? 0 : Math.PI * Math.signum(ph1() - ph2());
        } else {
            // Otherwise return the calculated mismatch
            return Math.abs((frozenP - unfrozenP) / dpdphi);
        }
    }

    public abstract double updateFrozenValue(double deltaPhi1);
}
