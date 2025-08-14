package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

public class VscToAcActivePowerEquationTerm extends AbstractVscToAcEquationTerm {

    protected static boolean isRectifier;

    public VscToAcActivePowerEquationTerm(LfDcNode vscDcNode, LfBus bus, VariableSet<AcVariableType> variableSet, boolean isControllingVac) {
        super(vscDcNode, bus, variableSet, isControllingVac);
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
        if (converterMode == ConverterStationMode.INVERTER) {
            return lossFactors.get(3) * PerUnit.ib(nominalV) * PerUnit.ib(nominalV) / PerUnit.SB;
        }
        else {
            return lossFactors.get(2) * PerUnit.ib(nominalV) * PerUnit.ib(nominalV) / PerUnit.SB;
        }
    }

    public static double dpDcdqAc(double pAc, double qAc) { //pAc and qAc are perUnit
        return -2 * qAc * (lossB() + 2 * lossC() * iAcPerUnit(pAc, qAc)) / (2 * Math.sqrt(pAc * pAc + qAc * qAc) * nominalV * Math.sqrt(6));
    }

    public double pDc(double pAcPerUnit, double qAcPerUnit) {
        double iAcPerUnit = iAcPerUnit(pAcPerUnit, qAcPerUnit);
        return -pAcPerUnit - pLoss(iAcPerUnit);
    }

    public double dpDcdpAc(double pAc, double qAc) {
        return -1 - 2 * pAc * (lossB() + 2 * lossC() * iAcPerUnit(pAc, qAc)) / (2 * Math.sqrt(pAc * pAc + qAc * qAc) * nominalV * Math.sqrt(6));
    }

    @Override
    public double eval() {
        return pDc(pAc(), qAc());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(pAcVar)) {
            return dpDcdpAc(pAc(), qAc());
        } else if (variable.equals(qAcVar)) {
            return dpDcdqAc(pAc(), qAc());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
