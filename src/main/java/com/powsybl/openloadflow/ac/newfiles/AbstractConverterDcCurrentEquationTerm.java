package com.powsybl.openloadflow.ac.newfiles;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
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

    protected boolean isBipolar;

    protected static List<Double> lossFactors = new ArrayList<>();

    protected static double nominalV;

    protected final Variable<AcVariableType> pAcVar;

    protected final Variable<AcVariableType> qAcVar;

    protected boolean isVoltageRegulatorOn;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();


    protected AbstractConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(converter);
        Objects.requireNonNull(converter);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_NODE_V;
        AcVariableType pType = AcVariableType.CONV_P_AC;
        AcVariableType qType = AcVariableType.CONV_Q_AC;
        LfBus bus = converter.getBus1();
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        vRVar = variableSet.getVariable(dcNode2.getNum(), vType);
        pAcVar = variableSet.getVariable(converter.getNum(), pType);
        qAcVar = variableSet.getVariable(converter.getNum(), qType);
        variables.add(v1Var);
        variables.add(vRVar);
        variables.add(pAcVar);
        variables.add(qAcVar);
        lossFactors = converter.getLossFactors();
        nominalV = bus.getNominalV();
        this.isVoltageRegulatorOn = converter.isVoltageRegulatorOn();
        isBipolar = true;
    }

    protected AbstractConverterDcCurrentEquationTerm(LfVoltageSourceConverter converter, LfDcNode dcNode1, VariableSet<AcVariableType> variableSet) {
        super(converter);
        Objects.requireNonNull(converter);
        Objects.requireNonNull(variableSet);
        AcVariableType vType = AcVariableType.DC_NODE_V;
        AcVariableType pType = AcVariableType.CONV_P_AC;
        AcVariableType qType = AcVariableType.CONV_Q_AC;
        LfBus bus = converter.getBus1();
        v1Var = variableSet.getVariable(dcNode1.getNum(), vType);
        vRVar = null;
        pAcVar = variableSet.getVariable(converter.getNum(), pType);
        qAcVar = variableSet.getVariable(converter.getNum(), qType);
        variables.add(v1Var);
        variables.add(pAcVar);
        variables.add(qAcVar);
        lossFactors = converter.getLossFactors();
        nominalV = bus.getNominalV();
        this.isVoltageRegulatorOn = converter.isVoltageRegulatorOn();
        isBipolar = false;
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
