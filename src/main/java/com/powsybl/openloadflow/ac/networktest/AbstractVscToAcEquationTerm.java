package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.network.PiModel.A2;

public abstract class AbstractVscToAcEquationTerm extends AbstractElementEquationTerm<LfDcNode, AcVariableType, AcEquationType> {

    protected final Variable<AcVariableType> pAcVar;

    protected final Variable<AcVariableType> qAcVar;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    protected static List<Double> lossFactors = new ArrayList<>();

    protected static double nominalV;

    protected boolean isControllingVac;


    protected AbstractVscToAcEquationTerm(LfDcNode dcNode, LfBus bus, VariableSet<AcVariableType> variableSet, boolean isControllingVac) {
        super(dcNode);
        Objects.requireNonNull(dcNode);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);
        AcVariableType pType = AcVariableType.BUS_P;
        AcVariableType qType = AcVariableType.BUS_Q;
        pAcVar = variableSet.getVariable(bus.getNum(), pType);
        qAcVar = variableSet.getVariable(bus.getNum(), qType);
        variables.add(pAcVar);
        lossFactors = bus.getVscConverterStations().get(0).getLossFactors();
        nominalV = bus.getNominalV();
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
