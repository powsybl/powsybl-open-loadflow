package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractVscToAcEquationTerm extends AbstractElementEquationTerm<LfAcDcConverter, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> pAcVar;

    protected final Variable<AcVariableType> qAcVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected static List<Double> lossFactors = new ArrayList<>();

    protected static double nominalV;

    protected boolean isControllingVac;

    protected AcDcConverter.ControlMode controlMode;

    protected AbstractVscToAcEquationTerm(LfAcDcConverter converter, VariableSet<AcVariableType> variableSet) {
        super(converter);
        Objects.requireNonNull(converter);
        Objects.requireNonNull(variableSet);
        AcVariableType pType = AcVariableType.AC_VSC_P;
        AcVariableType qType = AcVariableType.AC_VSC_Q;
        LfBus bus = converter.getBus1();
        pAcVar = variableSet.getVariable(converter.getNum(), pType);
        qAcVar = variableSet.getVariable(converter.getNum(), qType);
        variables.add(pAcVar);
        lossFactors = converter.getLossFactors();
        nominalV = bus.getNominalV();
        controlMode = converter.getControlMode();
        this.isControllingVac = (converter.isVoltageRegulatorOn());
        if(isControllingVac){
            variables.add(qAcVar);
        }
    }



    protected double pAc() {
        return sv.get(pAcVar.getRow());
    }

    protected double qAc(){
        if(isControllingVac){
            return sv.get(qAcVar.getRow());
        }
        else{
            return 0;
        }
    }

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }
}
