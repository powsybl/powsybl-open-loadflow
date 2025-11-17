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
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class ConverterDcCurrentEquationTerm extends AbstractConverterDcCurrentEquationTerm {

    public ConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcNode dcNode1, LfDcNode dcNode2, double nominalV, VariableSet<AcVariableType> variableSet) {
        super(converter, dcNode1, dcNode2, nominalV, variableSet);
    }

    public static double iConv(double pAc, double qAc, double v1, double v2) {
        return pDc(pAc, qAc) / (v1 - v2);
    }

    public static double diConvdv1(double pAc, double qAc, double v1, double v2) {
        return -pDc(pAc, qAc) / ((v1 - v2) * (v1 - v2));
    }

    public static double diConvdv2(double pAc, double qAc, double v1, double v2) {
        return pDc(pAc, qAc) / ((v1 - v2) * (v1 - v2));
    }

    public static double diConvdpAc(double pAc, double qAc, double v1, double v2) {
        return dpDcdpAc(pAc, qAc) / (v1 - v2);
    }

    public static double diConvdqAc(double pAc, double qAc, double v1, double v2) {
        return dpDcdqAc(pAc, qAc) / (v1 - v2);
    }

    public static double iAc(double pAc, double qAc) {
        return Math.sqrt(pAc * pAc + qAc * qAc) / 1000.0;
    }

    public static double pLoss(double iAc) {
        return lossA() + lossB() * iAc + lossC() * iAc * iAc;
    }

    public static double lossA() {
        return lossFactors.get(0) / PerUnit.SB;
    }

    public static double lossB() {
        return lossFactors.get(1) / (Math.sqrt(3) * acNominalV);
    }

    public static double lossC() {
        return lossFactors.get(2) * PerUnit.ib(acNominalV) * PerUnit.ib(acNominalV) / PerUnit.SB;
    }

    public static double dpDcdqAc(double pAc, double qAc) { //pAc, qAc, iAc and loss factors are per unitized
        return -qAc * (lossB() + 2 * lossC() * iAc(pAc, qAc)) / (Math.sqrt(pAc * pAc + qAc * qAc));
    }

    public static double pDc(double pAc, double qAc) {
        double iAc = iAc(pAc, qAc);
        return -pAc - pLoss(iAc);
    }

    public static double dpDcdpAc(double pAc, double qAc) {
        return -1 - pAc * (lossB() + 2 * lossC() * iAc(pAc, qAc)) / (Math.sqrt(pAc * pAc + qAc * qAc));
    }

    @Override
    public double eval() {
        return iConv(pAc(), qAc(), v1(), v2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(pAcVar)) {
            return diConvdpAc(pAc(), qAc(), v1(), v2());
        } else if (variable.equals(qAcVar)) {
            return diConvdqAc(pAc(), qAc(), v1(), v2());
        } else if (variable.equals(v1Var)) {
            return diConvdv1(pAc(), qAc(), v1(), v2());
        } else if (variable.equals(v2Var)) {
            return diConvdv2(pAc(), qAc(), v1(), v2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "dc_i";
    }
}
