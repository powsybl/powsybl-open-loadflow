package com.powsybl.openloadflow.ac.outerloop;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
import com.powsybl.math.matrix.MatrixFactory;
//import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.equations.DisymAcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DisymAcLoadFlowParameters {

    private final LfNetworkParameters networkParameters;

    private final DisymAcEquationSystemCreationParameters equationSystemCreationParameters;

    private final NewtonRaphsonParameters newtonRaphsonParameters;

    private final List<OuterLoop> outerLoops;

    private final MatrixFactory matrixFactory;

    private VoltageInitializer voltageInitializer;

    public DisymAcLoadFlowParameters(LfNetworkParameters networkParameters, DisymAcEquationSystemCreationParameters equationSystemCreationParameters,
                                     NewtonRaphsonParameters newtonRaphsonParameters, List<OuterLoop> outerLoops, MatrixFactory matrixFactory,
                                     VoltageInitializer voltageInitializer) {
        this.networkParameters = Objects.requireNonNull(networkParameters);
        this.equationSystemCreationParameters = Objects.requireNonNull(equationSystemCreationParameters);
        this.newtonRaphsonParameters = Objects.requireNonNull(newtonRaphsonParameters);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
    }

    public LfNetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    public DisymAcEquationSystemCreationParameters getEquationSystemCreationParameters() {
        return equationSystemCreationParameters;
    }

    public NewtonRaphsonParameters getNewtonRaphsonParameters() {
        return newtonRaphsonParameters;
    }

    public List<OuterLoop> getOuterLoops() {
        return outerLoops;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public VoltageInitializer getVoltageInitializer() {
        return voltageInitializer;
    }

    public void setVoltageInitializer(VoltageInitializer voltageInitializer) {
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
    }

    @Override
    public String toString() {
        return "DiSymAcLoadFlowParameters(" +
                "networkParameters=" + networkParameters +
                ", equationSystemCreationParameters=" + equationSystemCreationParameters +
                ", newtonRaphsonParameters=" + newtonRaphsonParameters +
                ", outerLoops=" + outerLoops.stream().map(outerLoop -> outerLoop.getClass().getSimpleName()).collect(Collectors.toList()) +
                ", matrixFactory=" + matrixFactory.getClass().getSimpleName() +
                ", voltageInitializer=" + voltageInitializer.getClass().getSimpleName() +
                ')';
    }
}
