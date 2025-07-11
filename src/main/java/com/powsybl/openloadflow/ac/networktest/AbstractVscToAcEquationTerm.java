package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

public abstract class AbstractVscToAcEquationTerm extends AbstractElementEquationTerm<LfDcNode, AcVariableType, AcEquationType> {

    // TODO: we should take the LfVsc object but since it is not an LfElement yet, we use the DC node connected to the vsc on the DC side
    protected final Variable<AcVariableType> pDcVar;

    protected AbstractVscToAcEquationTerm(LfDcNode vscDcNode, LfBus bus, VariableSet<AcVariableType> variableSet) {
        super(vscDcNode);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        pDcVar = variableSet.getVariable(vscDcNode.getNum(), AcVariableType.DC_NODE_P);
    }

    protected double pdc() {
        return sv.get(pDcVar.getRow());
    }
}
