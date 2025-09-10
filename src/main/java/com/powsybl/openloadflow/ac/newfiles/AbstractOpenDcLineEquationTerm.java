package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractOpenDcLineEquationTerm extends AbstractElementEquationTerm<LfDcLine, AcVariableType, AcEquationType> {

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected AbstractOpenDcLineEquationTerm(LfDcLine dcLine, VariableSet<AcVariableType> variableSet) {
        super(dcLine);
        Objects.requireNonNull(variableSet);
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}

