/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfDcNode;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;

import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class ConverterDcCurrentEquationTerm extends AbstractConverterDcCurrentEquationTerm {

    public ConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcNode dcNode1, LfDcNode dcNode2, double nominalV, VariableSet<AcVariableType> variableSet) {
        super(converter, dcNode1, dcNode2, nominalV, variableSet);
    }

    public static double iConv(double pAc, double qAc, double v1, double v2, double vAc, double idleLoss, double switchingLoss, double resistiveLoss) {
        double iAc = Math.sqrt(pAc * pAc + qAc * qAc) / vAc;
        double pLoss = idleLoss + switchingLoss * iAc + resistiveLoss * iAc * iAc;
        return (-pAc - pLoss) / (v1 - v2);
    }

    public static double diConvdv1(double pAc, double qAc, double v1, double v2, double vAc, double idleLoss, double switchingLoss, double resistiveLoss) {
        double iAc = Math.sqrt(pAc * pAc + qAc * qAc) / vAc;
        double pLoss = idleLoss + switchingLoss * iAc + resistiveLoss * iAc * iAc;
        return (pAc + pLoss) / ((v1 - v2) * (v1 - v2));
    }

    public static double diConvdv2(double pAc, double qAc, double v1, double v2, double vAc, double idleLoss, double switchingLoss, double resistiveLoss) {
        double iAc = Math.sqrt(pAc * pAc + qAc * qAc) / vAc;
        double pLoss = idleLoss + switchingLoss * iAc + resistiveLoss * iAc * iAc;
        return (-pAc - pLoss) / ((v1 - v2) * (v1 - v2));
    }

    public static double diConvdpAc(double pAc, double qAc, double v1, double v2, double vAc, double switchingLoss, double resistiveLoss) {
        double sAc = Math.sqrt(pAc * pAc + qAc * qAc);
        double iAc = sAc / vAc;
        double dpDcdpAc = -1 - pAc * (switchingLoss + 2 * resistiveLoss * iAc) / (vAc * sAc);
        return dpDcdpAc / (v1 - v2);
    }

    public static double diConvdqAc(double pAc, double qAc, double v1, double v2, double vAc, double switchingLoss, double resistiveLoss) {
        double sAc = Math.sqrt(pAc * pAc + qAc * qAc);
        double iAc = sAc / vAc;
        double dpDcdqAc = -qAc * (switchingLoss + 2 * resistiveLoss * iAc) / (vAc * sAc);
        return dpDcdqAc / (v1 - v2);
    }

    public static double diConvdvAc(double pAc, double qAc, double v1, double v2, double vAc, double switchingLoss, double resistiveLoss) {
        double sAc = Math.sqrt(pAc * pAc + qAc * qAc);
        double iAc = sAc / vAc;
        double dpDcdVAc = qAc * (switchingLoss + 2 * resistiveLoss * iAc) / (vAc * vAc);
        return dpDcdVAc / (v1 - v2);
    }

    @Override
    public double eval() {
        return iConv(pAc(), qAc(), v1(), v2(), vAc(), idleLoss, switchingLoss, resistiveLoss);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(pAcVar)) {
            return diConvdpAc(pAc(), qAc(), v1(), v2(), vAc(), switchingLoss, resistiveLoss);
        } else if (variable.equals(qAcVar)) {
            return diConvdqAc(pAc(), qAc(), v1(), v2(), vAc(), switchingLoss, resistiveLoss);
        } else if (variable.equals(v1Var)) {
            return diConvdv1(pAc(), qAc(), v1(), v2(), vAc(), idleLoss, switchingLoss, resistiveLoss);
        } else if (variable.equals(v2Var)) {
            return diConvdv2(pAc(), qAc(), v1(), v2(), vAc(), idleLoss, switchingLoss, resistiveLoss);
        } else if (variable.equals(vAcVar)) {
            return diConvdvAc(pAc(), qAc(), v1(), v2(), vAc(), switchingLoss, resistiveLoss);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "dc_i";
    }
}
