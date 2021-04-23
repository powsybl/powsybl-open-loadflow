package com.powsybl.openloadflow.reduction;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.equations.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.SlackBusSelector;

import java.util.Objects;
import java.util.List;

/**
 * @author JB Heyberger <jean-baptiste.heyberger at rte-france.com>
 */
public class ReductionParameters {

    private final SlackBusSelector slackBusSelector;

    private final MatrixFactory matrixFactory;

    private VoltageInitializer voltageInitializer = new PreviousValueVoltageInitializer(); //TODO: check why previous does not work

    private final List<String> externalVoltageLevels;

    public ReductionParameters(SlackBusSelector slackBusSelector, MatrixFactory matrixFactory, List<String> externalVoltageLevels) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.externalVoltageLevels = Objects.requireNonNull(externalVoltageLevels);
    }

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public VoltageInitializer getVoltageInitializer() {
        return voltageInitializer;
    }

    public List<String> getExternalVoltageLevels() {
        return externalVoltageLevels;
    }

}
