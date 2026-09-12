/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.dcnetwork;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Droop-control equation of an AC/DC voltage source converter in {@code DC_DROOP} mode:
 * {@code U_dc = refVdc + k*(CONV_P_AC - refP)}, i.e. the residual is
 * {@code a*(CONV_P_AC - refP) - b*(U_dc - refVdc)}, with {@code a = k} and {@code b = 1} kept as separate
 * coefficients (rather than folded into a single {@code k}) so that later work enforcing active-power limits
 * can override them to switch this same term to a pure {@code CONV_P_AC = refP} constraint ({@code a=1},
 * {@code b=0}) or a pure {@code U_dc = refVdc} constraint ({@code a=0}, {@code b=1}).
 * The reference point {@code (k, refVdc, refP)} is that of the droop-curve band containing the solved DC voltage
 * {@code U_dc = v1 - v2}, so it is refreshed at every evaluation; at convergence the coefficient is self-consistent
 * with the band the solution lands in. All quantities are per unit.
 *
 * @author Landry Huet {@literal <landry.huet at supergrid-institute.com>}
 */
public class ConverterDroopEquationTerm extends AbstractConverterDcFlowEquation {

    private List<LfVoltageSourceConverter.LfDroopReference> droopBands;

    public ConverterDroopEquationTerm(LfVoltageSourceConverter converter, LfDcBus dcBus1, LfDcBus dcBus2, VariableSet<AcVariableType> variableSet) {
        // pass the DC voltage base as nominalV so that v1() - v2() is U_dc in per unit of that base
        super(converter, dcBus1, dcBus2, converter.getDcVoltageBase(), variableSet);
        droopBands = new ArrayList<>(converter.getDroopCurve());
        if (droopBands.isEmpty()) {
            throw new PowsyblException("Cannot create a ConverterDroopEquationTerm for AC/DC converter '" + converter.getId()
                    + "' which is not in DC_DROOP control mode");
        }
    }

    @Override
    public double eval() {
        double uDc = v1() - v2();
        LfVoltageSourceConverter.LfDroopReference ref = getDroopReference(uDc);
        double a = ref.k();
        double b = 1;
        return a * (pAc() - ref.refP()) - b * (uDc - ref.refVdc());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        // All DC buses of a DC component share the same nominal voltage (enforced at network loading), which is the
        // DC voltage base. So v1()/v2() are already in the equation base and dU_dc/dv1 = 1, dU_dc/dv2 = -1.
        double b = 1;
        if (variable.equals(pAcVar)) {
            return getDroopReference(v1() - v2()).k();
        } else if (variable.equals(v1Var)) {
            return -b;
        } else if (variable.equals(v2Var)) {
            return b;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "conv_p_droop";
    }

    private LfVoltageSourceConverter.LfDroopReference getDroopReference(double uDc) {
        // Note that the list is supposed to be sorted by refVdc.
        // Linear search is faster for small lists.
        int idx = 0; // default if uDc below min voltage.
        for (idx = 0; idx < droopBands.size() - 1; idx++) {
            if (uDc < droopBands.get(idx + 1).refVdc()) {
                break;
            }
        } // if uDc is beyond voltage of last segment, idx stays clamped to droopBands.size() - 1
        return droopBands.get(idx);
    }
}
