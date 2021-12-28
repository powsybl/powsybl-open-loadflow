/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcloadFlowEngine implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final LfNetwork network;

    private final AcLoadFlowParameters parameters;

    private EquationSystem<AcVariableType, AcEquationType> equationSystem;

    private JacobianMatrix<AcVariableType, AcEquationType> j;

    private TargetVector<AcVariableType, AcEquationType> targetVector;

    public AcloadFlowEngine(LfNetwork network, AcLoadFlowParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public AcLoadFlowParameters getParameters() {
        return parameters;
    }

    public EquationSystem<AcVariableType, AcEquationType> getEquationSystem() {
        return equationSystem;
    }

    private void updatePvBusesReactivePower(NewtonRaphsonResult lastNrResult, LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        if (lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
            for (LfBus bus : network.getBuses()) {
                if (bus.isVoltageControllerEnabled()) {
                    Equation<AcVariableType, AcEquationType> q = equationSystem.createEquation(bus.getNum(), AcEquationType.BUS_TARGET_Q);
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

    private void runOuterLoop(OuterLoop outerLoop, LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                              NewtonRaphson newtonRaphson, RunningContext runningContext, Reporter reporter) {
        Reporter olReporter = reporter.createSubReporter("OuterLoop", "Outer loop ${outerLoopType}", "outerLoopType", outerLoop.getType());

        // for each outer loop re-run Newton-Raphson until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = runningContext.outerLoopIterationByType.computeIfAbsent(outerLoop.getType(), k -> new MutableInt());

            // check outer loop status
            outerLoopStatus = outerLoop.check(new OuterLoopContext(outerLoopIteration.getValue(), network, runningContext.lastNrResult), olReporter);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop iteration {} (name='{}')", outerLoopIteration, outerLoop.getType());

                // if not yet stable, restart Newton-Raphson
                runningContext.lastNrResult = newtonRaphson.run(reporter);
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
        return run(Reporter.NO_OP);
    }

    private static double getBusTargetV(LfBus bus) {
        Objects.requireNonNull(bus);
        return bus.getDiscreteVoltageControl().filter(dvc -> bus.isDiscreteVoltageControlled())
                .map(DiscreteVoltageControl::getTargetValue)
                .orElse(getVoltageControlledTargetValue(bus).orElse(Double.NaN));
    }

    private static Optional<Double> getVoltageControlledTargetValue(LfBus bus) {
        return bus.getVoltageControl().filter(vc -> bus.isVoltageControlled()).map(vc -> {
            if (vc.getControllerBuses().stream().noneMatch(LfBus::isVoltageControllerEnabled)) {
                throw new IllegalStateException("None of the controller buses of bus '" + bus.getId() + "'has voltage control on");
            }
            return vc.getTargetValue();
        });
    }

    private static double getReactivePowerDistributionTarget(LfNetwork network, int num, DistributionData data) {
        LfBus controllerBus = network.getBus(num);
        LfBus firstControllerBus = network.getBus(data.getFirstControllerElementNum());
        double c = data.getC();
        return c * (controllerBus.getLoadTargetQ() - controllerBus.getGenerationTargetQ())
                - firstControllerBus.getLoadTargetQ() - firstControllerBus.getGenerationTargetQ();
    }

    private static double getRho1DistributionTarget(LfNetwork network, int num, DistributionData data) {
        LfBranch controllerBranch = network.getBranch(num);
        LfBranch firstControllerBranch = network.getBranch(data.getFirstControllerElementNum());
        // as a first and very simple ratio distribution strategy, we keep the gap between the 2 ratios constant
        return controllerBranch.getPiModel().getR1() - firstControllerBranch.getPiModel().getR1();
    }

    private static double createBusWithSlopeTarget(LfBus bus, DistributionData data) {
        double slope = data.getC();
        return getBusTargetV(bus) - slope * (bus.getLoadTargetQ() - bus.getGenerationTargetQ());
    }

    private static double getReactivePowerControlTarget(LfBranch branch) {
        Objects.requireNonNull(branch);
        return branch.getReactivePowerControl().map(ReactivePowerControl::getTargetValue)
            .orElseThrow(() -> new PowsyblException("Branch '" + branch.getId() + "' has no target in for reactive remote control"));
    }

    public static void initTarget(Equation<AcVariableType, AcEquationType> equation, LfNetwork network, double[] targets) {
        switch (equation.getType()) {
            case BUS_TARGET_P:
                targets[equation.getColumn()] = network.getBus(equation.getElementNum()).getTargetP();
                break;

            case BUS_TARGET_Q:
                targets[equation.getColumn()] = network.getBus(equation.getElementNum()).getTargetQ();
                break;

            case BUS_TARGET_V:
                targets[equation.getColumn()] = getBusTargetV(network.getBus(equation.getElementNum()));
                break;

            case BUS_TARGET_V_WITH_SLOPE:
                targets[equation.getColumn()] = createBusWithSlopeTarget(network.getBus(equation.getElementNum()), equation.getData());
                break;

            case BUS_TARGET_PHI:
                targets[equation.getColumn()] = 0;
                break;

            case BRANCH_TARGET_P:
                targets[equation.getColumn()] = LfBranch.getDiscretePhaseControlTarget(network.getBranch(equation.getElementNum()), DiscretePhaseControl.Unit.MW);
                break;

            case BRANCH_TARGET_Q:
                targets[equation.getColumn()] = getReactivePowerControlTarget(network.getBranch(equation.getElementNum()));
                break;

            case BRANCH_TARGET_ALPHA1:
                targets[equation.getColumn()] = network.getBranch(equation.getElementNum()).getPiModel().getA1();
                break;

            case BRANCH_TARGET_RHO1:
                targets[equation.getColumn()] = network.getBranch(equation.getElementNum()).getPiModel().getR1();
                break;

            case DISTR_Q:
                targets[equation.getColumn()] = getReactivePowerDistributionTarget(network, equation.getElementNum(), equation.getData());
                break;

            case ZERO_V:
                targets[equation.getColumn()] = 0;
                break;

            case ZERO_PHI:
                targets[equation.getColumn()] = LfBranch.getA(network.getBranch(equation.getElementNum()));
                break;

            case DISTR_RHO:
                targets[equation.getColumn()] = getRho1DistributionTarget(network, equation.getElementNum(), equation.getData());
                break;

            default:
                throw new IllegalStateException("Unknown state variable type: "  + equation.getType());
        }

        for (EquationTerm<AcVariableType, AcEquationType> term : equation.getTerms()) {
            if (term.isActive() && term.hasRhs()) {
                targets[equation.getColumn()] -= term.rhs();
            }
        }
    }

    public AcLoadFlowResult run(Reporter reporter) {
        if (equationSystem == null) {
            LOGGER.info("Start AC loadflow on network {}", network);

            equationSystem = AcEquationSystem.create(network, parameters.getNetworkParameters(), parameters.getEquationSystemCreationParameters());
            j = new JacobianMatrix<>(equationSystem, parameters.getMatrixFactory());
            targetVector = new TargetVector<>(network, equationSystem, AcloadFlowEngine::initTarget);
        } else {
            LOGGER.info("Restart AC loadflow on network {}", network);
        }

        RunningContext runningContext = new RunningContext();
        NewtonRaphson newtonRaphson = new NewtonRaphson(network, parameters.getNewtonRaphsonParameters(), equationSystem, j, targetVector);

        // run initial Newton-Raphson
        runningContext.lastNrResult = newtonRaphson.run(reporter);

        // continue with outer loops only if initial Newton-Raphson succeed
        if (runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
            updatePvBusesReactivePower(runningContext.lastNrResult, network, equationSystem);

            // outer loops initialization
            for (OuterLoop outerLoop : parameters.getOuterLoops()) {
                outerLoop.initialize(network);
            }

            // re-run all outer loops until Newton-Raphson failed or no more Newton-Raphson iterations are needed
            int oldIterationCount;
            do {
                oldIterationCount = runningContext.lastNrResult.getIteration();

                // outer loops are nested: inner most loop first in the list, outer most loop last
                for (OuterLoop outerLoop : parameters.getOuterLoops()) {
                    runOuterLoop(outerLoop, network, equationSystem, newtonRaphson, runningContext, reporter);

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

        LOGGER.info("Ac loadflow complete on network {} (result={})", network, result);

        return result;
    }

    @Override
    public void close() {
        if (j != null) {
            j.close();
        }
    }

    public static <T> List<AcLoadFlowResult> run(T network, LfNetworkLoader<T> networkLoader, AcLoadFlowParameters parameters, Reporter reporter) {
        return LfNetwork.load(network, networkLoader, parameters.getNetworkParameters(), reporter)
                .stream()
                .map(n -> {
                    if (n.isValid()) {
                        try (AcloadFlowEngine engine = new AcloadFlowEngine(n, parameters)) {
                            return engine.run(reporter);
                        }
                    }
                    return new AcLoadFlowResult(n, 0, 0, NewtonRaphsonStatus.NO_CALCULATION, Double.NaN);
                })
                .collect(Collectors.toList());
    }
}
