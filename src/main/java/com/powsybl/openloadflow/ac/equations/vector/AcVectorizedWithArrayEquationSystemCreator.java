package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationContext;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationArray;
import com.powsybl.openloadflow.equations.EquationArrayElement;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTermArray;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;

public class AcVectorizedWithArrayEquationSystemCreator extends AcVectorizedEquationSystemCreator {

    private EquationArray<AcVariableType, AcEquationType> pArray;

    private EquationTermArray<AcVariableType, AcEquationType> p1Array;

    private EquationTermArray<AcVariableType, AcEquationType> p2Array;

    public AcVectorizedWithArrayEquationSystemCreator(LfNetwork network, AcEquationSystemCreationParameters creationParameters) {
        super(network, creationParameters);
    }

    protected void create(AcEquationSystemCreationContext creationContext) {
        pArray = creationContext.getEquationSystem().createEquationArray(AcEquationType.BUS_TARGET_P);
        p1Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(p1Array);
        p2Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(p2Array);
        super.create(creationContext);
    }

    @Override
    protected EquationArrayElement<AcVariableType, AcEquationType> createEquation(EquationSystem<AcVariableType, AcEquationType> equationSystem, LfElement element, AcEquationType equationType) {
        if (equationType == AcEquationType.BUS_TARGET_P) {
            return pArray.getElement(element.getNum());
        }
        return super.createEquation(equationSystem, element, equationType);
    }
}
