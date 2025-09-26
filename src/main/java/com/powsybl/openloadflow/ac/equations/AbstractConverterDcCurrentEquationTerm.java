package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.network.LfDcNode;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractConverterDcCurrentEquationTerm extends AbstractElementEquationTerm<LfVoltageSourceConverter, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> v1Var;

    protected final Variable<AcVariableType> vRVar;

    protected final Variable<AcVariableType> pAcVar;

    protected final Variable<AcVariableType> qAcVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected final boolean isBipolar;

    protected static List<Double> lossFactors = new ArrayList<>();

    protected static double nominalV;

    protected AbstractConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(converter);
        Objects.requireNonNull(converter);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_NODE_V;
        AcVariableType pType = AcVariableType.CONV_P_AC;
        AcVariableType qType = AcVariableType.CONV_Q_AC;
        LfBus bus = converter.getBus1();
        isBipolar = converter.isBipolar();
        if (isBipolar) {
            vRVar = variableSet.getVariable(dcNode2.getNum(), vType);
            variables.add(vRVar);
        } else {
            vRVar = null;
        }
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        pAcVar = variableSet.getVariable(converter.getNum(), pType);
        qAcVar = variableSet.getVariable(converter.getNum(), qType);
        variables.add(v1Var);
        variables.add(pAcVar);
        variables.add(qAcVar);
        lossFactors = converter.getLossFactors();
        nominalV = bus.getNominalV();
    }

    protected double v1() {
        return sv.get(v1Var.getRow());
    }

    protected double pAc() {
        return sv.get(pAcVar.getRow());
    }

    protected double qAc() {
        return sv.get(qAcVar.getRow());
    }

    protected double vR() {
        if (isBipolar) {
            return sv.get(vRVar.getRow());
        } else {
            return 0.0;
        }
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
