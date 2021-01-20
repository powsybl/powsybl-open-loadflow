package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

public class NetworkEquationSystemAcLoadFlowObserver extends DefaultAcLoadFlowObserver {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkEquationSystemAcLoadFlowObserver.class);

    private void logEquations(Equation equation, LfNetwork lfNetwork) {
        LOGGER.trace("     - equation {} with colJacobian = {} having {} terms :",
                equation.getType(), equation.getColumn(), equation.getTerms().size());
        for (EquationTerm equationTerm : equation.getTerms()) {
            LOGGER.trace("       * term {} {} on {} having {} variables : {}",
                    equationTerm.getClass().getSimpleName(), equationTerm.isActive() ? "active" : "inactive",
                    equationTerm.getSubjectType(), equationTerm.getVariables().size(), logVariable(lfNetwork, equationTerm));
        }
    }

    private String logVariable(LfNetwork lfNetwork, EquationTerm equationTerm) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Variable variable : equationTerm.getVariables()) {
            stringBuilder.append(stringBuilder.length() != 0 ? ", " : "");
            stringBuilder.append(variable.getType());
            stringBuilder.append(variable.isActive() ? " active" : " inactive");
            if (variable.getType() == VariableType.BRANCH_ALPHA1 || variable.getType() == VariableType.BRANCH_RHO1) {
                stringBuilder.append(" on branch ");
                stringBuilder.append(lfNetwork.getBranch(variable.getNum()).getId());
            } else {
                stringBuilder.append(" on bus ");
                stringBuilder.append(lfNetwork.getBus(variable.getNum()).getId());
            }
            stringBuilder.append(" with rowJacobian = ");
            stringBuilder.append(variable.getRow());
        }
        return stringBuilder.toString();
    }

    @Override
    public void afterEquationVectorCreation(double[] fx, EquationSystem equationSystem, int iteration) {
        if (LOGGER.isTraceEnabled()) {
            LfNetwork lfNetwork = equationSystem.getNetwork();
            Map<Pair<Integer, EquationType>, Equation> equations = equationSystem.getEquations();
            Map<LfBranch, List<Equation>> equationsByBranch = new LinkedHashMap<>();
            Map<LfBus, List<Equation>> equationsByBus = new LinkedHashMap<>();
            buildEquationCollections(lfNetwork, equations, equationsByBranch, equationsByBus);
            Comparator<Equation> equationComparator = Comparator.comparing(equation -> equation.getType().name());
            LOGGER.trace(">>> EquationSystem with {} branch equations and {} bus equations", equationsByBranch.size(), equationsByBus.size());
            logBusEquations(lfNetwork, equationsByBus, equationComparator);
            logBranchesEquations(lfNetwork, equationsByBranch, equationComparator);
        }
    }

    private void logBranchesEquations(LfNetwork lfNetwork, Map<LfBranch, List<Equation>> equationsByBranch, Comparator<Equation> equationComparator) {
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            LOGGER.trace("  Equations on branch {} :", lfBranch.getId());
            if (!equationsByBranch.containsKey(lfBranch)) {
                LOGGER.trace("    N/A");
            } else {
                equationsByBranch.get(lfBranch).sort(equationComparator);
                LOGGER.trace("    ACTIVE");
                for (Equation equation : equationsByBranch.get(lfBranch)) {
                    if (equation.isActive()) {
                        logEquations(equation, lfNetwork);
                    }
                }
                LOGGER.trace("    INACTIVE");
                for (Equation equation : equationsByBranch.get(lfBranch)) {
                    if (!equation.isActive()) {
                        logEquations(equation, lfNetwork);
                    }
                }
            }
        }
    }

    private void logBusEquations(LfNetwork lfNetwork, Map<LfBus, List<Equation>> equationsByBus, Comparator<Equation> equationComparator) {
        for (LfBus lfBus : lfNetwork.getBuses()) {
            LOGGER.trace("  Equations on bus {} :", lfBus.getId());
            if (!equationsByBus.containsKey(lfBus)) {
                LOGGER.trace("    N/A");
            } else {
                equationsByBus.get(lfBus).sort(equationComparator);
                LOGGER.trace("    ACTIVE");
                for (Equation equation : equationsByBus.get(lfBus)) {
                    if (equation.isActive()) {
                        logEquations(equation, lfNetwork);
                    }
                }
                LOGGER.trace("    INACTIVE");
                for (Equation equation : equationsByBus.get(lfBus)) {
                    if (!equation.isActive()) {
                        logEquations(equation, lfNetwork);
                    }
                }
            }
        }
    }

    private void buildEquationCollections(LfNetwork lfNetwork, Map<Pair<Integer, EquationType>, Equation> equations, Map<LfBranch, List<Equation>> equationsByBranch, Map<LfBus, List<Equation>> equationsByBus) {
        for (Map.Entry<Pair<Integer, EquationType>, Equation> equationByNumAndType : equations.entrySet()) {
            Pair<Integer, EquationType> numAndType = equationByNumAndType.getKey();
            switch (numAndType.getValue().getSubjectType()) {
                case BUS:
                    equationsByBus.computeIfAbsent(lfNetwork.getBus(numAndType.getKey()), busNum -> new ArrayList<>()).add(equationByNumAndType.getValue());
                    break;
                case BRANCH:
                    equationsByBranch.computeIfAbsent(lfNetwork.getBranch(numAndType.getKey()), branchNum -> new ArrayList<>()).add(equationByNumAndType.getValue());
                    break;
                case SHUNT_COMPENSATOR:
                    break;
            }
        }
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
            LOGGER.trace(">>> Jacobian matrix : {}{}", System.getProperty("line.separator"), stringBuilder);
        }
    }

    @Override
    public void beforeLoadFlow(LfNetwork network) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(">>> LfNetwork with {} buses", network.getBuses().size());
            for (LfBus lfBus : network.getBuses()) {
                LOGGER.trace("  Bus {} from VoltageLevel {} with NominalVoltage = {} ", lfBus.getId(), lfBus.getVoltageLevelId(), lfBus.getNominalV());
                logGenerators(lfBus);
                for (Load load : lfBus.getLoads()) {
                    LOGGER.trace("    Load {} with : P0 = {} ; Q0 = {}",
                            load.getId(), load.getP0(), load.getQ0());
                }
                for (LfShunt lfShunt : lfBus.getShunts()) {
                    LOGGER.trace("    Shunt {} with : B = {}", lfShunt.getId(), lfShunt.getB());
                }
                logBranches(lfBus);
            }
        }
    }

    private void logBranches(LfBus lfBus) {
        for (LfBranch lfBranch : lfBus.getBranches()) {
            PiModel piModel = lfBranch.getPiModel();
            double zb = lfBus.getNominalV() * lfBus.getNominalV() / PerUnit.SB;
            LOGGER.trace("    Line {} with : bus1 = {}, bus2 = {}, R = {}, X = {}, G1 = {}, G2 = {}, B1 = {}, B2 = {}",
                    lfBranch.getId(), lfBranch.getBus1() != null ? lfBranch.getBus1().getId() : "NaN",
                    lfBranch.getBus2() != null ? lfBranch.getBus2().getId() : "NaN",
                    piModel.getR() * zb, piModel.getX() * zb, piModel.getG1() / zb, piModel.getG2() / zb, piModel.getB1() / zb, piModel.getB2() / zb);
        }
    }

    private void logGenerators(LfBus lfBus) {
        for (LfGenerator lfGenerator : lfBus.getGenerators()) {
            if (lfGenerator instanceof LfStaticVarCompensatorImpl) {
                LfStaticVarCompensatorImpl lfStaticVarCompensator = (LfStaticVarCompensatorImpl) lfGenerator;
                VoltagePerReactivePowerControl voltagePerReactivePowerControl = lfStaticVarCompensator.getVoltagePerReactivePowerControl();
                double slope = Double.NaN;
                if (voltagePerReactivePowerControl != null) {
                    slope = voltagePerReactivePowerControl.getSlope();
                }
                LOGGER.trace("    Static Var Compensator {} with : Bmin = {} ; Bmax = {} ; VoltageRegulatorOn = {} ; VoltageSetpoint = {} : Slope = {}",
                        lfGenerator.getId(), lfStaticVarCompensator.getSvc().getBmin(),
                        lfStaticVarCompensator.getSvc().getBmax(), lfStaticVarCompensator.hasVoltageControl(),
                        lfStaticVarCompensator.getSvc().getVoltageSetpoint(), slope);
            } else {
                LOGGER.trace("    Generator {} with : TargetP = {} ; MinP = {} ; MaxP = {} ; VoltageRegulatorOn = {} ; TargetQ = {}",
                        lfGenerator.getId(), lfGenerator.getTargetP() * PerUnit.SB, lfGenerator.getMinP() * PerUnit.SB,
                        lfGenerator.getMaxP() * PerUnit.SB, lfGenerator.hasVoltageControl(), lfGenerator.getTargetQ() * PerUnit.SB);
            }
        }
    }
}
