package com.powsybl.openloadflow;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.ac.solver.AcSolverType;

public class CompareKnitroToNewtonRaphson {

    public LoadFlow.Runner loadFlowRunner;
    public LoadFlowParameters parameters;
    public OpenLoadFlowParameters parametersExt;
    public Network network;

    public CompareKnitroToNewtonRaphson(LoadFlow.Runner loadFlowRunner, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, Network network) {
        this.loadFlowRunner = loadFlowRunner;
        this.parameters = parameters;
        this.parametersExt = parametersExt;
        this.network = network;
    }

    public static LoadFlowResult runComparison(LoadFlow.Runner loadFlowRunner, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt, Network network) {
        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        parametersExt.setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.NONE);
        parametersExt.setAcSolverType(AcSolverType.NEWTON_RAPHSON);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        return result;
    }
}
