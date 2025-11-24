package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.Variable;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public interface ClosedBranchAcFlowEquationTerm extends EquationTerm<AcVariableType, AcEquationType> {

    double y();

    double ksi();

    double b1();

    double b2();

    double g1();

    double g2();

    double r1();

    double a1();

    Variable<AcVariableType> getPhi1Var();

    Variable<AcVariableType> getPhi2Var();

    Variable<AcVariableType> getV1Var();

    Variable<AcVariableType> getV2Var();

    Variable<AcVariableType> getA1Var();

    Variable<AcVariableType> getR1Var();
}
