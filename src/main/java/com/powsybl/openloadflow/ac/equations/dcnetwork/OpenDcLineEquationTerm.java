package com.powsybl.openloadflow.ac.equations.dcnetwork;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfDcLine;

import java.util.List;
import java.util.Objects;

public class OpenDcLineEquationTerm extends AbstractElementEquationTerm<LfDcLine, AcVariableType, AcEquationType> {

    public OpenDcLineEquationTerm(LfDcLine dcLine) {
        super(dcLine);
    }

    @Override
    public double eval() {
        return 0.0;
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        return 0.0;
    }

    @Override
    public String getName() {
        return "dc_open";
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return List.of();
    }
}
