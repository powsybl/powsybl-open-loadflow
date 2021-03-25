/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcloadFlowEngine implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final LfNetwork network;

    private final AcLoadFlowParameters parameters;

    private VariableSet variableSet;

    private EquationSystem equationSystem;

    private JacobianMatrix j;

    public AcloadFlowEngine(LfNetwork network, AcLoadFlowParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public static List<LfNetwork> createNetworks(Object network, AcLoadFlowParameters parameters) {
        LfNetworkParameters networkParameters = new LfNetworkParameters(parameters.getSlackBusSelector(),
                                                                        parameters.isVoltageRemoteControl(),
                                                                        parameters.isMinImpedance(),
                                                                        parameters.isTwtSplitShuntAdmittance(),
                                                                        parameters.isBreakers(),
                                                                        parameters.getPlausibleActivePowerLimit(),
                                                                        parameters.isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds());
        return LfNetwork.load(network, networkParameters);
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public AcLoadFlowParameters getParameters() {
        return parameters;
    }

    public VariableSet getVariableSet() {
        return variableSet;
    }

    public EquationSystem getEquationSystem() {
        return equationSystem;
    }

    private void updatePvBusesReactivePower(NewtonRaphsonResult lastNrResult, LfNetwork network, EquationSystem equationSystem) {
        if (lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
            for (LfBus bus : network.getBuses()) {
                if (bus.isVoltageControllerEnabled()) {
                    Equation q = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
                    bus.setCalculatedQ(q.eval());
                } else {
                    bus.setCalculatedQ(Double.NaN);
                }
            }
        }
    }

    private static class RunningContext {

        private NewtonRaphsonResult lastNrResult;

        private final Map<String, MutableInt> outerLoopIterationByType = new HashMap<>();
    }

    private void runOuterLoop(OuterLoop outerLoop, LfNetwork network, EquationSystem equationSystem, VariableSet variableSet,
                              NewtonRaphson newtonRaphson, NewtonRaphsonParameters nrParameters, RunningContext runningContext) {
        // for each outer loop re-run Newton-Raphson until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = runningContext.outerLoopIterationByType.computeIfAbsent(outerLoop.getType(), k -> new MutableInt());

            // check outer loop status
            outerLoopStatus = outerLoop.check(new OuterLoopContext(outerLoopIteration.getValue(), network, runningContext.lastNrResult));

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop iteration {} (name='{}')", outerLoopIteration, outerLoop.getType());

                // if not yet stable, restart Newton-Raphson
                runningContext.lastNrResult = newtonRaphson.run(nrParameters);
                if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                    return;
                }

                // update PV buses reactive power some outer loops might need this information
                updatePvBusesReactivePower(runningContext.lastNrResult, network, equationSystem);

                outerLoopIteration.increment();
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE);
    }

    public AcLoadFlowResult run() {
        if (equationSystem == null) {
            LOGGER.info("Start AC loadflow on network {}", network.getNum());

            variableSet = new VariableSet();
            AcEquationSystemCreationParameters creationParameters = new AcEquationSystemCreationParameters(
                    parameters.isPhaseControl(), parameters.isTransformerVoltageControlOn(), parameters.isShuntVoltageControlOn(),
                    parameters.isForceA1Var(), parameters.getBranchesWithCurrent());
            equationSystem = AcEquationSystem.create(network, variableSet, creationParameters);
            j = new JacobianMatrix(equationSystem, parameters.getMatrixFactory());
        } else {
            LOGGER.info("Restart AC loadflow on network {}", network.getNum());
        }

        RunningContext runningContext = new RunningContext();
        NewtonRaphson newtonRaphson = new NewtonRaphson(network, parameters.getMatrixFactory(), equationSystem, j, parameters.getStoppingCriteria());

        NewtonRaphsonParameters nrParameters = new NewtonRaphsonParameters().setVoltageInitializer(parameters.getVoltageInitializer());

        // run initial Newton-Raphson
        runningContext.lastNrResult = newtonRaphson.run(nrParameters);

        // continue with outer loops only if initial Newton-Raphson succeed
        if (runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
            updatePvBusesReactivePower(runningContext.lastNrResult, network, equationSystem);

            // re-run all outer loops until Newton-Raphson failed or no more Newton-Raphson iterations are needed
            int oldIterationCount;
            do {
                oldIterationCount = runningContext.lastNrResult.getIteration();

                // outer loops are nested: inner most loop first in the list, outer most loop last
                for (OuterLoop outerLoop : parameters.getOuterLoops()) {
                    runOuterLoop(outerLoop, network, equationSystem, variableSet, newtonRaphson, nrParameters, runningContext);

                    // continue with next outer loop only if last Newton-Raphson succeed
                    if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                        break;
                    }
                }
            } while (runningContext.lastNrResult.getIteration() > oldIterationCount
                    && runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED);
        }

        int nrIterations = runningContext.lastNrResult.getIteration();
        int outerLoopIterations = runningContext.outerLoopIterationByType.values().stream().mapToInt(MutableInt::getValue).sum() + 1;

        AcLoadFlowResult result = new AcLoadFlowResult(network, outerLoopIterations, nrIterations, runningContext.lastNrResult.getStatus(),
                runningContext.lastNrResult.getSlackBusActivePowerMismatch());

        LOGGER.info("Ac loadflow complete on network {} (result={})", network.getNum(), result);

        return result;
    }

    @Override
    public void close() {
        if (j != null) {
            j.close();
        }
    }

    public static List<AcLoadFlowResult> run(Object network, AcLoadFlowParameters parameters) {
        return createNetworks(network, parameters)
                .stream()
                .map(n -> {
                    if (n.isValid()) {
                        try (AcloadFlowEngine engine = new AcloadFlowEngine(n, parameters)) {
                            return engine.run();
                        }
                    }
                    return new AcLoadFlowResult(n, 0, 0, NewtonRaphsonStatus.NO_CALCULATION, Double.NaN);
                })
                .collect(Collectors.toList());
    }
}
