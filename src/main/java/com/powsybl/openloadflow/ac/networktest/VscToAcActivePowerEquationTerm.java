package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

public class VscToAcActivePowerEquationTerm extends AbstractVscToAcEquationTerm {

    public VscToAcActivePowerEquationTerm(LfAcDcConverter converter, VariableSet<AcVariableType> variableSet) {
        super(converter, variableSet);
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
        return -qAc*(lossB()+2*lossC()*iAcPerUnit(pAc,qAc))/(Math.sqrt(pAc*pAc+qAc*qAc));
    }

    public static double pDc(double pAcPerUnit, double qAcPerUnit) {
        double iAcPerUnit = iAcPerUnit(pAcPerUnit, qAcPerUnit);
        return -pAcPerUnit - pLoss(iAcPerUnit);
    }

    public static double dpDcdpAc(double pAc, double qAc) {
        return -1-pAc*(lossB()+2*lossC()*iAcPerUnit(pAc,qAc))/(Math.sqrt(pAc*pAc+qAc*qAc));
    }

    @Override
    public double eval() {
        System.out.println("##############################_____QAC_____##############################");
        System.out.println(qAc());
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
        return "ac_p";
    }
}
