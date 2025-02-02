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

    private EquationTermArray<AcVariableType, AcEquationType> dummyPArray;

    private EquationTermArray<AcVariableType, AcEquationType> minusDummyPArray;

    private EquationTermArray<AcVariableType, AcEquationType> dummyQArray;

    private EquationTermArray<AcVariableType, AcEquationType> minusDummyQArray;

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

        dummyPArray = new EquationTermArray<>(ElementType.BRANCH, new BranchDummyActivePowerEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet(), false));
        pArray.addTermArray(dummyPArray);

        minusDummyPArray = new EquationTermArray<>(ElementType.BRANCH, new BranchDummyActivePowerEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet(), true));
        pArray.addTermArray(minusDummyPArray);

        dummyQArray = new EquationTermArray<>(ElementType.BRANCH, new BranchDummyReactivePowerEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet(), false));
        qArray.addTermArray(dummyQArray);

        minusDummyQArray = new EquationTermArray<>(ElementType.BRANCH, new BranchDummyReactivePowerEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet(), true));
        qArray.addTermArray(minusDummyQArray);

        super.create(creationContext);
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, AcEquationSystemCreationContext creationContext) {
        return closedP1Array.getElement(branch.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, AcEquationSystemCreationContext creationContext) {
        return closedQ1Array.getElement(branch.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createClosedBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, AcEquationSystemCreationContext creationContext) {
        return closedP2Array.getElement(branch.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createClosedBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, AcEquationSystemCreationContext creationContext) {
        return closedQ2Array.getElement(branch.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createOpenBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus2, AcEquationSystemCreationContext creationContext) {
        return openP1Array.getElement(branch.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createOpenBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus2, AcEquationSystemCreationContext creationContext) {
        return openQ1Array.getElement(branch.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createOpenBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, AcEquationSystemCreationContext creationContext) {
        return openP2Array.getElement(branch.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createOpenBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, AcEquationSystemCreationContext creationContext) {
        return openQ2Array.getElement(branch.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createShuntCompensatorActiveFlowEquationTerm(LfShunt shunt, LfBus bus, AcEquationSystemCreationContext creationContext) {
        return shuntPArray.getElement(shunt.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createShuntCompensatorReactiveFlowEquationTerm(LfShunt shunt, LfBus bus, boolean deriveB, AcEquationSystemCreationContext creationContext) {
        return shuntQArray.getElement(shunt.getNum());
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createDummyActivePowerEquationTerm(LfBranch branch, AcEquationSystemCreationContext creationContext, boolean neg) {
        return neg ? minusDummyPArray.getElement(branch.getNum()) : dummyPArray.getElement(branch.getNum()) ;
    }

    @Override
    protected BaseEquationTerm<AcVariableType, AcEquationType> createDummyReactivePowerEquationTerm(LfBranch branch, AcEquationSystemCreationContext creationContext, boolean neg) {
        return neg ? minusDummyQArray.getElement(branch.getNum()) : dummyQArray.getElement(branch.getNum()) ;
    }
}
