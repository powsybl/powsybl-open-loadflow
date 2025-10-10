package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfDcNode;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

public class ConverterDcCurrentEquationTerm extends AbstractConverterDcCurrentEquationTerm {

    public ConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(converter, dcNode1, dcNode2, variableSet);
    }

    public static double iConv(double pAc, double qAc, double v1, double vR) {
        return pDc(pAc, qAc) / (v1 - vR);
    }

    public static double diConvdv1(double pAc, double qAc, double v1, double vR) {
        return -pDc(pAc, qAc) / ((v1 - vR) * (v1 - vR));
    }

    public static double diConvdvR(double pAc, double qAc, double v1, double vR) {
        return pDc(pAc, qAc) / ((v1 - vR) * (v1 - vR));
    }

    public static double diConvdpAc(double pAc, double qAc, double v1, double vR) {
        return dpDcdpAc(pAc, qAc) / (v1 - vR);
    }

    public static double diConvdqAc(double pAc, double qAc, double v1, double vR) {
        return dpDcdqAc(pAc, qAc) / (v1 - vR);
    }

    public static double iAcPerUnit(double pAcPerUnit, double qAcPerUnit) {
        double pAc = pAcPerUnit * PerUnit.SB;
        double qAc = qAcPerUnit * PerUnit.SB;
        return Math.sqrt(pAc * pAc + qAc * qAc) / (Math.sqrt(3) * nominalV * PerUnit.ib(nominalV));
    }

    public static double pLoss(double iAcPerUnit) {
        return lossA() + lossB() * iAcPerUnit + lossC() * iAcPerUnit * iAcPerUnit;
    }

    public static double lossA() {
        return lossFactors.get(0) / PerUnit.SB;
    }

    public static double lossB() {
        return lossFactors.get(1) / (Math.sqrt(3) * nominalV);
    }

    public static double lossC() {
        return lossFactors.get(2) * PerUnit.ib(nominalV) * PerUnit.ib(nominalV) / PerUnit.SB;
    }

    public static double dpDcdqAc(double pAc, double qAc) { //pAc and qAc are perUnit
        return -qAc * (lossB() + 2 * lossC() * iAcPerUnit(pAc, qAc)) / (Math.sqrt(pAc * pAc + qAc * qAc));
    }

    public static double pDc(double pAcPerUnit, double qAcPerUnit) {
        double iAcPerUnit = iAcPerUnit(pAcPerUnit, qAcPerUnit);
        return -pAcPerUnit - pLoss(iAcPerUnit);
    }

    public static double dpDcdpAc(double pAc, double qAc) {
        return -1 - pAc * (lossB() + 2 * lossC() * iAcPerUnit(pAc, qAc)) / (Math.sqrt(pAc * pAc + qAc * qAc));
    }

    @Override
    public double eval() {
        return iConv(pAc(), qAc(), v1(), vR());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(pAcVar)) {
            return diConvdpAc(pAc(), qAc(), v1(), vR());
        } else if (variable.equals(qAcVar)) {
            return diConvdqAc(pAc(), qAc(), v1(), vR());
        } else if (variable.equals(v1Var)) {
            return diConvdv1(pAc(), qAc(), v1(), vR());
        } else if (variable.equals(vRVar)) {
            return diConvdvR(pAc(), qAc(), v1(), vR());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "dc_i";
    }
}
