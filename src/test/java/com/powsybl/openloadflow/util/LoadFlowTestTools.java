package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfNetwork;

public class LoadFlowTestTools {
    protected MatrixFactory matrixFactory;
    protected AcLoadFlowParameters acParameters;
    protected LfNetwork lfNetwork;
    protected VariableSet variableSet;
    protected EquationSystem equationSystem;
    protected JacobianMatrix jacobianMatrix;

    public LoadFlowTestTools(Network network) {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        matrixFactory = new SparseMatrixFactory();
        acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, parameters, OpenLoadFlowProvider.getParametersExt(parameters), false);
        lfNetwork = AcloadFlowEngine.createNetworks(network, acParameters).get(0);
        AcEquationSystemCreationParameters acEquationSystemCreationParameters = new AcEquationSystemCreationParameters(true, false, false, true);
        variableSet = new VariableSet();
        equationSystem = AcEquationSystem.create(lfNetwork, variableSet, acEquationSystemCreationParameters);
        jacobianMatrix = JacobianMatrix.create(equationSystem, matrixFactory);
    }

    public AcLoadFlowParameters getAcParameters() {
        return acParameters;
    }

    public LfNetwork getLfNetwork() {
        return lfNetwork;
    }

    public VariableSet getVariableSet() {
        return variableSet;
    }

    public EquationSystem getEquationSystem() {
        return equationSystem;
    }

    public JacobianMatrix getJacobianMatrix() {
        return jacobianMatrix;
    }
}
