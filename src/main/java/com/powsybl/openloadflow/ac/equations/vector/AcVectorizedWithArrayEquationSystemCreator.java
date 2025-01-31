package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationContext;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;

public class AcVectorizedWithArrayEquationSystemCreator extends AcVectorizedEquationSystemCreator {

    private EquationArray<AcVariableType, AcEquationType> pArray;

    private EquationArray<AcVariableType, AcEquationType> qArray;

    private EquationTermArray<AcVariableType, AcEquationType> closedP1Array;

    private EquationTermArray<AcVariableType, AcEquationType> closedP2Array;

    private EquationTermArray<AcVariableType, AcEquationType> closedQ1Array;

    private EquationTermArray<AcVariableType, AcEquationType> closedQ2Array;

    private EquationTermArray<AcVariableType, AcEquationType> openP1Array;

    private EquationTermArray<AcVariableType, AcEquationType> openP2Array;

    private EquationTermArray<AcVariableType, AcEquationType> openQ1Array;

    private EquationTermArray<AcVariableType, AcEquationType> openQ2Array;

    private EquationTermArray<AcVariableType, AcEquationType> shuntPArray;

    private EquationTermArray<AcVariableType, AcEquationType> shuntQArray;

    public AcVectorizedWithArrayEquationSystemCreator(LfNetwork network, AcEquationSystemCreationParameters creationParameters) {
        super(network, creationParameters);
    }

    @Override
    protected void create(AcEquationSystemCreationContext creationContext) {
        pArray = creationContext.getEquationSystem().createEquationArray(AcEquationType.BUS_TARGET_P);
        qArray = creationContext.getEquationSystem().createEquationArray(AcEquationType.BUS_TARGET_Q);

        closedP1Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(closedP1Array);
        closedP2Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(closedP2Array);
        closedQ1Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(closedQ1Array);
        closedQ2Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide2ReactiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(closedQ2Array);

        openP1Array = new EquationTermArray<>(ElementType.BRANCH, new OpenBranchSide1ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(openP1Array);
        openP2Array = new EquationTermArray<>(ElementType.BRANCH, new OpenBranchSide2ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(openP2Array);
        openQ1Array = new EquationTermArray<>(ElementType.BRANCH, new OpenBranchSide1ReactiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(openQ1Array);
        openQ2Array = new EquationTermArray<>(ElementType.BRANCH, new OpenBranchSide2ReactiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(openQ2Array);

        shuntPArray = new EquationTermArray<>(ElementType.SHUNT_COMPENSATOR, new ShuntCompensatorActiveFlowEquationTermArrayEvaluator(networkVector.getShuntVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(shuntPArray);
        shuntQArray = new EquationTermArray<>(ElementType.SHUNT_COMPENSATOR, new ShuntCompensatorReactiveFlowEquationTermArrayEvaluator(networkVector.getShuntVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(shuntQArray);

        super.create(creationContext);
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, AcEquationSystemCreationContext creationContext) {
        return closedP1Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, AcEquationSystemCreationContext creationContext) {
        return closedQ1Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createClosedBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, AcEquationSystemCreationContext creationContext) {
        return closedP2Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createClosedBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, AcEquationSystemCreationContext creationContext) {
        return closedQ2Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createOpenBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus2, AcEquationSystemCreationContext creationContext) {
        return openP1Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createOpenBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus2, AcEquationSystemCreationContext creationContext) {
        return openQ1Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createOpenBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, AcEquationSystemCreationContext creationContext) {
        return openP2Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createOpenBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, AcEquationSystemCreationContext creationContext) {
        return openQ2Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createShuntCompensatorActiveFlowEquationTerm(LfShunt shunt, LfBus bus, AcEquationSystemCreationContext creationContext) {
        return shuntPArray.getElement(shunt.getNum());
    }

    @Override
    protected EquationTermArrayElement<AcVariableType, AcEquationType> createShuntCompensatorReactiveFlowEquationTerm(LfShunt shunt, LfBus bus, boolean deriveB, AcEquationSystemCreationContext creationContext) {
        return shuntQArray.getElement(shunt.getNum());
    }

    @Override
    protected EquationArrayElement<AcVariableType, AcEquationType> createEquation(EquationSystem<AcVariableType, AcEquationType> equationSystem, LfElement element, AcEquationType equationType) {
        if (equationType == AcEquationType.BUS_TARGET_P) {
            return pArray.getElement(element.getNum());
        } else if (equationType == AcEquationType.BUS_TARGET_Q) {
            return qArray.getElement(element.getNum());
        }
        return super.createEquation(equationSystem, element, equationType);
    }
}
