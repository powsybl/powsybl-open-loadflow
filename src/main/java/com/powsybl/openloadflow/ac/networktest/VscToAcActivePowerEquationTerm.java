package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;
import com.powsybl.openloadflow.util.PerUnit;
import net.jafama.FastMath;

import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.ac.equations.AbstractClosedBranchAcFlowEquationTerm.theta1;
import static com.powsybl.openloadflow.network.PiModel.R2;

public class VscToAcActivePowerEquationTerm extends AbstractVscToAcEquationTerm {

    public VscToAcActivePowerEquationTerm(LfDcNode vscDcNode, LfBus bus, VariableSet<AcVariableType> variableSet, boolean isControllingVac) {
        super(vscDcNode, bus, variableSet, isControllingVac);
    }


    public static double pDc(double pAc, double qAc){
        double iAc = iAc(pAc, qAc);
        return -(pAc-pLoss(iAc));
    }

    public static double iAc(double pAc, double qAc){
        return Math.sqrt(pAc*pAc+qAc*qAc)*Math.sqrt(2)/(Math.sqrt(3)* nominalV);
    }

    public static double pLoss(double iAc){
        return lossFactors.get(0) + lossFactors.get(1)*iAc + lossFactors.get(2)*iAc*iAc;
    }

    public static double dpDcdpAc(double pAc, double qAc){
        return -(1-2*pAc*(lossFactors.get(1)+2*lossFactors.get(2)*iAc(pAc, qAc))/(2*Math.sqrt(pAc*pAc+qAc*qAc)*nominalV*Math.sqrt(6)));
    }

    public static double dpDcdqAc(double pAc, double qAc){
        return 2*qAc*(lossFactors.get(1)+2*lossFactors.get(2)*iAc(pAc, qAc))/(2*Math.sqrt(pAc*pAc+qAc*qAc)*nominalV*Math.sqrt(6));
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
