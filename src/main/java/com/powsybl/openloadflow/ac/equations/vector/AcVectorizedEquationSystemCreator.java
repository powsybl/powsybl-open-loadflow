/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations.vector;

import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcVectorizedEquationSystemCreator extends AcEquationSystemCreator {

    protected AcNetworkVector networkVector;

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

    private EquationTermArray<AcVariableType, AcEquationType> hvdcP1Array;

    private EquationTermArray<AcVariableType, AcEquationType> hvdcP2Array;

    private EquationTermArray<AcVariableType, AcEquationType> loadModelPArray;

    private EquationTermArray<AcVariableType, AcEquationType> loadModelQArray;

    public AcVectorizedEquationSystemCreator(LfNetwork network, AcEquationSystemCreationParameters creationParameters) {
        super(network, creationParameters);
    }

    @Override
    protected void create(EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        networkVector = new AcNetworkVector(network, equationSystem, creationParameters);

        pArray = equationSystem.createEquationArray(AcEquationType.BUS_TARGET_P);
        qArray = equationSystem.createEquationArray(AcEquationType.BUS_TARGET_Q);

        closedP1Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), networkVector.getBusVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(closedP1Array);
        closedP2Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), networkVector.getBusVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(closedP2Array);
        closedQ1Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), networkVector.getBusVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(closedQ1Array);
        closedQ2Array = new EquationTermArray<>(ElementType.BRANCH, new ClosedBranchSide2ReactiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), networkVector.getBusVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(closedQ2Array);

        /*openP1Array = new EquationTermArray<>(ElementType.BRANCH, new OpenBranchSide1ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(openP1Array);
        openP2Array = new EquationTermArray<>(ElementType.BRANCH, new OpenBranchSide2ActiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(openP2Array);
        openQ1Array = new EquationTermArray<>(ElementType.BRANCH, new OpenBranchSide1ReactiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(openQ1Array);
        openQ2Array = new EquationTermArray<>(ElementType.BRANCH, new OpenBranchSide2ReactiveFlowEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(openQ2Array);

        shuntPArray = new EquationTermArray<>(ElementType.SHUNT_COMPENSATOR, new ShuntCompensatorActiveFlowEquationTermArrayEvaluator(networkVector.getShuntVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(shuntPArray);
        shuntQArray = new EquationTermArray<>(ElementType.SHUNT_COMPENSATOR, new ShuntCompensatorReactiveFlowEquationTermArrayEvaluator(networkVector.getShuntVector(), networkVector.getBusVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(shuntQArray);

        dummyPArray = new EquationTermArray<>(ElementType.BRANCH, new BranchDummyActivePowerEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet(), false));
        pArray.addTermArray(dummyPArray);

        minusDummyPArray = new EquationTermArray<>(ElementType.BRANCH, new BranchDummyActivePowerEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet(), true));
        pArray.addTermArray(minusDummyPArray);

        dummyQArray = new EquationTermArray<>(ElementType.BRANCH, new BranchDummyReactivePowerEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet(), false));
        qArray.addTermArray(dummyQArray);

        minusDummyQArray = new EquationTermArray<>(ElementType.BRANCH, new BranchDummyReactivePowerEquationTermArrayEvaluator(networkVector.getBranchVector(), equationSystem.getVariableSet(), true));
        qArray.addTermArray(minusDummyQArray);

        hvdcP1Array = new EquationTermArray<>(ElementType.HVDC, new HvdcAcEmulationSide1ActiveFlowEquationTermArrayEvaluator(networkVector.getHvdcVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(hvdcP1Array);
        hvdcP2Array = new EquationTermArray<>(ElementType.HVDC, new HvdcAcEmulationSide2ActiveFlowEquationTermArrayEvaluator(networkVector.getHvdcVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(hvdcP2Array);

        loadModelPArray = new EquationTermArray<>(ElementType.LOAD, new LoadModelActiveFlowEquationTermArrayEvaluator(networkVector.getLoadVector(), networkVector.getBusVector(), equationSystem.getVariableSet()));
        pArray.addTermArray(loadModelPArray);
        loadModelQArray = new EquationTermArray<>(ElementType.LOAD, new LoadModelReactiveFlowEquationTermArrayEvaluator(networkVector.getLoadVector(), networkVector.getBusVector(), equationSystem.getVariableSet()));
        qArray.addTermArray(loadModelQArray);

        networkVector.startListening();*/

        super.create(equationSystem);
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createClosedBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return closedP1Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createClosedBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return closedQ1Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createClosedBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return closedP2Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createClosedBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, boolean deriveA1, boolean deriveR1, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return closedQ2Array.getElement(branch.getNum());
    }

    /*@Override
    protected EquationTerm<AcVariableType, AcEquationType> createOpenBranchSide1ActiveFlowEquationTerm(LfBranch branch, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return openP1Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createOpenBranchSide1ReactiveFlowEquationTerm(LfBranch branch, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return openQ1Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createOpenBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return openP2Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createOpenBranchSide2ReactiveFlowEquationTerm(LfBranch branch, LfBus bus1, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return openQ2Array.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createShuntCompensatorActiveFlowEquationTerm(LfShunt shunt, LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return shuntPArray.getElement(shunt.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createShuntCompensatorReactiveFlowEquationTerm(LfShunt shunt, LfBus bus, boolean deriveB, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        return shuntQArray.getElement(shunt.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createDummyActivePowerEquationTerm(LfBranch branch, EquationSystem<AcVariableType, AcEquationType> equationSystem, boolean neg) {
        return neg ? minusDummyPArray.getElement(branch.getNum()) : dummyPArray.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createDummyReactivePowerEquationTerm(LfBranch branch, EquationSystem<AcVariableType, AcEquationType> equationSystem, boolean neg) {
        return neg ? minusDummyQArray.getElement(branch.getNum()) : dummyQArray.getElement(branch.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createHvdcP1(LfHvdc hvdc, VariableSet<AcVariableType> variableSet) {
        return hvdcP1Array.getElement(hvdc.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createHvdcP2(LfHvdc hvdc, VariableSet<AcVariableType> variableSet) {
        return hvdcP2Array.getElement(hvdc.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createLoadModelP(LfLoad load, LfLoadModel loadModel, LfBus bus, VariableSet<AcVariableType> variableSet) {
        return loadModelPArray.getElement(load.getNum());
    }

    @Override
    protected EquationTerm<AcVariableType, AcEquationType> createLoadModelQ(LfLoad load, LfLoadModel loadModel, LfBus bus, VariableSet<AcVariableType> variableSet) {
        return loadModelQArray.getElement(load.getNum());
    }*/
}
