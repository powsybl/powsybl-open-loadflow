package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DisymAcLoadFlowResult {

    private final LfNetwork network;

    private final int outerLoopIterations;

    private final int newtonRaphsonIterations;

    private final NewtonRaphsonStatus newtonRaphsonStatus;

    private final double slackBusActivePowerMismatch;

    private final double distributedActivePower;

    public DisymAcLoadFlowResult(LfNetwork network, int outerLoopIterations, int newtonRaphsonIterations, NewtonRaphsonStatus newtonRaphsonStatus,
                            double slackBusActivePowerMismatch, double distributedActivePower) {
        this.network = Objects.requireNonNull(network);
        this.outerLoopIterations = outerLoopIterations;
        this.newtonRaphsonIterations = newtonRaphsonIterations;
        this.newtonRaphsonStatus = newtonRaphsonStatus;
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
        this.distributedActivePower = distributedActivePower;
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public int getOuterLoopIterations() {
        return outerLoopIterations;
    }

    public int getNewtonRaphsonIterations() {
        return newtonRaphsonIterations;
    }

    public NewtonRaphsonStatus getNewtonRaphsonStatus() {
        return newtonRaphsonStatus;
    }

    public double getSlackBusActivePowerMismatch() {
        return slackBusActivePowerMismatch;
    }

    public double getDistributedActivePower() {
        return distributedActivePower;
    }

    @Override
    public String toString() {
        return "DisymAcLoadFlowResult(outerLoopIterations=" + outerLoopIterations
                + ", newtonRaphsonIterations=" + newtonRaphsonIterations
                + ", newtonRaphsonStatus=" + newtonRaphsonStatus
                + ", slackBusActivePowerMismatch=" + slackBusActivePowerMismatch * PerUnit.SB
                + ", distributedActivePower=" + distributedActivePower * PerUnit.SB
                + ")";
    }
}
