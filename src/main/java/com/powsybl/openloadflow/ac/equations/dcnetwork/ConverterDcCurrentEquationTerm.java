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
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public class ConverterDcCurrentEquationTerm extends AbstractConverterDcCurrentEquationTerm {

    public ConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcBus dcBus1, LfDcBus dcBus2, double nominalV, VariableSet<AcVariableType> variableSet) {
        super(converter, dcBus1, dcBus2, nominalV, variableSet);
    }

    private static double iConvSign(double pAc, double v1, double v2) {
        // P_AC <=0 and v1>=v2 or P_AC>=0 and v1<=v2 implies Iconv>=0. Otherwise, Iconv<=0
        return -Math.signum(pAc) * Math.signum(v1 - v2);
    }

    /*
        Computation of Iconv and its derivative if there is no resistive losses
     */
    public static double iConvNoResistiveLoss(double pAc, double v1, double v2, double idleLoss, double switchingLoss) {
        double iConvSign = iConvSign(pAc, v1, v2);
        return (pAc - idleLoss) / (iConvSign * switchingLoss - (v1 - v2));
    }

    public static double diConvdpAcNoResistiveLoss(double pAc, double v1, double v2, double switchingLoss) {
        double iConvSign = iConvSign(pAc, v1, v2);
        return 1 / (iConvSign * switchingLoss - (v1 - v2));
    }

    public static double diConvdv1NoResistiveLoss(double pAc, double v1, double v2, double idleLoss, double switchingLoss) {
        double iConvSign = -Math.signum(pAc) * Math.signum(v1 - v2);
        return (pAc - idleLoss) / Math.pow(iConvSign * switchingLoss - (v1 - v2), 2);
    }

    public static double diConvdv2NoResistiveLoss(double pAc, double v1, double v2, double idleLoss, double switchingLoss) {
        double iConvSign = -Math.signum(pAc) * Math.signum(v1 - v2);
        return -(pAc - idleLoss) / Math.pow(iConvSign * switchingLoss - (v1 - v2), 2);
    }

    /*
        Computation of Iconv and its derivative if there is resistive losses
     */
    public static double iConvWithResistiveLoss(double pAc, double v1, double v2, double idleLoss, double switchingLoss, double resistiveLoss) {
        // Once the sign of Iconv is fixed, the equation is a simple second order polynomial
        double iConvSign = iConvSign(pAc, v1, v2);
        double b = iConvSign * switchingLoss - (v1 - v2); // In polynomial expression a=resistiveLoss and c=idleLoss-P_AC
        double delta = Math.pow(b, 2) - 4 * resistiveLoss * (idleLoss - pAc);
        // Whether to consider solution with + sqrt(delta) or -sqrt(delta) depends on the sign of v1-v2
        return (-b - Math.signum(v1 - v2) * Math.sqrt(delta)) / (2 * resistiveLoss);
    }

    public static double diConvdpAcWithResistiveLoss(double pAc, double v1, double v2, double idleLoss, double switchingLoss, double resistiveLoss) {
        double iConvSign = iConvSign(pAc, v1, v2);
        double b = iConvSign * switchingLoss - (v1 - v2);
        double delta = Math.pow(b, 2) - 4 * resistiveLoss * (idleLoss - pAc);

        return -Math.signum(v1 - v2) / Math.sqrt(delta);
    }

    public static double diConvdv1WithResistiveLoss(double pAc, double v1, double v2, double idleLoss, double switchingLoss, double resistiveLoss) {
        double iConvSign = -Math.signum(pAc) * Math.signum(v1 - v2);
        double b = iConvSign * switchingLoss - (v1 - v2);
        double delta = Math.pow(b, 2) - 4 * resistiveLoss * (idleLoss - pAc);

        return (1 - Math.signum(v1 - v2) * (v1 - v2 - iConvSign * switchingLoss) / Math.sqrt(delta)) / (2 * resistiveLoss);
    }

    public static double diConvdv2WithResistiveLoss(double pAc, double v1, double v2, double idleLoss, double switchingLoss, double resistiveLoss) {
        double iConvSign = -Math.signum(pAc) * Math.signum(v1 - v2);
        double b = iConvSign * switchingLoss - (v1 - v2);
        double delta = Math.pow(b, 2) - 4 * resistiveLoss * (idleLoss - pAc);

        return -(1 - Math.signum(v1 - v2) * (v1 - v2 - iConvSign * switchingLoss) / Math.sqrt(delta)) / (2 * resistiveLoss);
    }

    @Override
    public double eval() {
        /*
            P_AC + P_DC = P_loss
            P_AC + I_conv * (V1-V2) = idle_loss + switching_loss_factor * |Iconv| + resistive_loss_factor * I_conv^2
            This is close to a 2nd order polynomial expression except for the absolute value of Iconv, which is the
            unknown value to compute.
            Assuming |P_AC|, |P_DC| > P_loss, the sign of P_AC and P_DC are opposite.
            Thus, we can use sign(P_AC) and sign(V1-V2) to infer sign(I_conv) and solve the polynomial expression.
            Additionally, the equation system changes if resistive_loss_factor is zero
         */
        if (resistiveLoss == 0) {
            return iConvNoResistiveLoss(pAc(), v1(), v2(), idleLoss, switchingLoss);
        } else {
            return iConvWithResistiveLoss(pAc(), v1(), v2(), idleLoss, switchingLoss, resistiveLoss);
        }
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(pAcVar)) {
            if (resistiveLoss == 0) {
                return diConvdpAcNoResistiveLoss(pAc(), v1(), v2(), switchingLoss);
            } else {
                return diConvdpAcWithResistiveLoss(pAc(), v1(), v2(), idleLoss, switchingLoss, resistiveLoss);
            }
        } else if (variable.equals(v1Var)) {
            if (resistiveLoss == 0) {
                return diConvdv1NoResistiveLoss(pAc(), v1(), v2(), idleLoss, switchingLoss);
            } else {
                return diConvdv1WithResistiveLoss(pAc(), v1(), v2(), idleLoss, switchingLoss, resistiveLoss);
            }
        } else if (variable.equals(v2Var)) {
            if (resistiveLoss == 0) {
                return diConvdv2NoResistiveLoss(pAc(), v1(), v2(), idleLoss, switchingLoss);
            } else {
                return diConvdv2WithResistiveLoss(pAc(), v1(), v2(), idleLoss, switchingLoss, resistiveLoss);
            }
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "dc_i";
    }
}
