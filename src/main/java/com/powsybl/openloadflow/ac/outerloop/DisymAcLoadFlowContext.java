package com.powsybl.openloadflow.ac.outerloop;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.DisymAcEquationSystem;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

public class DisymAcLoadFlowContext implements AutoCloseable {

    private final LfNetwork network;

    private final DisymAcLoadFlowParameters parameters;

    private EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private JacobianMatrix<AcVariableType, AcEquationType> jacobianMatrix;

    private AcTargetVector targetVector;

    private EquationVector<AcVariableType, AcEquationType> equationVector;

    public DisymAcLoadFlowContext(LfNetwork network, DisymAcLoadFlowParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public DisymAcLoadFlowParameters getParameters() {
        return parameters;
    }

    public EquationSystem<AcVariableType, AcEquationType> getEquationSystem() {
        if (equationSystem == null) {
            // test from AcEquationSystem to DisymAcEquationSystem
            //equationSystem = AcEquationSystem.create(network, parameters.getEquationSystemCreationParameters());
            equationSystem = DisymAcEquationSystem.create(network, parameters.getEquationSystemCreationParameters());

        }
        return equationSystem;
    }

    public JacobianMatrix<AcVariableType, AcEquationType> getJacobianMatrix() {
        if (jacobianMatrix == null) {
            jacobianMatrix = new JacobianMatrix<>(getEquationSystem(), parameters.getMatrixFactory());
        }
        return jacobianMatrix;
    }

    public TargetVector<AcVariableType, AcEquationType> getTargetVector() {
        if (targetVector == null) {
            targetVector = new AcTargetVector(network, getEquationSystem());
        }
        return targetVector;
    }

    public EquationVector<AcVariableType, AcEquationType> getEquationVector() {
        if (equationVector == null) {
            equationVector = new EquationVector<>(getEquationSystem());
        }
        return equationVector;
    }

    @Override
    public void close() {
        if (jacobianMatrix != null) {
            jacobianMatrix.close();
        }
    }
}
