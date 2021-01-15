/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.iidm.network.Load;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DefaultAcLoadFlowObserver implements AcLoadFlowObserver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAcLoadFlowObserver.class);

    @Override
    public void beforeNetworksCreation() {
        // empty
    }

    @Override
    public void afterNetworksCreation(List<LfNetwork> networks) {
        // empty
    }

    @Override
    public void beforeEquationSystemCreation() {
        // empty
    }

    @Override
    public void afterEquationSystemCreation() {
        // empty
    }

    @Override
    public void beforeOuterLoopBody(int outerLoopIteration, String outerLoopName) {
        // empty
    }

    @Override
    public void beforeVoltageInitializerPreparation(Class<?> voltageInitializerClass) {
        // empty
    }

    @Override
    public void afterVoltageInitializerPreparation() {
        // empty
    }

    @Override
    public void beforeStateVectorCreation(int iteration) {
        // empty
    }

    @Override
    public void afterStateVectorCreation(double[] x, int iteration) {
        // empty
    }

    @Override
    public void beginIteration(int iteration) {
        // empty
    }

    @Override
    public void beforeStoppingCriteriaEvaluation(double[] mismatch, EquationSystem equationSystem, int iteration) {
        // empty
    }

    @Override
    public void afterStoppingCriteriaEvaluation(double norm, int iteration) {
        // empty
    }

    @Override
    public void beforeEquationsUpdate(int iteration) {
        // empty
    }

    @Override
    public void afterEquationsUpdate(EquationSystem equationSystem, int iteration) {
        // empty
    }

    @Override
    public void beforeEquationVectorCreation(int iteration) {
        // empty
    }

    @Override
    public void afterEquationVectorCreation(double[] fx, EquationSystem equationSystem, int iteration) {
        if (LOGGER.isTraceEnabled()) {
            LfNetwork lfNetwork = equationSystem.getNetwork();
            NavigableMap<Equation, NavigableMap<Variable, List<EquationTerm>>> equationNavigableMapNavigableMap = equationSystem.getSortedEquationsToSolve();
            Map<LfBus, List<Equation>> equationsByBus = new LinkedHashMap<>();
            for (Equation equation : equationNavigableMapNavigableMap.keySet()) {
                equationsByBus.computeIfAbsent(lfNetwork.getBus(equation.getNum()), bus -> new ArrayList<>()).add(equation);
            }
            LOGGER.trace(">>> EquationSystem with {} equations", equationNavigableMapNavigableMap.size());
            for (LfBus lfBus : equationsByBus.keySet()) {
                LOGGER.trace("  Equations on bus {} :", lfBus.getId());
                for (Equation equation : equationsByBus.get(lfBus)) {
                    LOGGER.trace("   - equation (type = {}) having terms :", equation.getType());
                    for (EquationTerm equationTerm : equation.getTerms()) {
                        StringBuilder stringBuilder = new StringBuilder();
                        for (Variable variable : equationTerm.getVariables()) {
                            stringBuilder.append((stringBuilder.length() != 0 ? ", " : "") + variable.getType() + " (active = " + variable.isActive() + "; bus = " + lfNetwork.getBus(variable.getNum()).getId() + ")");
                        }
                        LOGGER.trace("     * term {} (active = {}; type = {}) having variables : {}",
                                equationTerm.getClass().getSimpleName(), equationTerm.isActive(), equationTerm.getSubjectType(), stringBuilder.toString());
                    }
                }
            }
        }
    }

    @Override
    public void beforeJacobianBuild(int iteration) {
        // empty
    }

    @Override
    public void afterJacobianBuild(Matrix j, EquationSystem equationSystem, int iteration) {
        if (LOGGER.isTraceEnabled()) {
            StringBuilder stringBuilder = new StringBuilder();
            j.print(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    stringBuilder.append((char) b);
                }
            }));
            LOGGER.trace(">>> Jacobian matrix : {}{}", System.getProperty("line.separator"), stringBuilder.toString());
        }
    }

    @Override
    public void beforeLuDecomposition(int iteration) {
        // empty
    }

    @Override
    public void afterLuDecomposition(int iteration) {
        // empty
    }

    @Override
    public void beforeLuSolve(int iteration) {
        // empty
    }

    @Override
    public void afterLuSolve(int iteration) {
        // empty
    }

    @Override
    public void endIteration(int iteration) {
        // empty
    }

    @Override
    public void beforeOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName) {
        // empty
    }

    @Override
    public void afterOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName, boolean stable) {
        // empty
    }

    @Override
    public void afterOuterLoopBody(int outerLoopIteration, String outerLoopName) {
        // empty
    }

    @Override
    public void beforeNetworkUpdate() {
        // empty
    }

    @Override
    public void afterNetworkUpdate(LfNetwork network) {
        // empty
    }

    @Override
    public void beforePvBusesReactivePowerUpdate() {
        // empty
    }

    @Override
    public void afterPvBusesReactivePowerUpdate() {
        // empty
    }

    @Override
    public void beforeLoadFlow(LfNetwork network) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(">>> LfNetwork with {} buses", network.getBuses().size());
            for (LfBus lfBus : network.getBuses()) {
                LOGGER.trace("  Bus {} from VoltageLevel {} with NominalVoltage = {} ", lfBus.getId(), lfBus.getVoltageLevelId(), lfBus.getNominalV());
                for (LfGenerator lfGenerator : lfBus.getGenerators()) {
                    if (lfGenerator instanceof LfStaticVarCompensatorImpl) {
                        LfStaticVarCompensatorImpl lfStaticVarCompensator = (LfStaticVarCompensatorImpl) lfGenerator;
                        LOGGER.trace("    Static Var Compensator {} with : Bmin = {} ; Bmax = {} ; VoltageRegulatorOn = {} ; VoltageSetpoint = {}",
                                lfGenerator.getId(), lfStaticVarCompensator.getSvc().getBmin(),
                                lfStaticVarCompensator.getSvc().getBmax(), lfStaticVarCompensator.hasVoltageControl(), lfStaticVarCompensator.getSvc().getVoltageSetpoint());
                    } else {
                        LOGGER.trace("    Generator {} with : TargetP = {} ; MinP = {} ; MaxP = {} ; VoltageRegulatorOn = {} ; TargetQ = {}",
                                lfGenerator.getId(), lfGenerator.getTargetP() * PerUnit.SB, lfGenerator.getMinP() * PerUnit.SB,
                                lfGenerator.getMaxP() * PerUnit.SB, lfGenerator.hasVoltageControl(), lfGenerator.getTargetQ() * PerUnit.SB);
                    }
                }
                for (Load load : lfBus.getLoads()) {
                    LOGGER.trace("    Load {} with : P0 = {} ; Q0 = {}",
                            load.getId(), load.getP0(), load.getQ0());
                }
                for (LfShunt lfShunt : lfBus.getShunts()) {
                    LOGGER.trace("    Shunt {} with : B = {}", lfShunt.getId(), lfShunt.getB());
                }
                for (LfBranch lfBranch : lfBus.getBranches()) {
                    PiModel piModel = lfBranch.getPiModel();
                    double zb = lfBus.getNominalV() * lfBus.getNominalV() / PerUnit.SB;
                    LOGGER.trace("    Line {} with : bus1 = {}, bus2 = {}, R = {}, X = {}, G1 = {}, G2 = {}, B1 = {}, B2 = {}",
                            lfBranch.getId(), lfBranch.getBus1() != null ? lfBranch.getBus1().getId() : "NaN",
                            lfBranch.getBus2() != null ? lfBranch.getBus2().getId() : "NaN",
                            piModel.getR() * zb, piModel.getX() * zb, piModel.getG1() / zb, piModel.getG2() / zb, piModel.getB1() / zb, piModel.getB2() / zb);
                }
            }
        }
    }

    @Override
    public void afterLoadFlow(LfNetwork network) {
        // empty
    }
}
