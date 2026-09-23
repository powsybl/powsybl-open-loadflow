/*
 * Copyright (c) 2020-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.action.Action;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.NetworkCache;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.sensi.mt.BufferedFactorReader;
import com.powsybl.openloadflow.sensi.mt.SequentialSensitivityResultWriter;
import com.powsybl.openloadflow.util.Derivable;
import com.powsybl.openloadflow.util.Indexed;
import com.powsybl.openloadflow.util.Lists2;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.openloadflow.util.mt.ContingencyMultiThreadHelper;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gael Macherel {@literal <gael.macherel at artelys.com>}
 */
public class AcSensitivityAnalysis extends AbstractSensitivityAnalysis<AcVariableType, AcEquationType> {

    public AcSensitivityAnalysis(MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory, SensitivityAnalysisParameters parameters) {
        super(matrixFactory, connectivityFactory, parameters);
    }

    private void calculateSensitivityValues(List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors,
                                            SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups, DenseMatrix factorsState,
                                            int contingencyIndex, int operatorStrategyIndex, SensitivityResultWriter resultWriter) {
        Set<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactorsSet = new HashSet<>(lfFactors);

        for (SensitivityFactorGroup<AcVariableType, AcEquationType> factorGroup : factorGroups.getList()) {
            for (LfSensitivityFactor<AcVariableType, AcEquationType> factor : factorGroup.getFactors()) {
                if (!lfFactorsSet.contains(factor)) {
                    continue;
                }
                double sensi;
                double ref;
                if (factor.getSensitivityValuePredefinedResult() != null) {
                    sensi = factor.getSensitivityValuePredefinedResult();
                } else {
                    if (!factor.getFunctionEquationTerm().isActive()) {
                        throw new PowsyblException("Found an inactive equation for a factor that has no predefined result");
                    }
                    sensi = factor.getFunctionEquationTerm().calculateSensi(factorsState, factorGroup.getIndex());
                    // Add the direct term (explicit dependence of the function on the variable), if any
                    sensi += computeParameterDirectPartial(factor);
                    // unscale the sensitivity computed in per unit
                    sensi = unscaleSensitivity(factor, sensi);
                }
                if (factor.getFunctionPredefinedResult() != null) {
                    ref = factor.getFunctionPredefinedResult();
                } else {
                    ref = unscaleFunction(factor, factor.getFunctionReference());
                }
                if (!filterSensitivityValue(sensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                    resultWriter.writeSensitivityValue(factor.getIndex(), contingencyIndex, operatorStrategyIndex, sensi, ref);
                }
            }
        }
    }

    /**
     * Direct term of a sensitivity factor: the explicit dependence of the monitored function on the variable,
     * to be added to the indirect term flowing through the Jacobian. A direct term exists only when the function
     * depends explicitly (not only through the state) on the variable, i.e. for self-sensitivity where the
     * variable element and the function element are the same. Today only branch parameters (R / X / Y) contribute;
     * every other variable type returns 0.
     */
    private static double computeParameterDirectPartial(LfSensitivityFactor<AcVariableType, AcEquationType> factor) {
        if (!(factor instanceof SingleVariableLfSensitivityFactor<AcVariableType, AcEquationType> singleFactor)) {
            return 0;
        }
        SensitivityVariableType varType = factor.getVariableType();
        if (varType != SensitivityVariableType.BRANCH_RESISTANCE && varType != SensitivityVariableType.BRANCH_REACTANCE
                && varType != SensitivityVariableType.BRANCH_ADMITTANCE) {
            return 0; // only branch parameters have a direct term; other variables act only through the state
        }
        LfBranch branch = (LfBranch) singleFactor.getVariableElement();
        LfElement functionElement = factor.getFunctionElement();
        SensitivityFunctionType functionType = factor.getFunctionType();
        if (branch == functionElement) {
            // self-sensitivity: the monitored flow/current is on the very branch being perturbed
            return computeBranchFunctionDirectPartial(branch, functionType, varType);
        }
        if (functionType == SensitivityFunctionType.BUS_REACTIVE_POWER && functionElement instanceof LfBus bus) {
            // Bus reactive injection has a direct term only when the perturbed branch is incident to that bus.
            // The injection is the opposite of the reactive power leaving the bus into the branch, hence the minus.
            double[] partials = SingleVariableFactorGroup.computeBranchFlowParameterPartials(branch, varType);
            if (partials == null) {
                return 0;
            }
            if (branch.getBus1() == bus) {
                return -partials[1]; // -dq1/du
            } else if (branch.getBus2() == bus) {
                return -partials[3]; // -dq2/du
            }
        }
        return 0;
    }

    /**
     * Direct partial ∂F/∂u of a branch function F (active/reactive flow or current magnitude) w.r.t. one of the
     * branch's own parameters u (R / X / Y), at the converged operating point. Built from the four branch flow
     * partials [∂p1/∂u, ∂q1/∂u, ∂p2/∂u, ∂q2/∂u] (shared with the indirect RHS term).
     * <p>
     * The side the quantity is taken on is selected consistently with
     * {@code AbstractLfSensitivityFactor#getFunctionEquationTerm}: function types {@code *_1} and {@code *_3} read
     * the branch's side-1 quantities, {@code *_2} reads its side-2 quantities. Branch parameter (R / X / Y)
     * self-sensitivity is only defined on two-winding branches and lines (a three-winding leg is not addressable as
     * an R / X / Y variable), so {@code branch} is never a leg here and side 3 collapses to side 1 just like side 1.
     */
    private static double computeBranchFunctionDirectPartial(LfBranch branch, SensitivityFunctionType functionType, SensitivityVariableType varType) {
        double[] partials = SingleVariableFactorGroup.computeBranchFlowParameterPartials(branch, varType);
        if (partials.length == 0) {
            return 0;
        }
        boolean side2 = functionType.getSide().orElse(0) == 2;
        double dpdu = side2 ? partials[2] : partials[0]; // ∂p/∂u on the monitored side
        double dqdu = side2 ? partials[3] : partials[1]; // ∂q/∂u on the monitored side
        switch (functionType) {
            case BRANCH_ACTIVE_POWER_1, BRANCH_ACTIVE_POWER_2, BRANCH_ACTIVE_POWER_3:
                return dpdu;
            case BRANCH_REACTIVE_POWER_1, BRANCH_REACTIVE_POWER_2, BRANCH_REACTIVE_POWER_3:
                return dqdu;
            case BRANCH_CURRENT_1, BRANCH_CURRENT_2, BRANCH_CURRENT_3: {
                // Current magnitude is recovered from the apparent power: |S| = v·|I|, hence |I| = |S| / v with
                // |S| = sqrt(p² + q²). For the direct partial the state (V, θ) is held fixed, so v is constant:
                //   ∂|I|/∂u = (1/v)·∂|S|/∂u = (1/v)·(p·∂p/∂u + q·∂q/∂u) / |S| = (p·∂p/∂u + q·∂q/∂u) / (v·|S|).
                double v = (side2 ? branch.getBus2() : branch.getBus1()).getV();
                double p = (side2 ? branch.getP2() : branch.getP1()).eval();
                double q = (side2 ? branch.getQ2() : branch.getQ1()).eval();
                double s = Math.sqrt(p * p + q * q);
                return s == 0 ? 0 : (p * dpdu + q * dqdu) / (v * s);
            }
            default:
                // Unreachable: this method is only reached for a self-sensitivity whose function element is the
                // branch carrying the R/X/Y variable (see computeParameterDirectPartial), so functionType is always
                // one of the branch flow/current types above. Bus function types never get here.
                throw new IllegalStateException("Unexpected branch function type: " + functionType);
        }
    }

    private void setFunctionReferences(List<LfSensitivityFactor<AcVariableType, AcEquationType>> factors) {
        for (LfSensitivityFactor<AcVariableType, AcEquationType> factor : factors) {
            if (factor.getFunctionPredefinedResult() != null) {
                factor.setFunctionReference(factor.getFunctionPredefinedResult());
            } else {
                factor.setFunctionReference(factor.getFunctionEquationTerm().eval());
            }
        }
    }

    private void calculatePostContingencySensitivityValues(List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors, LfNetworkChange lfNetworkChange,
                                                           LfNetwork lfNetwork, AcLoadFlowContext context, SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                                           Map<LfBus, Double> participationByBus,
                                                           LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                           SensitivityResultWriter resultWriter,
                                                           boolean hasTransformerBusTargetVoltage) {
        if (lfParameters.isDistributedSlack() && Math.abs(lfNetworkChange.getActivePowerLoss()) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(lfParameters.getBalanceType(),
                    lfParametersExt.isLoadPowerFactorConstant(), lfParametersExt.isUseActiveLimits());
            activePowerDistribution.run(lfNetwork.getSynchronousNetworks().getFirst(), lfNetworkChange.getActivePowerLoss());
        }

        int contingencyIndex = lfNetworkChange.getContingencyIndex();
        int operatorStrategyIndex = lfNetworkChange.getOperatorStrategyIndex();

        if (!runLoadFlow(context, false)) {
            // write contingency status
            resultWriter.writeStateStatus(contingencyIndex, operatorStrategyIndex, SensitivityAnalysisResult.Status.FAILURE);
            return;
        }

        // write contingency status
        resultWriter.writeStateStatus(contingencyIndex, operatorStrategyIndex, SensitivityAnalysisResult.Status.SUCCESS);

        // if we have at least one bus target voltage linked to a ratio tap changer, we have to rebuild the AC equation
        // system obtained just before the transformer steps rounding.
        if (hasTransformerBusTargetVoltage) {
            for (LfBranch branch : lfNetwork.getBranches()) {
                branch.getVoltageControl().ifPresent(vc -> branch.setVoltageControlEnabled(true));
            }
            lfNetwork.fixTransformerVoltageControls();
        }

        if (factorGroups.hasMultiVariables() && (!lfNetworkChange.getLostLoads().isEmpty() || !lfNetworkChange.getLostGenerators().isEmpty())) {
            // FIXME. It does not work with a contingency that breaks connectivity and lose an isolate injection.
            Set<LfBus> affectedBuses = lfNetworkChange.getLoadAndGeneratorBuses();
            rescaleGlsk(factorGroups, affectedBuses);
        }

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one

        // solve system
        DenseMatrix factorsStates = initFactorsRhs(context.getEquationSystem(), factorGroups, participationByBus); // this is the rhs for the moment
        describeSvcPilotFactorsRhs(factorGroups, context).forEach((col, column) -> column.writeInto(factorsStates, col));
        context.getJacobianMatrix().solveTransposed(factorsStates);
        setFunctionReferences(lfFactors);

        // calculate sensitivity values
        calculateSensitivityValues(lfFactors, factorGroups, factorsStates, contingencyIndex, operatorStrategyIndex, resultWriter);
    }

    /**
     * The RHS columns of the SVC_PILOT_TARGET_VOLTAGE factor groups, by group index: for each such group the
     * column is a linear combination of the controlled buses' BUS_TARGET_V columns, weighted by the
     * closed-loop coordination coefficients (see {@link SvcPilotPointClosedLoopSensitivity}). Empty when no
     * group is an SVC pilot one; groups it does not name keep the column their factor group describes.
     *
     * <p>Returned rather than pushed into the caller's storage, because the two directions keep it
     * differently — the forward writes it into the dense matrix it solves against, the adjoint keeps it to
     * contract with λ — and a description that is merely handed back lets each do that in one obvious line.</p>
     */
    private static Map<Integer, RhsColumn> describeSvcPilotFactorsRhs(
            SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
            AcLoadFlowContext context) {
        boolean hasSvcPilot = factorGroups.getList().stream()
                .anyMatch(group -> group.getVariableType() == SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE);
        if (!hasSvcPilot) {
            return Map.of();
        }
        // Build + factorize the all-zones coordination matrix ONCE (it is pilot-independent) and reuse it for
        // every queried pilot, instead of one full assembly + LU per pilot.
        try (SvcPilotPointClosedLoopSensitivity.Coordination coordination =
                     SvcPilotPointClosedLoopSensitivity.buildCoordination(context)) {
            if (coordination == null) {
                return Map.of(); // no active SVC zone
            }
            Map<Integer, RhsColumn> columnsByGroupIndex = new HashMap<>();
            Map<LfBus, Map<LfBus, Double>> weightsByPilot = new HashMap<>();
            for (SensitivityFactorGroup<AcVariableType, AcEquationType> group : factorGroups.getList()) {
                if (group.getVariableType() != SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE) {
                    continue;
                }
                LfBus pilotBus = (LfBus) ((SingleVariableLfSensitivityFactor<AcVariableType, AcEquationType>)
                        group.getFirstFactor()).getVariableElement();
                Map<LfBus, Double> weights = weightsByPilot.computeIfAbsent(pilotBus, coordination::weightsForPilot);
                RhsColumnBuilder builder = new RhsColumnBuilder(weights.size());
                for (var entry : weights.entrySet()) {
                    LfBus controlled = entry.getKey();
                    double w = entry.getValue();
                    context.getEquationSystem()
                            .getEquation(controlled.getNum(), AcEquationType.BUS_TARGET_V)
                            .ifPresent(eq -> builder.add(eq.getColumn(), w));
                }
                columnsByGroupIndex.put(group.getIndex(), builder.build());
            }
            return columnsByGroupIndex;
        }
    }

    /**
     * Reverse-mode (adjoint / VJP) dual of the forward AC sensitivity: given output cotangents {@code ȳ}
     * over the declared functions, return {@code θ̄ = Sᵀ·ȳ} over the variable groups — WITHOUT
     * materialising the sensitivity matrix {@code S}. A single transpose solve on the retained (e.g.
     * networkCacheEnabled) factorization, reusing {@link #initFactorsRhs} (∂F/∂p),
     * {@link #describeSvcPilotFactorsRhs} (SVC pilot closed-loop) and the function equation terms (∂f/∂x).
     * Assumes a load flow already converged on {@code context} (its Jacobian is factorized), exactly as
     * the forward path assumes.
     *
     * @param cotangents dL/dfunction, keyed by the base-network sensitivity factor.
     * @return θ̄ indexed by factor-group index ({@link SensitivityFactorGroup#getIndex()}).
     */
    private double[] analyseAdjoint(AcLoadFlowContext context,
                                   SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                   Map<LfSensitivityFactor<AcVariableType, AcEquationType>, Double> cotangents,
                                   Map<LfBus, Double> slackParticipationByBus) {
        var equationSystem = context.getEquationSystem();
        int equationCount = equationSystem.getIndex().getColumnCount();

        // ∂F/∂p columns (one per variable group), including the SVC pilot closed-loop — the same fill the
        // forward path runs, but collected SPARSELY. Forward needs the dense matrix because it SOLVES against
        // it; the adjoint only ever contracts it with λ, and most variable types write a scaled unit vector
        // (a bus target voltage or a shunt susceptance is one row; a branch parameter, four), so the dense
        // n_equations × n_groups array it used to allocate was almost entirely zeros — and on a large network
        // with several lever families it was the call's dominant allocation.
        RhsColumn[] parameterRhs = new RhsColumn[factorGroups.getList().size()];
        for (var group : factorGroups.getList()) {
            parameterRhs[group.getIndex()] = group.describeRhs(slackParticipationByBus);
        }
        describeSvcPilotFactorsRhs(factorGroups, context).forEach((col, column) -> parameterRhs[col] = column);

        // x̄ = Σ_f ȳ_f · (∂f/∂x): transpose of calculateSensi — scatter der() into the equation rows.
        // ȳ is per monitored FUNCTION, so add each function's ∂f/∂x exactly once even though a function
        // paired with several variables produces several factors that share the same cotangent.
        double[] xBar = new double[equationCount];
        Set<Pair<SensitivityFunctionType, String>> seenFunctions = new HashSet<>();
        for (var e : cotangents.entrySet()) {
            // Never 0.0: runAdjoint puts only non-zero cotangents in this map, so a zero function is already
            // absent rather than skipped here. A guard for it would be unreachable, which is worse than none.
            double yBar = e.getValue();
            var factor = e.getKey();
            if (!seenFunctions.add(Pair.of(factor.getFunctionType(), factor.getFunctionId()))) {
                continue;
            }
            // Scatter (∂f/∂x)ᵀ into x̄ through the Derivable contract (getVariables()/der()), whatever the
            // function's implementation: a single equation term (branch flows, currents, bus voltage) or the
            // InjectionDerivable behind BUS_REACTIVE_POWER, which is a signed sum of the bus's branch terms.
            // There is deliberately no type test with a fall-through here: skipping a function silently zeroes
            // its cotangent's contribution and returns a plausible, wrong θ̄.
            Derivable<AcVariableType> functionTerm = factor.getFunctionEquationTerm();
            // Same guard as the forward calculateSensitivityValues: a VALID factor whose function term is
            // inactive has no derivative to scatter, and der() on it is not a zero but an undefined value.
            if (!functionTerm.isActive()) {
                throw new PowsyblException("runAdjoint: the equation term of function " + factor.getFunctionType()
                        + " on '" + factor.getFunctionId() + "' is inactive, so its cotangent cannot be propagated");
            }
            // scale by the function's per-unit base so θ̄ comes out UNSCALED (physical), the dual of the
            // forward get_sensitivity_matrix: unscaleSensitivity = funcBase(f) / varBase(v), applied here as
            // funcBase on the function (x̄) side and varBase on the variable (θ̄) side.
            // Java multiplies left to right, so hoisting (ȳ · base) out of the loop keeps the very same
            // product order the inlined form had: same bits, one multiply less per variable.
            double scale = yBar * getFunctionBaseValue(factor);
            for (Variable<AcVariableType> variable : functionTerm.getVariables()) {
                int row = variable.getRow();
                if (row >= 0) {
                    xBar[row] += scale * functionTerm.der(variable);
                }
            }
        }

        // λ = J⁻ᵀ x̄: the stored matrix is M = Jᵀ, so plain solve() IS the adjoint solve. One solve.
        double[] lambda = xBar; // solved in place
        context.getJacobianMatrix().solve(lambda);

        // θ̄_v = λᵀ·rhs_v per variable group, plus the direct ∂f/∂p term (branch parameters). rhs_v is the
        // initFactorsRhs column, which already carries the forward's −∂F/∂p sign (it is what solveTransposed
        // maps to the state sensitivity dx/dp), so this dot product is the exact transpose of the forward
        // calculateSensi — no extra sign.
        double[] thetaBar = new double[factorGroups.getList().size()];
        for (var group : factorGroups.getList()) {
            int col = group.getIndex();
            // Over the column's NON-ZEROS: O(nnz) rather than O(n_equations) per group. A one-hot column —
            // a bus target voltage, a shunt susceptance, a transformer phase — is a single multiply. A
            // genuinely dense one (an injection under distributed slack) still costs what it always did.
            double thetaG = parameterRhs[col].dot(lambda);
            for (var factor : group.getFactors()) {
                Double yBar = cotangents.get(factor);
                if (yBar != null && yBar != 0.0) {
                    thetaG += yBar * getFunctionBaseValue(factor) * computeParameterDirectPartial(factor);
                }
            }
            // divide by the variable's per-unit base to finish the unscale (see the x̄ scaling above): θ̄ then
            // equals the forward's unscaled Sᵀ·ȳ in physical units, per variable.
            thetaBar[col] = thetaG / getVariableBaseValue(group.getFirstFactor());
        }
        return thetaBar;
    }

    /**
     * A monitored function of a {@link #runAdjoint} request, and the key of the cotangent map: a
     * (type, id) PAIR, never an id alone, since a branch is monitored by several function types that share
     * its id and each carries its own {@code ȳ}.
     */
    public record FunctionRef(SensitivityFunctionType type, String id) {

        public FunctionRef {
            Objects.requireNonNull(type);
            Objects.requireNonNull(id);
        }
    }

    /**
     * A differentiation variable of a {@link #runAdjoint} request, and the key of the gradient it returns: a
     * (type, id) PAIR for the mirror reason.
     *
     * <p>The same element is a legitimate lever under several types at once — a line's resistance and its
     * reactance are different derivatives of one branch — and a caller may declare both in one call.
     * {@code createFactorGroups} already keys a factor group by exactly this pair, so the two get their own
     * groups and their own gradients; keying by the id alone collapsed them, and the second type either
     * vanished from the map or was reported under the first type's value.</p>
     */
    public record VariableRef(SensitivityVariableType type, String id) {

        public VariableRef {
            Objects.requireNonNull(type);
            Objects.requireNonNull(id);
        }
    }

    /**
     * Reverse-mode / VJP entry point — <b>the</b> adjoint API, and the only one: exactly one public entry
     * per direction, this being the mirror of {@link #analyse} on the forward side. There is deliberately
     * no factor-list overload to choose from, because the choice was the problem: assembling the O(F+V)
     * set by hand depends on internals of this class (a variable with no factor gets no group from
     * {@code createFactorGroups}, hence no θ̄ entry, hence a silent zero downstream), and getting it wrong
     * returns a plausible gradient rather than an error — which is why {@link #buildAdjointFactors} owns
     * that construction.
     *
     * <p>The request is the cotangents themselves plus the levers to differentiate against: the cotangent
     * map names every monitored function, so there is no second list of functions to keep in step with it.
     * OpenLoadFlow owns the factor-set construction and the pipeline.</p>
     *
     * <p>Reuses the AC load flow retained in the network cache ({@code networkCacheEnabled}): a cached AC
     * load flow must have run on {@code network} first. Instead of materialising the sensitivity matrix
     * {@code S}, it contracts an output cotangent to return {@code θ̄ = Sᵀ·ȳ}.</p>
     *
     * <p>The whole public adjoint surface is this method, {@link FunctionRef} / {@link VariableRef} to key
     * the two maps, and {@link AdjointVariable} to declare a lever.</p>
     *
     * @param cotangentsByFunction dL/dfunction per monitored function. A zero entry still DECLARES its
     *                             function, which is what lets a caller state "not weighted here" without
     *                             withdrawing it; only an empty map is an error. Ids are resolved
     *                             internally, so this map and the returned one stay keyed by the ids the
     *                             caller passed.
     * @param variables            the levers, each with its resolved type and whether its id names a
     *                             {@link SensitivityVariableSet}.
     * @return dL/dvariable, keyed by {@link VariableRef} — by the (type, id) PAIR, since one element may be
     *         declared under several variable types in the same call. ONE ENTRY PER DECLARED LEVER, in
     *         declaration order: a lever that does not resolve to an element of this network reads 0, the
     *         value the forward path writes for the same case, so a caller never has to tell an absent
     *         lever from a zero one. A lever that cannot be differentiated at all throws instead.
     */
    public Map<VariableRef, Double> runAdjoint(Network network, String workingVariantId,
                                               List<SensitivityVariableSet> variableSets,
                                               Map<FunctionRef, Double> cotangentsByFunction,
                                               List<AdjointVariable> variables) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(cotangentsByFunction);
        Objects.requireNonNull(variables);
        // BEFORE building the factors: buildAdjointFactors resolves a BUS_VOLTAGE function id against the
        // bus view (SensitivityFactor.resolveBusId), which is variant-dependent, so it has to see the
        // variant the caller asked about rather than whichever one happens to be current.
        network.getVariantManager().setWorkingVariant(workingVariantId);
        List<SensitivityFactor> factors = buildAdjointFactors(network, cotangentsByFunction.keySet(), variables);

        NetworkCache.Entry<NetworkCache.LfInput, NetworkCache.AcLfValue> entry =
                NetworkCache.AC_LF_INSTANCE.findEntry(network)
                        .orElseThrow(() -> new PowsyblException("No cached AC load flow for this network "
                                + "— run a load flow with networkCacheEnabled=true before runAdjoint."));
        NetworkCache.AcLfValue value = entry.getValues().get(0); // main synchronous network
        AcLoadFlowContext context = value.getContext();
        LfNetwork lfNetwork = value.getNetwork();
        boolean breakers = context.getParameters().getNetworkParameters().isBreakers();

        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream()
                .collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
        // An LfSensitivityFactor does not always carry the ids the caller wrote: SensitivityFactorModelReader
        // rewrites the FUNCTION id through SensitivityFactor.resolveBusId, which maps a BUS_VOLTAGE id to its
        // bus-view bus and leaves every other function type alone. Variable ids are passed through untouched
        // today. Only getIndex() links back to the declared factor, so BOTH the cotangent (in) and the θ̄ map
        // (out) go through `factors` by index — one rule for both sides, and one that stays correct if the
        // reader ever resolves variable ids too.
        SensitivityFactorHolder<AcVariableType, AcEquationType> allFactorHolder =
                readAndCheckFactors(network, variableSetsById, new SensitivityFactorModelReader(factors, network), lfNetwork, breakers);
        // The two sides of the product need different things from a factor, so they filter differently.
        // x̄ needs the FUNCTION's equation term, so it takes VALID only. A θ̄ group needs the VARIABLE's
        // element and equation and nothing from the function at all, so it also takes ZERO — a factor whose
        // variable resolved and whose function did not. Filtering groups on VALID too made one unresolvable
        // monitored function able to strip a perfectly good lever of its group, and with the anchor now
        // chosen by a total order that would have been systematic rather than occasional.
        List<LfSensitivityFactor<AcVariableType, AcEquationType>> allLfFactors = allFactorHolder.getAllFactors();
        List<LfSensitivityFactor<AcVariableType, AcEquationType>> validLfFactors = allLfFactors.stream()
                .filter(f -> f.getStatus() == LfSensitivityFactor.Status.VALID)
                .collect(Collectors.toList());
        SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups = createFactorGroups(
                allLfFactors.stream()
                        .filter(f -> f.getStatus() == LfSensitivityFactor.Status.VALID
                                || f.getStatus() == LfSensitivityFactor.Status.ZERO)
                        .collect(Collectors.toList()));

        LoadFlowParameters lfParameters = parameters.getLoadFlowParameters();
        Map<LfBus, Double> slackParticipationByBus;
        if (lfParameters.isDistributedSlack()) {
            List<ParticipatingElement> participatingElements = getParticipatingElements(
                    lfNetwork.getBuses(), lfParameters.getBalanceType(), OpenLoadFlowParameters.get(lfParameters));
            slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                    ParticipatingElement::getLfBus, e -> -e.getFactor(), Double::sum));
        } else {
            slackParticipationByBus = Collections.singletonMap(
                    lfNetwork.getSynchronousNetworks().getFirst().getSlackBuses().getFirst(), -1d);
        }

        // cotangent per factor = the cotangent of its monitored function, matched by the caller's
        // (functionType, functionId). No guard is needed on this side any more: the cotangent map IS the
        // declaration of what is monitored, so a key that names nothing cannot exist. It used to be
        // possible to key a function no block declared — the contribution then vanished into a plausible,
        // wrong θ̄ — and removing the second list removed the failure mode with it.
        Map<LfSensitivityFactor<AcVariableType, AcEquationType>, Double> cotangents = new HashMap<>();
        for (var factor : validLfFactors) {
            SensitivityFactor declared = factors.get(factor.getIndex());
            Double yBar = cotangentsByFunction.get(new FunctionRef(declared.getFunctionType(), declared.getFunctionId()));
            if (yBar != null && yBar != 0.0) {
                cotangents.put(factor, yBar);
            }
        }

        double[] thetaBar = analyseAdjoint(context, factorGroups, cotangents, slackParticipationByBus);

        Map<VariableRef, Double> gradientByGroup = new HashMap<>();
        for (var group : factorGroups.getList()) {
            SensitivityFactor declared = factors.get(group.getFirstFactor().getIndex());
            gradientByGroup.put(new VariableRef(declared.getVariableType(), declared.getVariableId()),
                    thetaBar[group.getIndex()]);
        }
        return assembleGradients(variables, gradientByGroup, statusesByLever(factors, allLfFactors));
    }

    /**
     * The statuses OpenLoadFlow gave the factors of each declared lever, which is how the lever's own fate is
     * read back: a factor is VALID_ONLY_FOR_FUNCTION or SKIP exactly when its VARIABLE element did not
     * resolve, and VALID or ZERO exactly when it did.
     */
    private static Map<VariableRef, Set<LfSensitivityFactor.Status>> statusesByLever(
            List<SensitivityFactor> factors, List<LfSensitivityFactor<AcVariableType, AcEquationType>> allLfFactors) {
        Map<VariableRef, Set<LfSensitivityFactor.Status>> statuses = new HashMap<>();
        for (var lfFactor : allLfFactors) {
            SensitivityFactor declared = factors.get(lfFactor.getIndex());
            statuses.computeIfAbsent(new VariableRef(declared.getVariableType(), declared.getVariableId()),
                    k -> EnumSet.noneOf(LfSensitivityFactor.Status.class)).add(lfFactor.getStatus());
        }
        return statuses;
    }

    /**
     * One entry per DECLARED lever, in declaration order — the invariant a caller needs, because it cannot
     * tell an absent lever from one whose gradient is zero, and reading an absent one as zero is exactly how
     * "this lever cannot help" gets said by accident.
     *
     * <p>A lever with no θ̄ group did not resolve to an element of this network, and the forward path already
     * decided what that means: {@code calculateSensitivityValues} writes 0 for every
     * VALID_ONLY_FOR_FUNCTION factor, because a variable outside the main connected component moves nothing,
     * so the sensitivity is not merely unknown, it is known to be zero. The adjoint is that path transposed
     * and answers the same, rather than staying silent and leaving the caller to guess.</p>
     *
     * <p>SKIP is the one case where it does NOT follow the forward. There neither the lever nor any monitored
     * function resolved, and the forward writes NaN while recording the id in a separate skipped-variable
     * list. The adjoint has no such second channel — it returns one number per lever — and a NaN loose in a
     * gradient poisons an optimiser's step with no clue where it came from. Naming the levers in an
     * exception says the same thing where it can be acted on.</p>
     */
    private static Map<VariableRef, Double> assembleGradients(List<AdjointVariable> variables,
                                                              Map<VariableRef, Double> gradientByGroup,
                                                              Map<VariableRef, Set<LfSensitivityFactor.Status>> statusesByLever) {
        List<VariableRef> unresolved = new ArrayList<>();
        Map<VariableRef, Double> gradientByVariable = new LinkedHashMap<>();
        for (AdjointVariable v : variables) {
            Double gradient = gradientByGroup.get(v.ref());
            Set<LfSensitivityFactor.Status> statuses =
                    statusesByLever.getOrDefault(v.ref(), EnumSet.noneOf(LfSensitivityFactor.Status.class));
            if (gradient != null) {
                gradientByVariable.put(v.ref(), gradient);
            } else if (statuses.contains(LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)) {
                gradientByVariable.put(v.ref(), 0.0);
            } else {
                // SKIP, or a lever that emitted no factor at all — which buildAdjointFactors is supposed to
                // make impossible, so if it ever happens the caller should hear about it rather than read a
                // zero that means something else entirely.
                unresolved.add(v.ref());
            }
        }
        if (!unresolved.isEmpty()) {
            throw new PowsyblException("runAdjoint: " + unresolved.size() + " lever(s) could not be "
                    + "differentiated, because neither the lever nor any monitored function resolves to an "
                    + "element of this network. Returning a zero for them would read as 'this lever cannot "
                    + "help', which is a different statement: " + unresolved);
        }
        return gradientByVariable;
    }

    /**
     * A lever of a {@link #runAdjoint} request: its {@link VariableRef}, plus whether the id names a
     * {@link SensitivityVariableSet} (a GLSK zone) rather than a network element — which the ref alone
     * cannot say, since such a variable is typed INJECTION_ACTIVE_POWER like any injection.
     *
     * <p>One request can serve several lever families at once: they all contract the same cotangent and
     * differ only in the per-variable contraction, so fusing them costs one transpose solve instead of N.
     * Every lever gets its own θ̄ group, and since a lever is its (type, id) PAIR, the same element declared
     * under two types — a line as a resistance and as a reactance — is two levers with two gradients.</p>
     */
    public record AdjointVariable(VariableRef ref, boolean variableSet) {

        public AdjointVariable {
            Objects.requireNonNull(ref);
        }

        /** A lever on a network element. */
        public static AdjointVariable of(SensitivityVariableType type, String id) {
            return new AdjointVariable(new VariableRef(type, id), false);
        }

        /** A lever on a {@link SensitivityVariableSet} (GLSK zone) id. */
        public static AdjointVariable ofVariableSet(SensitivityVariableType type, String id) {
            return new AdjointVariable(new VariableRef(type, id), true);
        }

        public SensitivityVariableType type() {
            return ref.type();
        }

        public String id() {
            return ref.id();
        }
    }

    /**
     * Build the O(functions + variables) reverse-mode factor set from the monitored functions and the levers
     * — the minimal set that reproduces the full functions×variables cross product. Package-private rather
     * than private so {@code AcSensitivityAnalysisAdjointTest} can assert the set it builds really is
     * smaller than that cross product, whose θ̄ the equivalence gates check against the forward matrix.
     * Not part of the API — the caller passes cotangents and levers, never factors.
     * <p>Two statements, one per side of {@code θ̄ = Sᵀȳ}:</p>
     * <ul>
     *   <li>every monitored FUNCTION gets a factor, so its cotangent reaches x̄;</li>
     *   <li>every VARIABLE gets a factor, so {@code createFactorGroups} gives it a θ̄ group — plus the pairs
     *       that can carry its direct term {@code ∂f/∂p}, which {@link #directTermFunctionIds} names.</li>
     * </ul>
     * <p>The direct term used to be chased by two separate rules, a self-pair and a BUS_REACTIVE_POWER
     * cross product, each covering the case its author had in mind; the second was added only after the
     * first silently missed one. Asking instead which functions CAN carry the term, in one place, makes
     * completeness a property rather than a list of cases: no function type can reintroduce a gap.</p>
     * Deduplicated by (functionType, resolvedFunctionId, variableType, variableId). This is the construction that used
     * to live in the pypowsybl caller; owning it here keeps it under the OLF equivalence gates.
     */
    static List<SensitivityFactor> buildAdjointFactors(Network network, Collection<FunctionRef> functions,
                                                       List<AdjointVariable> variables) {
        // Reject an empty declaration rather than index into it. Each is a caller error with no meaningful
        // answer — a VJP with no function has no cotangent to propagate, and one with no variable has no θ̄
        // to return — and an empty θ̄ returned quietly would read exactly like "none of your levers can help".
        if (functions.isEmpty()) {
            throw new PowsyblException("runAdjoint needs at least one monitored function: with none there is "
                    + "no cotangent to propagate and no gradient to return.");
        }
        if (variables.isEmpty()) {
            throw new PowsyblException("runAdjoint needs at least one variable to differentiate against, "
                    + "or θ̄ = Sᵀ·ȳ is empty. Skip the call instead when a lever family is empty.");
        }

        List<SensitivityFactor> factors = new ArrayList<>();
        // Identifies a pair by the (type, id) of BOTH sides, which is what createFactorGroups groups by.
        // Keying a variable by its id alone made a second declaration of the same element under another type
        // collide with the first: the factor was deduplicated away, so that type got no group at all.
        Set<EmittedPair> emittedPairs = new HashSet<>();

        // x̄: every monitored function needs a factor, so its cotangent reaches the scatter (base case only —
        // the adjoint ignores contingencies). Which lever it is paired with does not matter, so take any.
        AdjointVariable anchorVariable = variables.get(0); // a List, so this choice is already reproducible
        for (FunctionRef function : functions) {
            addAdjointFactor(factors, emittedPairs, network, function, anchorVariable);
        }

        // θ̄: one factor is all it takes for createFactorGroups to give a lever its own group, and the group
        // is what carries the RHS column. Any monitored function can anchor it; if that pair happens to
        // carry a direct term, the loop below emits the same pair and the dedup keeps it counted once.
        FunctionRef anchorFunction = functions.stream().min(ANCHOR_ORDER).orElseThrow();
        for (AdjointVariable v : variables) {
            addAdjointFactor(factors, emittedPairs, network, anchorFunction, v);
            // The direct term is summed over EVERY monitored function, because x̄ and λ are shared by the
            // whole call. With one flat list of functions that is simply every function — there is no longer
            // a per-block scope for a carrier to fall outside of, which is how a lever declared beside
            // branch flows used to lose its term to a bus reactive injection declared next to it.
            for (FunctionRef function : functions) {
                if (carriesDirectTerm(function, v)) {
                    addAdjointFactor(factors, emittedPairs, network, function, v);
                }
            }
        }
        return factors;
    }

    /**
     * Whether the pair {@code (function, v)} can hold a non-zero direct term {@code ∂f/∂p} — the explicit
     * dependence of a function on a variable, which no RHS column carries and which only a factor can reach
     * (see {@code computeParameterDirectPartial}).
     *
     * <p>Only a branch parameter (R / X / Y) has one at all: every other variable acts on every function
     * purely through the state, so false is its complete answer. For a branch parameter there are two cases,
     * and what separates them is what can be decided HERE, before the LF network exists:</p>
     * <ul>
     *   <li>a branch flow or current holds the term only on the variable's OWN branch, which is an id
     *       comparison;</li>
     *   <li>a bus reactive injection holds it on every branch INCIDENT to the bus, and that incidence needs
     *       the resolved network — so admit every such function and let the pairs that do not touch the
     *       branch contribute their exact zero. A factor group is one per VARIABLE, so the extra pairs cost
     *       a direct-term lookup each, never an RHS column and never a solve.</li>
     * </ul>
     */
    private static boolean carriesDirectTerm(FunctionRef function, AdjointVariable v) {
        if (!isBranchParameter(v.type())) {
            return false;
        }
        return function.type() == SensitivityFunctionType.BUS_REACTIVE_POWER || function.id().equals(v.id());
    }

    private static boolean isBranchParameter(SensitivityVariableType type) {
        return type == SensitivityVariableType.BRANCH_RESISTANCE
                || type == SensitivityVariableType.BRANCH_REACTANCE
                || type == SensitivityVariableType.BRANCH_ADMITTANCE;
    }

    /**
     * A total order on the monitored functions, used only to pick the one that anchors a lever's θ̄ group.
     *
     * <p>Any function can anchor a group, so the choice looks free — but it is not allowed to be ARBITRARY.
     * The anchor's own validity decides the lever's fate: if that function's element is outside the main
     * connected component, its factor is dropped and the lever gets no group, while a different function
     * would have anchored it fine. Taking the first the caller's map happens to yield made that outcome
     * depend on hash order, and {@code Map.of} salts its iteration per JVM run, so the same request could
     * answer with different levers on different runs. A total order costs one pass and makes the failure
     * reproducible, which is the least a caller needs to diagnose it.</p>
     */
    private static final Comparator<FunctionRef> ANCHOR_ORDER =
            Comparator.comparing((FunctionRef f) -> f.type().name()).thenComparing(FunctionRef::id);

    /** One emitted (function, variable) pair, both sides by their resolved (type, id) — the dedup identity. */
    private record EmittedPair(FunctionRef resolvedFunction, VariableRef variable) {
    }

    private static void addAdjointFactor(List<SensitivityFactor> factors, Set<EmittedPair> emittedPairs, Network network,
                                         FunctionRef function, AdjointVariable v) {
        // Dedup on the RESOLVED id so two caller ids for the same LF element (e.g. two bus-breaker ids on one
        // bus-view bus) collapse to a single factor, but keep the caller's ORIGINAL functionId on the emitted
        // factor: runAdjoint reads the cotangent by declared.getFunctionId() (see the id note there), so a
        // resolved id here would miss the caller's cotangent key and return a silent zero θ̄ (a BUS_VOLTAGE
        // function, whose bus-breaker id resolves to a different bus-view id, hit exactly this).
        FunctionRef resolved = new FunctionRef(function.type(),
                SensitivityFactor.resolveBusId(function.id(), function.type(), network));
        if (emittedPairs.add(new EmittedPair(resolved, v.ref()))) {
            factors.add(new SensitivityFactor(function.type(), function.id(), v.type(), v.id(),
                    v.variableSet(), ContingencyContext.none()));
        }
    }

    private static boolean runLoadFlow(AcLoadFlowContext context, boolean isRunningBaseSituation) {
        AcLoadFlowResult result = new AcloadFlowEngine(context)
                .run();
        if (result.isSuccess() || result.getSolverStatus() == AcSolverStatus.NO_CALCULATION) {
            return true;
        } else {
            if (isRunningBaseSituation) {
                if (result.getOuterLoopResult().status() != OuterLoopStatus.STABLE) {
                    throw new PowsyblException("Initial load flow of base situation ended with outer loop status " + result.getOuterLoopResult().statusText());
                } else {
                    throw new PowsyblException("Initial load flow of base situation ended with solver status " + result.getSolverStatus());
                }
            } else {
                LOGGER.warn("Load flow failed with result={}", result);
                return false;
            }
        }
    }

    /**
     * https://people.montefiore.uliege.be/vct/elec0029/lf.pdf / Equation 32 is transposed
     */
    @Override
    public void analyse(Network network, String workingVariantId, List<Contingency> contingencies, List<OperatorStrategy> configuredOperatorStrategies,
                        List<Action> configuredActions, PropagatedContingencyCreationParameters creationParameters,
                        List<SensitivityVariableSet> variableSets, SensitivityFactorReader factorReader,
                        SensitivityResultWriter resultWriter, ReportNode sensiReportNode,
                        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt,
                        Executor executor) throws ExecutionException {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(resultWriter);
        Objects.requireNonNull(sensiReportNode);
        Objects.requireNonNull(sensitivityAnalysisParametersExt);
        Objects.requireNonNull(executor);

        network.getVariantManager().setWorkingVariant(workingVariantId);

        LoadFlowParameters lfParameters = parameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(lfParameters);
        VariablesTargetVoltageInfo variablesTargetVoltageInfo = getVariableTargetVoltageInfo(factorReader, network);

        // create LF network (we only manage main connected component)
        if (variablesTargetVoltageInfo.hasTransformerTargetVoltage()) {
            // if we have at least one bus target voltage linked to a ratio tap changer, we activate the transformer
            // voltage control for the AC load flow engine.
            lfParameters.setTransformerVoltageControlOn(true);
        }
        SlackBusSelector slackBusSelector = makeSlackBusSelector(network, lfParameters, lfParametersExt);

        checkVariableSet(variableSets);
        checkContingencies(contingencies);
        checkLoadFlowParameters(lfParameters);

        List<OperatorStrategy> operatorStrategies;
        List<Action> actions;
        if (parameters.getOperatorStrategiesCalculationMode() == SensitivityOperatorStrategiesCalculationMode.NONE) {
            operatorStrategies = Collections.emptyList();
            actions = Collections.emptyList();
        } else {
            operatorStrategies = configuredOperatorStrategies;
            actions = configuredActions;
        }

        LfTopoConfig topoConfig = new LfTopoConfig();

        // update topo config with supported actions
        Actions.addAllSwitchesToOperate(topoConfig, network, actions);
        Actions.addAllBranchesToClose(topoConfig, network, actions);
        Actions.addAllPtcToOperate(topoConfig, actions);

        if (sensitivityAnalysisParametersExt.getThreadCount() == 1) {
            List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);
            AcLoadFlowParameters acParameters = makeAcLoadFlowParameters(network, slackBusSelector, lfParameters, lfParametersExt, topoConfig.isBreaker());
            try (LfNetworkList lfNetworks = Networks.loadWithReconnectableElements(network, topoConfig, acParameters.getNetworkParameters(), sensiReportNode)) {

                analyzeContingencySet(network, lfNetworks, propagatedContingencies, operatorStrategies, actions, acParameters, lfParameters, lfParametersExt, variableSets, factorReader,
                        topoConfig.isBreaker(), resultWriter, variablesTargetVoltageInfo, sensitivityAnalysisParametersExt);
            }
        } else {
            try (SequentialSensitivityResultWriter sequentialSensitivityResultWriter = new SequentialSensitivityResultWriter(resultWriter)) {
                BufferedFactorReader bufferedFactorReader = new BufferedFactorReader(factorReader);
                var contingenciesPartitions = Lists2.partition(contingencies, sensitivityAnalysisParametersExt.getThreadCount());
                ContingencyMultiThreadHelper.ParameterProvider<AcLoadFlowParameters> parameterProvider
                        = tc -> makeAcLoadFlowParameters(network, slackBusSelector, lfParameters, lfParametersExt, tc.isBreaker());
                ContingencyMultiThreadHelper.ContingencyRunner<AcLoadFlowParameters> contingencyRunner = (partitionNum, lfNetworks, propagatedContingencies, acParameters) -> {
                    analyzeContingencySet(network, lfNetworks, propagatedContingencies, operatorStrategies, actions, acParameters, lfParameters, lfParametersExt, variableSets, bufferedFactorReader,
                        acParameters.getNetworkParameters().isBreakers(), sequentialSensitivityResultWriter, variablesTargetVoltageInfo, sensitivityAnalysisParametersExt);
                    sequentialSensitivityResultWriter.flush(); // flush the batch of data kept in this thread
                };
                ContingencyMultiThreadHelper.ReportMerger reportMerger = ContingencyMultiThreadHelper::mergeReportThreadResults;

                ContingencyMultiThreadHelper.createLFNetworksPerContingencyPartitionAndRunAnalysis(network, workingVariantId, contingenciesPartitions, creationParameters, topoConfig,
                        parameterProvider, contingencyRunner, sensiReportNode, reportMerger, executor);
            }
        }
    }

    private void warnOverridenParameter(String parameterName, String wantedValue, String usedValue) {
        LOGGER.warn("Load flow parameter {}={} is not handled in AC sensitivity analysis, using parameter value {} instead", parameterName, wantedValue, usedValue);
    }

    private LfNetworkParameters overrideUnsupportedParameters(LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        if (lfParameters.getComponentMode() != LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS) {
            warnOverridenParameter("componentMode", lfParameters.getComponentMode().name(), LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS.name());
        }
        if (lfParametersExt.getLowImpedanceBranchMode() != OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE) {
            warnOverridenParameter("lowImpedanceBranchMode", lfParametersExt.getLowImpedanceBranchMode().name(), OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE.name());
        }
        if (lfParametersExt.isNetworkCacheEnabled()) {
            warnOverridenParameter("networkCacheEnabled", "true", "false");
        }
        if (lfParametersExt.isSimulateAutomationSystems()) {
            warnOverridenParameter("simulateAutomationSystems", "true", "false");
        }
        if (lfParametersExt.getReferenceBusSelectionMode() != ReferenceBusSelector.DEFAULT_MODE) {
            warnOverridenParameter("referenceBusSelectionMode", lfParametersExt.getReferenceBusSelectionMode().name(), ReferenceBusSelector.DEFAULT_MODE.name());
        }
        return new LfNetworkParameters()
                .setLoadFlowModel(LoadFlowModel.AC)
                .setComponentMode(LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS)
                .setMinImpedance(true)
                .setCacheEnabled(false) // force not caching as not supported in sensi analysis
                .setSimulateAutomationSystems(false)
                .setReferenceBusSelector(ReferenceBusSelector.DEFAULT_SELECTOR); // not supported yet
    }

    private LfNetworkParameters makeNetworkParameters(SlackBusSelector slackBusSelector, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean breakers) {
        LfNetworkParameters lfNetworkParams = overrideUnsupportedParameters(lfParameters, lfParametersExt);
        return lfNetworkParams
                .setSlackBusSelector(slackBusSelector)
                .setConnectivityFactory(connectivityFactory)
                .setGeneratorVoltageRemoteControl(lfParametersExt.isVoltageRemoteControl())
                .setTwtSplitShuntAdmittance(lfParameters.isTwtSplitShuntAdmittance())
                .setBreakers(breakers)
                .setPlausibleActivePowerLimit(lfParametersExt.getPlausibleActivePowerLimit())
                .setCountriesToBalance(lfParameters.getCountriesToBalance())
                .setDistributedOnConformLoad(lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setPhaseControl(lfParameters.isPhaseShifterRegulationOn())
                .setTransformerVoltageControl(lfParameters.isTransformerVoltageControlOn())
                .setVoltagePerReactivePowerControl(lfParametersExt.isVoltagePerReactivePowerControl())
                .setGeneratorReactivePowerRemoteControl(lfParametersExt.isGeneratorReactivePowerRemoteControl())
                .setTransformerReactivePowerControl(lfParametersExt.isTransformerReactivePowerControl())
                .setShuntVoltageControl(lfParameters.isShuntCompensatorVoltageControlOn())
                .setReactiveLimits(lfParameters.isUseReactiveLimits())
                .setHvdcAcEmulation(lfParameters.isHvdcAcEmulation())
                .setMinPlausibleTargetVoltage(lfParametersExt.getMinPlausibleTargetVoltage())
                .setMaxPlausibleTargetVoltage(lfParametersExt.getMaxPlausibleTargetVoltage())
                .setMinNominalVoltageTargetVoltageCheck(lfParametersExt.getMinNominalVoltageTargetVoltageCheck())
                .setLowImpedanceThreshold(lfParametersExt.getLowImpedanceThreshold())
                .setSecondaryVoltageControl(lfParametersExt.isSecondaryVoltageControl()) // load SVC zones for SVC_PILOT sensitivity
                .setAreaInterchangeControlAreaType(lfParametersExt.getAreaInterchangeControlAreaType())
                .setForceTargetQInReactiveLimits(lfParametersExt.isForceTargetQInReactiveLimits())
                .setDisableInconsistentVoltageControls(lfParametersExt.isDisableInconsistentVoltageControls())
                .setExtrapolateReactiveLimits(lfParametersExt.isExtrapolateReactiveLimits())
                .setGeneratorsWithZeroMwTargetAreNotStarted(lfParametersExt.isGeneratorsWithZeroMwTargetAreNotStarted())
                .setDetailedReport(lfParametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NETWORK_LOADING))
                .setAllowNonLinearShuntZeroSection(lfParametersExt.isAllowNonLinearShuntZeroSection());
    }

    private SlackBusSelector makeSlackBusSelector(Network network, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(lfParametersExt.getSlackBusSelectionMode(),
                lfParametersExt.getSlackBusesIds(),
                lfParametersExt.getPlausibleActivePowerLimit(),
                lfParametersExt.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(),
                lfParametersExt.getSlackBusCountryFilter());
        if (lfParameters.isReadSlackBus()) {
            slackBusSelector = new NetworkSlackBusSelector(network, lfParametersExt.getSlackBusCountryFilter(), slackBusSelector);
        }
        return slackBusSelector;
    }

    private AcLoadFlowParameters makeAcLoadFlowParameters(Network network, SlackBusSelector slackBusSelector,
                                                          LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                          boolean breakers) {
        // create AC engine
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, matrixFactory, connectivityFactory, breakers, true);
        acParameters.setDetailedReport(lfParametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_SENSITIVITY_ANALYSIS));
        acParameters.setNetworkParameters(makeNetworkParameters(slackBusSelector, lfParameters, lfParametersExt, breakers));
        return acParameters;
    }

    private void analyzeContingencySet(Network network, LfNetworkList lfNetworks, List<PropagatedContingency> propagatedContingencies,
                                       List<OperatorStrategy> operatorStrategies, List<Action> actions, AcLoadFlowParameters acParameters,
                                       LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, List<SensitivityVariableSet> variableSets,
                                       SensitivityFactorReader factorReader, boolean breakers, SensitivityResultWriter resultWriter,
                                       VariablesTargetVoltageInfo variablesTargetVoltageInfo, OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt) {

        if (breakers && variablesTargetVoltageInfo.hasBusTargetVoltage()) {
            // FIXME
            // a bus voltage function works only on a bus/branch topology and a switch contingency only works on a
            // bus/breaker topology. It is not compatible and must be fixed in the API.
            throw new PowsyblException("Switch contingency is not yet supported with sensitivity function of type BUS_VOLTAGE");
        }

        // create networks including all necessary switches
        LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow(() -> new PowsyblException("Empty network"));
        // As sensitivity analysis does not support AC-DC networks, the lfNetwork contains only one synchronous network.

        ReportNode networkReportNode = lfNetwork.getReportNode();

        Map<String, Action> actionsById = Actions.indexById(actions);
        Map<String, List<Indexed<OperatorStrategy>>> operatorStrategiesByContingencyId =
                OperatorStrategies.indexByContingencyId(propagatedContingencies, operatorStrategies, actionsById, true);
        Set<Action> neededActions = OperatorStrategies.getNeededActions(operatorStrategiesByContingencyId, actionsById);
        Map<String, LfAction> lfActionById = LfActionUtils.createLfActions(lfNetwork, neededActions, network); // only convert needed actions

        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
        SensitivityFactorHolder<AcVariableType, AcEquationType> allFactorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork, breakers);
        List<LfSensitivityFactor<AcVariableType, AcEquationType>> allLfFactors = allFactorHolder.getAllFactors();
        LOGGER.info("Running AC sensitivity analysis with {} factors, {} contingencies and {} operator strategies",
                allLfFactors.size(), propagatedContingencies.size(), operatorStrategies.size());

        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {

            runLoadFlow(context, true);

            acParameters.setVoltageInitReport(false);

            // index factors by variable group to compute a minimal number of states
            SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups = createFactorGroups(allLfFactors);

            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution

            Map<LfBus, Double> slackParticipationByBus;
            if (lfParameters.isDistributedSlack()) {
                List<ParticipatingElement> participatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
                slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                        ParticipatingElement::getLfBus,
                        element -> -element.getFactor(),
                        Double::sum
                ));
            } else {
                slackParticipationByBus = Collections.singletonMap(lfNetwork.getSynchronousNetworks().getFirst().getSlackBuses().getFirst(), -1d);

            }

            // if we have at least one bus target voltage linked to a ratio tap changer, we have to rebuild the AC equation
            // system obtained just before the transformer steps rounding.
            if (variablesTargetVoltageInfo.hasTransformerTargetVoltage()) {
                // switch on regulating transformers
                for (LfBranch branch : lfNetwork.getBranches()) {
                    branch.getVoltageControl().ifPresent(vc -> branch.setVoltageControlEnabled(true));
                }
                lfNetwork.fixTransformerVoltageControls();
            }

            // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one

            // initialize right hand side from valid factors
            DenseMatrix factorsStates = initFactorsRhs(context.getEquationSystem(), factorGroups, slackParticipationByBus); // this is the rhs for the moment
            describeSvcPilotFactorsRhs(factorGroups, context).forEach((col, column) -> column.writeInto(factorsStates, col));

            // solve system
            context.getJacobianMatrix().solveTransposed(factorsStates);

            OpenLoadFlowParameters contingencylfParametersExt = applyGenericContingencyParameters(context, lfParameters, lfParametersExt,
                    sensitivityAnalysisParametersExt.isStartWithFrozenACEmulation());

            setPredefinedResults(allLfFactors, new DisabledNetwork(), null);

            // calculate sensitivity values
            setFunctionReferences(allLfFactors);
            if (parameters.getOperatorStrategiesCalculationMode() != SensitivityOperatorStrategiesCalculationMode.ONLY_OPERATOR_STRATEGIES) {
                calculateSensitivityValues(allFactorHolder.getFactorsForBaseNetwork(), factorGroups, factorsStates, -1, -1, resultWriter);
            }

            NetworkState networkState = NetworkState.save(lfNetwork);

            if (parameters.getOperatorStrategiesCalculationMode() != SensitivityOperatorStrategiesCalculationMode.NONE) {
                List<Indexed<OperatorStrategy>> preContingencyOperatorStrategies = operatorStrategiesByContingencyId.getOrDefault(null, Collections.emptyList());
                if (!preContingencyOperatorStrategies.isEmpty()) {
                    for (Indexed<OperatorStrategy> operatorStrategy : preContingencyOperatorStrategies) {
                        LOGGER.info("Simulate operator strategy '{}'", operatorStrategy.value().getId());
                        LfOperatorStrategy lfOperatorStrategy = LfOperatorStrategy.create(operatorStrategy, lfActionById);
                        LfNetworkChange lfNetworkChange = new LfNetworkChange(lfNetwork, null, null, lfOperatorStrategy);
                        processNetworkChange(lfParameters, contingencylfParametersExt, acParameters, resultWriter, variablesTargetVoltageInfo, null, lfNetworkChange, networkReportNode,
                                lfNetwork, allFactorHolder, factorGroups, context, networkState, factorsStates);
                    }
                }
            }

            // we always restart from base case voltages for contingency simulation
            context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());

            propagatedContingencies.forEach(propagatedContingency -> {
                if (Thread.currentThread().isInterrupted()) {
                    throw new PowsyblException("Computation was interrupted");
                }
                computeLfContingency(acParameters, lfParameters, resultWriter, variablesTargetVoltageInfo, propagatedContingency,
                        lfNetwork, contingencylfParametersExt, networkReportNode, allFactorHolder, factorGroups, context, networkState,
                        factorsStates, operatorStrategiesByContingencyId, lfActionById);
            });
        }
    }

    private void computeLfContingency(AcLoadFlowParameters acParameters, LoadFlowParameters lfParameters, SensitivityResultWriter resultWriter,
                                      VariablesTargetVoltageInfo variablesTargetVoltageInfo, PropagatedContingency propagatedContingency, LfNetwork lfNetwork,
                                      OpenLoadFlowParameters contingencylfParametersExt, ReportNode networkReportNode,
                                      SensitivityFactorHolder<AcVariableType, AcEquationType> allFactorHolder,
                                      SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups, AcLoadFlowContext context,
                                      NetworkState networkState, DenseMatrix factorsStates,
                                      Map<String, List<Indexed<OperatorStrategy>>> operatorStrategiesByContingencyId,
                                      Map<String, LfAction> lfActionById) {
        LfContingency lfContingency = propagatedContingency.toLfContingency(lfNetwork).orElse(null);
        if (parameters.getOperatorStrategiesCalculationMode() != SensitivityOperatorStrategiesCalculationMode.ONLY_OPERATOR_STRATEGIES) {
            LOGGER.info("Simulate contingency '{}'", propagatedContingency.getContingency().getId());
            LfNetworkChange lfNetworkChange = new LfNetworkChange(lfNetwork, propagatedContingency, lfContingency, null);
            processNetworkChange(lfParameters, contingencylfParametersExt, acParameters, resultWriter, variablesTargetVoltageInfo, propagatedContingency, lfNetworkChange, networkReportNode,
                    lfNetwork, allFactorHolder, factorGroups, context, networkState, factorsStates);
        }

        if (parameters.getOperatorStrategiesCalculationMode() != SensitivityOperatorStrategiesCalculationMode.NONE) {
            List<Indexed<OperatorStrategy>> operatorStrategiesForThisContingency = operatorStrategiesByContingencyId
                    .getOrDefault(propagatedContingency.getContingency().getId(), Collections.emptyList());

            for (Indexed<OperatorStrategy> operatorStrategy : operatorStrategiesForThisContingency) {
                LOGGER.info("Simulate contingency '{}' and operator strategy '{}'", propagatedContingency.getContingency().getId(), operatorStrategy.value().getId());
                LfOperatorStrategy lfOperatorStrategy = LfOperatorStrategy.create(operatorStrategy, lfActionById);
                LfNetworkChange lfNetworkChange = new LfNetworkChange(lfNetwork, propagatedContingency, lfContingency, lfOperatorStrategy);
                processNetworkChange(lfParameters, contingencylfParametersExt, acParameters, resultWriter, variablesTargetVoltageInfo, propagatedContingency, lfNetworkChange, networkReportNode,
                        lfNetwork, allFactorHolder, factorGroups, context, networkState, factorsStates);
            }
        }
    }

    private void processNetworkChange(LoadFlowParameters lfParameters, OpenLoadFlowParameters contingencylfParametersExt, AcLoadFlowParameters acParameters,
                                      SensitivityResultWriter resultWriter, VariablesTargetVoltageInfo variablesTargetVoltageInfo,
                                      PropagatedContingency propagatedContingency, LfNetworkChange lfNetworkChange, ReportNode networkReportNode, LfNetwork lfNetwork,
                                      SensitivityFactorHolder<AcVariableType, AcEquationType> validFactorHolder, SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                      AcLoadFlowContext context, NetworkState networkState, DenseMatrix factorsStates) {
        if (lfNetworkChange.hasImpact()) {
            ReportNode postContSimReportNode = Reports.createPostContingencySimulation(networkReportNode, lfNetworkChange.getContingencyId());
            lfNetwork.setReportNode(postContSimReportNode);

            lfNetworkChange.apply(lfParameters.getBalanceType(), acParameters.getNetworkParameters());

            String contingencyId = lfNetworkChange.getContingencyId();
            List<LfSensitivityFactor<AcVariableType, AcEquationType>> contingencyFactors = contingencyId != null
                    ? validFactorHolder.getFactorsForContingency(contingencyId)
                    : validFactorHolder.getFactorsForBaseNetwork();
            contingencyFactors.forEach(lfFactor -> {
                lfFactor.setSensitivityValuePredefinedResult(null);
                lfFactor.setFunctionPredefinedResult(null);
                lfFactor.updateStatus();
            });

            setPredefinedResults(contingencyFactors, lfNetworkChange.getDisabledNetwork(), propagatedContingency);

            Map<LfBus, Double> postContingencySlackParticipationByBus;
            Set<LfBus> slackConnectedComponent;
            boolean hasChanged = false;
            if (lfNetworkChange.getDisabledNetwork().getBuses().isEmpty()) {
                // contingency not breaking connectivity
                LOGGER.debug("Contingency '{}' and operator strategy '{}' without loss of connectivity",
                        lfNetworkChange.getContingencyId(), lfNetworkChange.getOperatorStrategyId());
                slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
            } else {
                // contingency breaking connectivity
                LOGGER.debug("Contingency '{}' and operator strategy '{}' with loss of connectivity",
                        lfNetworkChange.getContingencyId(), lfNetworkChange.getOperatorStrategyId());
                // we check if factors are still in the main component
                slackConnectedComponent = new HashSet<>(lfNetwork.getBuses()).stream().filter(Predicate.not(lfNetworkChange.getDisabledNetwork().getBuses()::contains)).collect(Collectors.toSet());
                // we recompute GLSK weights if needed
                hasChanged = rescaleGlsk(factorGroups, lfNetworkChange.getDisabledNetwork().getBuses());
            }

            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution)
            if (lfParameters.isDistributedSlack()) {
                postContingencySlackParticipationByBus = getParticipatingElements(slackConnectedComponent, lfParameters.getBalanceType(), contingencylfParametersExt).stream().collect(Collectors.toMap(
                        ParticipatingElement::getLfBus, element -> -element.getFactor(), Double::sum));
            } else {
                postContingencySlackParticipationByBus = Collections.singletonMap(lfNetwork.getSynchronousNetworks().getFirst().getSlackBuses().getFirst(), -1d);
            }
            calculatePostContingencySensitivityValues(contingencyFactors, lfNetworkChange, lfNetwork, context, factorGroups, postContingencySlackParticipationByBus,
                    lfParameters, contingencylfParametersExt, resultWriter, variablesTargetVoltageInfo.hasTransformerTargetVoltage());

            if (hasChanged) {
                rescaleGlsk(factorGroups, Collections.emptySet());
            }
            networkState.restore();
        } else {
            // it means that the contingency has no impact.
            // we need to force the state vector to be re-initialized from base case network state
            AcSolverUtil.initStateVector(lfNetwork, context.getEquationSystem(), context.getParameters().getVoltageInitializer());

            calculateSensitivityValues(validFactorHolder.getFactorsForContingency(lfNetworkChange.getContingencyId()),
                    factorGroups, factorsStates, lfNetworkChange.getContingencyIndex(), lfNetworkChange.getOperatorStrategyIndex(), resultWriter);
            // write contingency status
            resultWriter.writeStateStatus(lfNetworkChange.getContingencyIndex(), lfNetworkChange.getOperatorStrategyIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
        }
    }

    private OpenLoadFlowParameters applyGenericContingencyParameters(AcLoadFlowContext context, LoadFlowParameters lfParameters,
                                                                     OpenLoadFlowParameters lfParametersExt, boolean startWithFrozenACEmulation) {
        OpenLoadFlowParameters contingencylfParametersExt = lfParametersExt;
        if (startWithFrozenACEmulation) {
            contingencylfParametersExt = OpenLoadFlowParameters.clone(lfParametersExt);
            contingencylfParametersExt.setStartWithFrozenACEmulation(true);
            context.getParameters().setOuterLoops(OpenLoadFlowParameters.createAcOuterLoops(lfParameters, contingencylfParametersExt));
        }
        context.getParameters().setFixVoltageTargets(false); // Checking voltage targets in contingency cases is unnecessary in most cases
        return contingencylfParametersExt;
    }
}
