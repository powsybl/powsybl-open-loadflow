package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchAcFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.vector.ClosedBranchEquationTermArrayEvaluator;
import com.powsybl.openloadflow.equations.Variable;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public abstract class AbstractClosedBranchAcFlowFastDecoupledEquationTerm<ClosedBranchTermType extends ClosedBranchAcFlowEquationTerm, ClosedBranchTermEvaluatorType extends ClosedBranchEquationTermArrayEvaluator> implements FastDecoupledEquationTerm {

    protected final double y;
    protected final double ksi;
    protected final double b1;
    protected final double b2;
    protected final double r1;
    protected final double a1;
    protected final Variable<AcVariableType> phi1Var;
    protected final Variable<AcVariableType> phi2Var;
    protected final Variable<AcVariableType> v1Var;
    protected final Variable<AcVariableType> v2Var;
    protected final Variable<AcVariableType> a1Var;
    protected final Variable<AcVariableType> r1Var;

    protected AbstractClosedBranchAcFlowFastDecoupledEquationTerm(ClosedBranchTermType closedBranchAcFlowEquationTerm) {
        // If single term, getting term data through ClosedBranchAcFlowEquationTerm
        y = closedBranchAcFlowEquationTerm.y();
        ksi = closedBranchAcFlowEquationTerm.ksi();
        b1 = closedBranchAcFlowEquationTerm.b1();
        b2 = closedBranchAcFlowEquationTerm.b2();
        r1 = closedBranchAcFlowEquationTerm.r1();
        a1 = closedBranchAcFlowEquationTerm.a1();
        phi1Var = closedBranchAcFlowEquationTerm.getPhi1Var();
        phi2Var = closedBranchAcFlowEquationTerm.getPhi2Var();
        v1Var = closedBranchAcFlowEquationTerm.getV1Var();
        v2Var = closedBranchAcFlowEquationTerm.getV2Var();
        a1Var = closedBranchAcFlowEquationTerm.getA1Var();
        r1Var = closedBranchAcFlowEquationTerm.getR1Var();
    }

    protected AbstractClosedBranchAcFlowFastDecoupledEquationTerm(ClosedBranchTermEvaluatorType closedBranchEvaluator, int branchNum) {
        // If term array, getting term data through its evaluator
        y = closedBranchEvaluator.y(branchNum);
        ksi = closedBranchEvaluator.ksi(branchNum);
        b1 = closedBranchEvaluator.b1(branchNum);
        b2 = closedBranchEvaluator.b2(branchNum);
        r1 = closedBranchEvaluator.r1(branchNum);
        a1 = closedBranchEvaluator.a1(branchNum);
        phi1Var = closedBranchEvaluator.getPhi1Var(branchNum);
        phi2Var = closedBranchEvaluator.getPhi2Var(branchNum);
        v1Var = closedBranchEvaluator.getV1Var(branchNum);
        v2Var = closedBranchEvaluator.getV2Var(branchNum);
        a1Var = closedBranchEvaluator.getA1Var(branchNum);
        r1Var = closedBranchEvaluator.getR1Var(branchNum);
    }
}
