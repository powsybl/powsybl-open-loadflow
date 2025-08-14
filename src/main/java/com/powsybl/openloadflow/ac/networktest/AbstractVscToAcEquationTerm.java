package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractVscToAcEquationTerm extends AbstractElementEquationTerm<LfDcNode, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> pAcVar;

    protected final Variable<AcVariableType> qAcVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected static List<Double> lossFactors = new ArrayList<>();

    protected static double nominalV;

    protected boolean isControllingVac;

    protected static ConverterStationMode converterMode;

    protected AbstractVscToAcEquationTerm(LfDcNode dcNode, LfBus bus, VariableSet<AcVariableType> variableSet, boolean isControllingVac) {
        super(dcNode);
        Objects.requireNonNull(dcNode);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        LfAcDcVscConverterStation converterStation = bus.getAcDcVscConverterStations().get(0);
        AcVariableType pType = AcVariableType.AC_VSC_P;
        AcVariableType qType = AcVariableType.AC_VSC_Q;
        pAcVar = variableSet.getVariable(bus.getNum(), pType);
        qAcVar = variableSet.getVariable(bus.getNum(), qType);
        variables.add(pAcVar);
        lossFactors = converterStation.getLossFactors();
        nominalV = bus.getNominalV();
        converterMode = converterStation.getMode();
        this.isControllingVac = isControllingVac;
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
