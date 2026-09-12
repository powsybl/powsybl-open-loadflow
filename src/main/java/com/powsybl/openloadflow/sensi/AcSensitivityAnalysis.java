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
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.sensi.mt.BufferedFactorReader;
import com.powsybl.openloadflow.sensi.mt.SequentialSensitivityResultWriter;
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
                                            int contingencyIndex, SensitivityResultWriter resultWriter) {
        Set<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactorsSet = new HashSet<>(lfFactors);

        // VALID_ONLY_FOR_FUNCTION status is for factors where variable element is not in the main connected component but reference element is.
        // Therefore, the sensitivity is known to value 0 and the reference value can be computed.
        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> {
                    if (!filterSensitivityValue(0, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                        resultWriter.writeSensitivityValue(factor.getIndex(), contingencyIndex, -1, 0, unscaleFunction(factor, factor.getFunctionReference()));
                    }
                });

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
                }
                if (factor.getFunctionPredefinedResult() != null) {
                    ref = factor.getFunctionPredefinedResult();
                } else {
                    ref = factor.getFunctionReference();
                }
                double unscaledSensi = unscaleSensitivity(factor, sensi);
                if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                    resultWriter.writeSensitivityValue(factor.getIndex(), contingencyIndex, -1, unscaledSensi, unscaleFunction(factor, ref));
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

    private void calculatePostContingencySensitivityValues(List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors, LfContingency lfContingency,
                                                           LfNetwork lfNetwork, AcLoadFlowContext context, SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                                           Map<LfBus, Double> participationByBus,
                                                           LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                           int contingencyIndex, SensitivityResultWriter resultWriter,
                                                           boolean hasTransformerBusTargetVoltage) {
        if (lfParameters.isDistributedSlack() && Math.abs(lfContingency.getActivePowerLoss()) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(lfParameters.getBalanceType(),
                lfParametersExt.isLoadPowerFactorConstant(), lfParametersExt.isUseActiveLimits());
            activePowerDistribution.run(lfNetwork.getSynchronousNetworks().getFirst(), lfContingency.getActivePowerLoss());
        }

        if (!runLoadFlow(context, false)) {
            // write contingency status
            resultWriter.writeStateStatus(contingencyIndex, -1, SensitivityAnalysisResult.Status.FAILURE);
            return;
        }

        // write contingency status
        resultWriter.writeStateStatus(contingencyIndex, -1, SensitivityAnalysisResult.Status.SUCCESS);

        // if we have at least one bus target voltage linked to a ratio tap changer, we have to rebuild the AC equation
        // system obtained just before the transformer steps rounding.
        if (hasTransformerBusTargetVoltage) {
            for (LfBranch branch : lfNetwork.getBranches()) {
                branch.getVoltageControl().ifPresent(vc -> branch.setVoltageControlEnabled(true));
            }
            lfNetwork.fixTransformerVoltageControls();
        }

        if (factorGroups.hasMultiVariables() && (!lfContingency.getLostLoads().isEmpty() || !lfContingency.getLostGenerators().isEmpty())) {
            // FIXME. It does not work with a contingency that breaks connectivity and lose an isolate injection.
            Set<LfBus> affectedBuses = lfContingency.getLoadAndGeneratorBuses();
            rescaleGlsk(factorGroups, affectedBuses);
        }

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one

        // solve system
        DenseMatrix factorsStates = initFactorsRhs(context.getEquationSystem(), factorGroups, participationByBus); // this is the rhs for the moment
        fillSvcPilotFactorsRhs(factorGroups, factorsStates, context);
        context.getJacobianMatrix().solveTransposed(factorsStates);
        setFunctionReferences(lfFactors);

        // calculate sensitivity values
        calculateSensitivityValues(lfFactors, factorGroups, factorsStates, contingencyIndex, resultWriter);
    }

    /**
     * Fills the RHS columns for SVC_PILOT_TARGET_VOLTAGE factor groups: for each
     * such group, the RHS becomes a linear combination of the controlled buses'
     * BUS_TARGET_V columns, weighted by the closed-loop coordination coefficients
     * (see {@link SvcPilotPointClosedLoopSensitivity}).  Other factor groups are untouched.
     */
    private static void fillSvcPilotFactorsRhs(
            SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
            DenseMatrix factorsStates,
            AcLoadFlowContext context) {
        boolean hasSvcPilot = factorGroups.getList().stream().anyMatch(group -> {
            LfSensitivityFactor<AcVariableType, AcEquationType> probe = group.getFactors().isEmpty() ? null : group.getFactors().get(0);
            return probe != null && probe.getVariableType() == SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE;
        });
        if (!hasSvcPilot) {
            return;
        }
        // Build + factorize the all-zones coordination matrix ONCE (it is pilot-independent) and reuse it for
        // every queried pilot, instead of one full assembly + LU per pilot.
        try (SvcPilotPointClosedLoopSensitivity.Coordination coordination =
                     SvcPilotPointClosedLoopSensitivity.buildCoordination(context)) {
            if (coordination == null) {
                return; // no active SVC zone
            }
            Map<LfBus, Map<LfBus, Double>> weightsByPilot = new HashMap<>();
            for (SensitivityFactorGroup<AcVariableType, AcEquationType> group : factorGroups.getList()) {
                LfSensitivityFactor<AcVariableType, AcEquationType> probe = group.getFactors().isEmpty() ? null : group.getFactors().get(0);
                if (probe == null || probe.getVariableType() != SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE) {
                    continue;
                }
                LfBus pilotBus = (LfBus) ((SingleVariableLfSensitivityFactor<AcVariableType, AcEquationType>) probe).getVariableElement();
                Map<LfBus, Double> weights = weightsByPilot.computeIfAbsent(pilotBus, coordination::weightsForPilot);
                int col = group.getIndex();
                for (var entry : weights.entrySet()) {
                    LfBus controlled = entry.getKey();
                    double w = entry.getValue();
                    context.getEquationSystem()
                            .getEquation(controlled.getNum(), AcEquationType.BUS_TARGET_V)
                            .ifPresent(eq -> factorsStates.set(eq.getColumn(), col, w));
                }
            }
        }
    }

    /**
     * Reverse-mode (adjoint / VJP) dual of the forward AC sensitivity: given output cotangents {@code ȳ}
     * over the declared functions, return {@code θ̄ = Sᵀ·ȳ} over the variable groups — WITHOUT
     * materialising the sensitivity matrix {@code S}. A single transpose solve on the retained (e.g.
     * networkCacheEnabled) factorization, reusing {@link #initFactorsRhs} (∂F/∂p),
     * {@link #fillSvcPilotFactorsRhs} (SVC pilot closed-loop) and the function equation terms (∂f/∂x).
     * Assumes a load flow already converged on {@code context} (its Jacobian is factorized), exactly as
     * the forward path assumes.
     *
     * @param cotangents dL/dfunction, keyed by the base-network sensitivity factor.
     * @return θ̄ indexed by factor-group index ({@link SensitivityFactorGroup#getIndex()}).
     */
    @SuppressWarnings("unchecked")
    private double[] analyseAdjoint(AcLoadFlowContext context,
                                   SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                   Map<LfSensitivityFactor<AcVariableType, AcEquationType>, Double> cotangents,
                                   Map<LfBus, Double> slackParticipationByBus) {
        var equationSystem = context.getEquationSystem();
        int equationCount = equationSystem.getIndex().getColumnCount();

        // ∂F/∂p columns (one per variable group), including the SVC pilot closed-loop — identical to forward.
        DenseMatrix parameterRhs = initFactorsRhs(equationSystem, factorGroups, slackParticipationByBus);
        fillSvcPilotFactorsRhs(factorGroups, parameterRhs, context);

        // x̄ = Σ_f ȳ_f · (∂f/∂x): transpose of calculateSensi — scatter der() into the equation rows.
        // ȳ is per monitored FUNCTION, so add each function's ∂f/∂x exactly once even though a function
        // paired with several variables produces several factors that share the same cotangent.
        double[] xBar = new double[equationCount];
        Set<Pair<SensitivityFunctionType, String>> seenFunctions = new HashSet<>();
        for (var e : cotangents.entrySet()) {
            double yBar = e.getValue();
            if (yBar == 0.0) {
                continue;
            }
            var factor = e.getKey();
            if (!seenFunctions.add(Pair.of(factor.getFunctionType(), factor.getFunctionId()))) {
                continue;
            }
            // getFunctionEquationTerm() is declared as Derivable; the in-scope AC function types are all
            // EquationTerms, which expose getVariables()/der() needed to scatter (∂f/∂x)ᵀ into x̄.
            if (!(factor.getFunctionEquationTerm() instanceof EquationTerm)) {
                continue;
            }
            EquationTerm<AcVariableType, AcEquationType> functionTerm =
                    (EquationTerm<AcVariableType, AcEquationType>) factor.getFunctionEquationTerm();
            // scale by the function's per-unit base so θ̄ comes out UNSCALED (physical), the dual of the
            // forward get_sensitivity_matrix: unscaleSensitivity = funcBase(f) / varBase(v), applied here as
            // funcBase on the function (x̄) side and varBase on the variable (θ̄) side.
            double functionBase = getFunctionBaseValue(factor);
            for (Variable<AcVariableType> variable : functionTerm.getVariables()) {
                int row = variable.getRow();
                if (row >= 0) {
                    xBar[row] += yBar * functionBase * functionTerm.der(variable);
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
            double dot = 0.0;
            for (int row = 0; row < equationCount; row++) {
                dot += parameterRhs.get(row, col) * lambda[row];
            }
            double thetaG = dot;
            for (var factor : group.getFactors()) {
                Double yBar = cotangents.get(factor);
                if (yBar != null && yBar != 0.0) {
                    thetaG += yBar * getFunctionBaseValue(factor) * computeParameterDirectPartial(factor);
                }
            }
            // divide by the variable's per-unit base to finish the unscale (see the x̄ scaling above): θ̄ then
            // equals the forward's unscaled Sᵀ·ȳ in physical units, per variable.
            thetaBar[col] = thetaG / getVariableBaseValue(group.getFactors().get(0));
        }
        return thetaBar;
    }

    /**
     * Key for the {@code functionCotangentsById} map of {@link #runAdjoint}: a monitored function is a
     * (functionType, functionId) pair, not just an id (a branch is monitored by several function types
     * that share its id), so the cotangent must be keyed by both.
     */
    public static String functionCotangentKey(SensitivityFunctionType functionType, String functionId) {
        return functionType.name() + ' ' + functionId;
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
     * <p>The caller passes its natural per-function-type {@link AdjointBlock}s (monitored functions +
     * already type-resolved variables) and the cotangents beside them; OpenLoadFlow owns the factor-set
     * construction and the pipeline.</p>
     *
     * <p>Reuses the AC load flow retained in the network cache ({@code networkCacheEnabled}): a cached AC
     * load flow must have run on {@code network} first. Instead of materialising the sensitivity matrix
     * {@code S}, it contracts an output cotangent to return {@code θ̄ = Sᵀ·ȳ}.</p>
     *
     * <p>The whole public adjoint surface is this method, {@link AdjointBlock} / {@link AdjointVariable}
     * to shape the request, and {@link #functionCotangentKey} to key the cotangent map.</p>
     *
     * @param blocks                 per-function-type monitored functions + the variables to differentiate.
     *                               Function and variable ids are resolved internally, so the cotangents
     *                               and the returned map stay keyed by the ids the caller passed here.
     * @param functionCotangentsById dL/dfunction keyed by {@link #functionCotangentKey}; the caller keeps
     *                               this build since it depends on the caller's matrix column layout.
     * @return dL/dvariable, keyed by variable id.
     */
    public Map<String, Double> runAdjoint(Network network, String workingVariantId,
                                          List<SensitivityVariableSet> variableSets,
                                          List<AdjointBlock> blocks,
                                          Map<String, Double> functionCotangentsById) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(functionCotangentsById);
        List<SensitivityFactor> factors = buildAdjointFactors(network, blocks);
        network.getVariantManager().setWorkingVariant(workingVariantId);

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
        // A LfSensitivityFactor's getFunctionId()/getVariableId() are the RESOLVED LF element ids (a
        // BUS_VOLTAGE function id is resolved to the bus-view bus id, an SVC pilot variable to its pilot
        // bus), NOT the caller's ids — only getIndex() links back to the declared factor. So match the
        // cotangent (in) and the θ̄ map (out) through `factors` by index, using the ids the caller passed.
        SensitivityFactorHolder<AcVariableType, AcEquationType> allFactorHolder =
                readAndCheckFactors(network, variableSetsById, new SensitivityFactorModelReader(factors, network), lfNetwork, breakers);
        List<LfSensitivityFactor<AcVariableType, AcEquationType>> validLfFactors = allFactorHolder.getAllFactors().stream()
                .filter(f -> f.getStatus() == LfSensitivityFactor.Status.VALID)
                .collect(Collectors.toList());
        SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups = createFactorGroups(validLfFactors);

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

        // Guard the FUNCTION side of the boundary, the mirror of an absent variable on the output side:
        // a cotangent whose (functionType, functionId) key matches NO emitted factor is silently dropped
        // below, so its contribution to x̄ vanishes and θ̄ is wrong with no error. That is precisely the
        // shape of the resolved-vs-caller id bug this class carried — caught here, unambiguously, because
        // every block function produces a factor carrying the CALLER's id (buildAdjointFactors), so an
        // unmatched cotangent means the caller keyed a function that is in no block. The out-of-component
        // "legitimate zero" case does NOT trip this: that function still has an emitted factor (only its
        // validity is dropped later), so its key is present here.
        Set<String> declaredFunctionKeys = new HashSet<>();
        for (SensitivityFactor f : factors) {
            declaredFunctionKeys.add(functionCotangentKey(f.getFunctionType(), f.getFunctionId()));
        }
        List<String> orphanCotangents = functionCotangentsById.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() != 0.0)
                .map(Map.Entry::getKey)
                .filter(k -> !declaredFunctionKeys.contains(k))
                .sorted()
                .toList();
        if (!orphanCotangents.isEmpty()) {
            throw new PowsyblException("runAdjoint: " + orphanCotangents.size() + " cotangent(s) key no "
                    + "monitored function in any block, so they would be silently dropped and θ̄ would omit "
                    + "their contribution. Check the function ids match the block's (same id convention on "
                    + "both sides): " + orphanCotangents);
        }

        // cotangent per factor = the cotangent of its monitored function, matched by the caller's
        // (functionType, functionId): a branch can be monitored by several function types (current and
        // active power, on both sides) that share the same functionId, so the key must include the type.
        Map<LfSensitivityFactor<AcVariableType, AcEquationType>, Double> cotangents = new HashMap<>();
        for (var factor : validLfFactors) {
            SensitivityFactor declared = factors.get(factor.getIndex());
            Double yBar = functionCotangentsById.get(functionCotangentKey(declared.getFunctionType(), declared.getFunctionId()));
            if (yBar != null && yBar != 0.0) {
                cotangents.put(factor, yBar);
            }
        }

        double[] thetaBar = analyseAdjoint(context, factorGroups, cotangents, slackParticipationByBus);

        Map<String, Double> gradientByVariableId = new LinkedHashMap<>();
        for (var group : factorGroups.getList()) {
            gradientByVariableId.put(factors.get(group.getFactors().get(0).getIndex()).getVariableId(), thetaBar[group.getIndex()]);
        }
        return gradientByVariableId;
    }

    /**
     * One monitored-function-type block of a {@link #runAdjoint} request: the functions monitored under
     * {@code functionType}, and the variables to differentiate against (each carrying its resolved
     * {@link SensitivityVariableType} and whether it is a {@link SensitivityVariableSet} id).
     *
     * <p>Blocks may carry DIFFERENT variable lists: one request can serve several lever families at once,
     * since they all contract the same cotangent and differ only in the per-variable contraction. Every
     * block's variables get a θ̄ group (gated by {@code runAdjointGivesEveryVariableSetItsOwnGradient}) —
     * do not narrow that to the first block's, which silently zeroed every other family.</p>
     */
    public record AdjointBlock(SensitivityFunctionType functionType, List<String> functionIds,
                               List<AdjointVariable> variables) {
    }

    /** A differentiation variable of an {@link AdjointBlock}: its id, resolved type, and set-ness. */
    public record AdjointVariable(String id, SensitivityVariableType type, boolean variableSet) {
    }

    /**
     * Build the O(functions + variables) reverse-mode factor set from the caller's {@link AdjointBlock}s —
     * the minimal set that reproduces the full functions×variables cross product. Package-private rather
     * than private so {@code AcSensitivityAnalysisAdjointTest} can assert the set it builds really is
     * smaller than that cross product, whose θ̄ the equivalence gates check against the forward matrix.
     * Not part of the API — the caller passes blocks, never factors.
     * <ul>
     *   <li>one factor per monitored function, paired with the first variable — feeds x̄ via the cotangent map;</li>
     *   <li>the self-pair for a variable that is itself a monitored function — carries the direct term ∂f/∂p;</li>
     *   <li>a group guarantee: any variable never appearing as a function still gets one factor (fixed
     *       function, whose direct term is 0 for it) so it gets its own θ̄ group.</li>
     * </ul>
     * Deduplicated by (functionType, resolvedFunctionId, variableId). This is the construction that used
     * to live in the pypowsybl caller; owning it here keeps it under the OLF equivalence gates.
     */
    static List<SensitivityFactor> buildAdjointFactors(Network network, List<AdjointBlock> blocks) {
        // Reject an empty declaration rather than index into it. Each of these is a caller error with no
        // meaningful answer — a VJP with no function has no cotangent to propagate, and one with no
        // variable has no θ̄ to return — and the alternative to naming it is an IndexOutOfBoundsException
        // thrown from the middle of this method (blocks.get(0) / functionIds().get(0) / variables().get(0)),
        // which says nothing about which block was empty. An empty θ̄ returned quietly would be worse
        // still: it reads exactly like "none of your levers can help".
        if (blocks.isEmpty()) {
            throw new PowsyblException("runAdjoint needs at least one AdjointBlock: with no monitored "
                    + "function there is no cotangent to propagate and no gradient to return.");
        }
        for (AdjointBlock block : blocks) {
            if (block.functionIds().isEmpty()) {
                throw new PowsyblException("AdjointBlock for function type " + block.functionType()
                        + " declares no monitored function; a block exists to say which functions to "
                        + "differentiate, so an empty one cannot contribute to x̄ nor anchor a θ̄ group.");
            }
            if (block.variables().isEmpty()) {
                throw new PowsyblException("AdjointBlock for function type " + block.functionType()
                        + " declares no variable to differentiate against; θ̄ = Sᵀ·ȳ would be empty. "
                        + "Skip the runAdjoint call instead when a lever family is empty.");
            }
        }

        List<SensitivityFactor> factors = new ArrayList<>();
        Set<String> emittedPairs = new HashSet<>();       // functionType|resolvedFunctionId|variableId
        Set<String> variablesWithGroup = new HashSet<>();

        for (AdjointBlock block : blocks) {
            SensitivityFunctionType functionType = block.functionType();
            Set<String> functionIdSet = new HashSet<>(block.functionIds());

            // x̄: one factor per function, paired with the first variable (base case only — adjoint ignores contingencies)
            AdjointVariable v0 = block.variables().get(0);
            for (String functionId : block.functionIds()) {
                addAdjointFactor(factors, emittedPairs, network, functionType, functionId, v0);
            }
            variablesWithGroup.add(v0.id());

            // self-pair (direct term): a variable that is itself a monitored function in this block
            for (AdjointVariable v : block.variables()) {
                if (functionIdSet.contains(v.id())) {
                    addAdjointFactor(factors, emittedPairs, network, functionType, v.id(), v);
                    variablesWithGroup.add(v.id());
                }
            }
        }

        // group guarantee: every variable (across all blocks) needs a θ̄ group; give the ungrouped ones one
        // factor with a fixed function whose direct term is 0 for them (safe — see the OLF test).
        AdjointBlock b0 = blocks.get(0);
        SensitivityFunctionType ft0 = b0.functionType();
        String f0 = b0.functionIds().get(0);
        for (AdjointBlock block : blocks) {
            for (AdjointVariable v : block.variables()) {
                if (variablesWithGroup.add(v.id())) {
                    addAdjointFactor(factors, emittedPairs, network, ft0, f0, v);
                }
            }
        }
        return factors;
    }

    private static void addAdjointFactor(List<SensitivityFactor> factors, Set<String> emittedPairs, Network network,
                                         SensitivityFunctionType functionType, String functionId, AdjointVariable v) {
        // Dedup on the RESOLVED id so two caller ids for the same LF element (e.g. two bus-breaker ids on one
        // bus-view bus) collapse to a single factor, but keep the caller's ORIGINAL functionId on the emitted
        // factor: runAdjoint reads the cotangent by declared.getFunctionId() (see the id note there), so a
        // resolved id here would miss the caller's cotangent key and return a silent zero θ̄ (a BUS_VOLTAGE
        // function, whose bus-breaker id resolves to a different bus-view id, hit exactly this).
        String resolvedFunctionId = SensitivityFactor.resolveBusId(functionId, functionType, network);
        if (emittedPairs.add(functionType.name() + '|' + resolvedFunctionId + '|' + v.id())) {
            factors.add(new SensitivityFactor(functionType, functionId, v.type(), v.id(),
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
    public void analyse(Network network, String workingVariantId, List<Contingency> contingencies, List<OperatorStrategy> operatorStrategies,
                        List<Action> actions, PropagatedContingencyCreationParameters creationParameters,
                        List<SensitivityVariableSet> variableSets, SensitivityFactorReader factorReader,
                        SensitivityResultWriter resultWriter, ReportNode sensiReportNode,
                        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt,
                        Executor executor) throws ExecutionException {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(resultWriter);
        Objects.requireNonNull(sensiReportNode);

        if (!operatorStrategies.isEmpty()) {
            throw new PowsyblException("AC sensitivity analysis does not support operator strategies");
        }

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

        if (sensitivityAnalysisParametersExt.getThreadCount() == 1) {
            LfTopoConfig topoConfig = new LfTopoConfig();
            List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);
            AcLoadFlowParameters acParameters = makeAcLoadFlowParameters(network, slackBusSelector, lfParameters, lfParametersExt, topoConfig.isBreaker());
            try (LfNetworkList lfNetworks = Networks.loadWithReconnectableElements(network, topoConfig, acParameters.getNetworkParameters(), sensiReportNode)) {

                analyzeContingencySet(network, lfNetworks, propagatedContingencies, acParameters, lfParameters, lfParametersExt, variableSets, factorReader,
                        topoConfig.isBreaker(), resultWriter, variablesTargetVoltageInfo, sensitivityAnalysisParametersExt);
            }
        } else {
            try (SequentialSensitivityResultWriter sequentialSensitivityResultWriter = new SequentialSensitivityResultWriter(resultWriter)) {
                BufferedFactorReader bufferedFactorReader = new BufferedFactorReader(factorReader);
                var contingenciesPartitions = Lists2.partition(contingencies, sensitivityAnalysisParametersExt.getThreadCount());
                ContingencyMultiThreadHelper.ParameterProvider<AcLoadFlowParameters> parameterProvider = topoConfig -> makeAcLoadFlowParameters(network,
                    slackBusSelector, lfParameters, lfParametersExt, topoConfig.isBreaker());
                ContingencyMultiThreadHelper.ContingencyRunner<AcLoadFlowParameters> contingencyRunner = (partitionNum, lfNetworks, propagatedContingencies, acParameters) -> {
                    analyzeContingencySet(network, lfNetworks, propagatedContingencies, acParameters, lfParameters, lfParametersExt, variableSets, bufferedFactorReader,
                        acParameters.getNetworkParameters().isBreakers(), sequentialSensitivityResultWriter, variablesTargetVoltageInfo, sensitivityAnalysisParametersExt);
                    sequentialSensitivityResultWriter.flush(); // flush the batch of data kept in this thread
                };
                ContingencyMultiThreadHelper.ReportMerger reportMerger = ContingencyMultiThreadHelper::mergeReportThreadResults;

                ContingencyMultiThreadHelper.createLFNetworksPerContingencyPartitionAndRunAnalysis(network, workingVariantId, contingenciesPartitions, creationParameters, new LfTopoConfig(),
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

    private void analyzeContingencySet(Network network, LfNetworkList lfNetworks, List<PropagatedContingency> contingencies, AcLoadFlowParameters acParameters,
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

        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
        SensitivityFactorHolder<AcVariableType, AcEquationType> allFactorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork, breakers);
        List<LfSensitivityFactor<AcVariableType, AcEquationType>> allLfFactors = allFactorHolder.getAllFactors();
        LOGGER.info("Running AC sensitivity analysis with {} factors and {} contingencies", allLfFactors.size(), contingencies.size());

        // next we only work with valid and valid only for function factors
        var validFactorHolder = writeInvalidFactors(allFactorHolder, resultWriter, contingencies, new HashMap<>(), parameters);
        var validLfFactors = validFactorHolder.getAllFactors();

        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {

            runLoadFlow(context, true);

            acParameters.setVoltageInitReport(false);

            // index factors by variable group to compute a minimal number of states
            SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups = createFactorGroups(validLfFactors.stream()
                    .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID).collect(Collectors.toList()));

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
            fillSvcPilotFactorsRhs(factorGroups, factorsStates, context);

            // solve system
            context.getJacobianMatrix().solveTransposed(factorsStates);

            // calculate sensitivity values
            setFunctionReferences(validLfFactors);
            calculateSensitivityValues(validFactorHolder.getFactorsForBaseNetwork(), factorGroups, factorsStates, -1, resultWriter);

            NetworkState networkState = NetworkState.save(lfNetwork);

            // we always restart from base case voltages for contingency simulation
            context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());

            OpenLoadFlowParameters contingencylfParametersExt = applyGenericContingencyParameters(context, lfParameters, lfParametersExt,
                    sensitivityAnalysisParametersExt.isStartWithFrozenACEmulation());

            contingencies.forEach(contingency -> {
                if (Thread.currentThread().isInterrupted()) {
                    throw new PowsyblException("Computation was interrupted");
                }
                LOGGER.info("Simulate contingency '{}'", contingency.getContingency().getId());
                contingency.toLfContingency(lfNetwork)
                    .ifPresentOrElse(lfContingency -> computeLfContingency(lfContingency, lfNetwork, lfParameters,
                        validFactorHolder, networkReportNode, contingency, factorGroups, contingencylfParametersExt, context,
                            resultWriter, variablesTargetVoltageInfo, networkState),
                        () -> {
                            // it means that the contingency has no impact.
                            // we need to force the state vector to be re-initialized from base case network state
                            AcSolverUtil.initStateVector(lfNetwork, context.getEquationSystem(), context.getParameters().getVoltageInitializer());

                            calculateSensitivityValues(validFactorHolder.getFactorsForContingency(contingency.getContingency().getId()),
                                factorGroups, factorsStates, contingency.getIndex(), resultWriter);
                            // write contingency status
                            resultWriter.writeStateStatus(contingency.getIndex(), -1, SensitivityAnalysisResult.Status.NO_IMPACT);
                        });
            });
        }

    }

    private void computeLfContingency(LfContingency lfContingency, LfNetwork lfNetwork, LoadFlowParameters lfParameters,
                                      SensitivityFactorHolder<AcVariableType, AcEquationType> validFactorHolder, ReportNode networkReportNode,
                                      PropagatedContingency contingency, SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                      OpenLoadFlowParameters contingencyLfParametersExt, AcLoadFlowContext context,
                                      SensitivityResultWriter resultWriter, VariablesTargetVoltageInfo variablesTargetVoltageInfo,
                                      NetworkState networkState) {
        ReportNode postContSimReportNode = Reports.createPostContingencySimulation(networkReportNode, lfContingency.getId());
        lfNetwork.setReportNode(postContSimReportNode);

        List<LfSensitivityFactor<AcVariableType, AcEquationType>> contingencyFactors = validFactorHolder.getFactorsForContingency(lfContingency.getId());
        contingencyFactors.forEach(lfFactor -> {
            lfFactor.setSensitivityValuePredefinedResult(null);
            lfFactor.setFunctionPredefinedResult(null);
        });

        lfContingency.apply(lfParameters.getBalanceType());

        setPredefinedResults(contingencyFactors, lfContingency.getDisabledNetwork(), contingency);

        Map<LfBus, Double> postContingencySlackParticipationByBus;
        Set<LfBus> slackConnectedComponent;
        boolean hasChanged = false;
        if (lfContingency.getDisabledNetwork().getBuses().isEmpty()) {
            // contingency not breaking connectivity
            LOGGER.debug("Contingency '{}' without loss of connectivity", lfContingency.getId());
            slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
        } else {
            // contingency breaking connectivity
            LOGGER.debug("Contingency '{}' with loss of connectivity", lfContingency.getId());
            // we check if factors are still in the main component
            slackConnectedComponent = new HashSet<>(lfNetwork.getBuses()).stream()
                .filter(Predicate.not(lfContingency.getDisabledNetwork().getBuses()::contains))
                .collect(Collectors.toSet());
            // we recompute GLSK weights if needed
            hasChanged = rescaleGlsk(factorGroups, lfContingency.getDisabledNetwork().getBuses());
        }

        // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
        // buses that contain elements participating to slack distribution)
        if (lfParameters.isDistributedSlack()) {
            postContingencySlackParticipationByBus = getParticipatingElements(slackConnectedComponent, lfParameters.getBalanceType(), contingencyLfParametersExt).stream()
                .collect(Collectors.toMap(ParticipatingElement::getLfBus, element -> -element.getFactor(), Double::sum));
        } else {
            postContingencySlackParticipationByBus = Collections.singletonMap(lfNetwork.getSynchronousNetworks().getFirst().getSlackBuses().getFirst(), -1d);
        }
        calculatePostContingencySensitivityValues(contingencyFactors, lfContingency, lfNetwork, context, factorGroups, postContingencySlackParticipationByBus,
            lfParameters, contingencyLfParametersExt, lfContingency.getIndex(), resultWriter, variablesTargetVoltageInfo.hasTransformerTargetVoltage());

        if (hasChanged) {
            rescaleGlsk(factorGroups, Collections.emptySet());
        }
        networkState.restore();

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
