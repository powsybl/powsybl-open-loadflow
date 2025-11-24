package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;

public interface ClosedBranchEquationTermArrayEvaluator {

    double y(int branchNum);

    double ksi(int branchNum);

    double b1(int branchNum);

    double b2(int branchNum);

    double g1(int branchNum);

    double g2(int branchNum);

    double r1(int branchNum);

    double a1(int branchNum);

    Variable<AcVariableType> getPhi1Var(int branchNum);

    Variable<AcVariableType> getPhi2Var(int branchNum);

    Variable<AcVariableType> getV1Var(int branchNum);

    Variable<AcVariableType> getV2Var(int branchNum);

    Variable<AcVariableType> getA1Var(int branchNum);

    Variable<AcVariableType> getR1Var(int branchNum);
}
