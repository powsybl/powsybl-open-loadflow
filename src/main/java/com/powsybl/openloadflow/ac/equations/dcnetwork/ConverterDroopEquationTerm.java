/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.dcnetwork;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;

import java.util.Objects;

/**
 * Droop-control equation of an AC/DC voltage source converter in {@code P_PCC_DROOP} mode:
 * {@code CONV_P_AC = refP + k*(U_dc - refVdc)}, i.e. the residual is {@code CONV_P_AC - refP - k*(U_dc - refVdc)}.
 * The reference point {@code (k, refVdc, refP)} is that of the droop-curve band containing the solved DC voltage
 * {@code U_dc = v1 - v2}, so it is refreshed at every evaluation; at convergence the coefficient is self-consistent
 * with the band the solution lands in. All quantities are per unit.
 *
 * @author Landry Huet {@literal <landry.huet at supergrid-institute.com>}
 */
public class ConverterDroopEquationTerm extends AbstractConverterDcCurrentEquationTerm {

    public ConverterDroopEquationTerm(LfVoltageSourceConverter converter, LfDcBus dcBus1, LfDcBus dcBus2, VariableSet<AcVariableType> variableSet) {
        // pass the DC voltage base as nominalV so that v1() - v2() is U_dc in per unit of that base
        super(converter, dcBus1, dcBus2, converter.getDcVoltageBase(), variableSet);
    }

    @Override
    public double eval() {
        double uDc = v1() - v2();
        LfVoltageSourceConverter.DroopReference ref = element.getDroopReference(uDc);
        return pAc() - ref.refP() - ref.k() * (uDc - ref.refVdc());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        // The residual is expressed in the dcNominalV base, but the state variables v1Var/v2Var are per unit of their
        // own DC bus nominal voltage (see v1()/v2() in the parent). The Jacobian must therefore be taken with respect
        // to the raw state variables, which introduces the chain-rule factor dcBusX.getNominalV() / dcNominalV that
        // converts from the equation base to the state-variable base.
        // This factor is defensive programming: in a well-formed DC network the nominal voltage is the same everywhere
        // (dcBusX.getNominalV() == dcNominalV) and the factor reduces to 1. We keep it explicit so the Jacobian stays
        // consistent with the residual should that assumption ever be relaxed.
        if (variable.equals(pAcVar)) {
            return 1;
        } else if (variable.equals(v1Var)) {
            double k = element.getDroopReference(v1() - v2()).k();
            return -k * (dcBus1.getNominalV() / dcNominalV);
        } else if (variable.equals(v2Var)) {
            double k = element.getDroopReference(v1() - v2()).k();
            return k * (dcBus2.getNominalV() / dcNominalV);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "conv_p_droop";
    }
}
