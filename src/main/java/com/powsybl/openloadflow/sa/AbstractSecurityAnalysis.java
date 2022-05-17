/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.*;
import com.powsybl.security.interceptors.SecurityAnalysisInterceptor;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResults;
import com.powsybl.security.results.ThreeWindingsTransformerResult;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractSecurityAnalysis {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSecurityAnalysis.class);

    protected final Network network;

    protected final LimitViolationDetector detector;

    protected final LimitViolationFilter filter;

    protected final List<SecurityAnalysisInterceptor> interceptors = new ArrayList<>();

    protected final MatrixFactory matrixFactory;

    protected final GraphDecrementalConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    protected final StateMonitorIndex monitorIndex;

    protected AbstractSecurityAnalysis(Network network, LimitViolationDetector detector, LimitViolationFilter filter,
                                MatrixFactory matrixFactory, GraphDecrementalConnectivityFactory<LfBus, LfBranch> connectivityFactory, List<StateMonitor> stateMonitors) {
        this.network = Objects.requireNonNull(network);
        this.detector = Objects.requireNonNull(detector);
        this.filter = Objects.requireNonNull(filter);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
        this.monitorIndex = new StateMonitorIndex(stateMonitors);
    }

    public void addInterceptor(SecurityAnalysisInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor));
    }

    public boolean removeInterceptor(SecurityAnalysisInterceptor interceptor) {
        return interceptors.remove(Objects.requireNonNull(interceptor));
    }

    public CompletableFuture<SecurityAnalysisReport> run(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters,
                                                         ContingenciesProvider contingenciesProvider, ComputationManager computationManager) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        return CompletableFutureTask.runAsync(() -> {
            String oldWorkingVariantId = network.getVariantManager().getWorkingVariantId();
            network.getVariantManager().setWorkingVariant(workingVariantId);
            try {
                return runSync(workingVariantId, securityAnalysisParameters, contingenciesProvider, computationManager);
            } finally {
                network.getVariantManager().setWorkingVariant(oldWorkingVariantId);
            }
        }, computationManager.getExecutor());
    }

    abstract SecurityAnalysisReport runSync(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider,
                                            ComputationManager computationManager);

    /**
     * Detect violations on branches and on buses
     * @param branches branches on which the violation limits are checked
     * @param buses buses on which the violation limits are checked
     * @param violations list on which the violation limits encountered are added
     */
    protected void detectViolations(Stream<LfBranch> branches, Stream<LfBus> buses, Map<Pair<String, Branch.Side>, LimitViolation> violations) {
        // Detect violation limits on branches
        branches.forEach(branch -> detectBranchViolations(branch, violations));

        // Detect violation limits on buses
        buses.forEach(bus -> detectBusViolations(bus, violations));
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param branch branch of interest
     * @param violations list on which the violation limits encountered are added
     */
    protected void detectBranchViolations(LfBranch branch, Map<Pair<String, Branch.Side>, LimitViolation> violations) {
        // detect violation limits on a branch
        // Only detect the most serious one (findFirst) : limit violations are ordered by severity
        if (branch.getBus1() != null) {
            branch.getLimits1(LimitType.CURRENT).stream()
                .filter(temporaryLimit1 -> branch.getI1().eval() > temporaryLimit1.getValue())
                .findFirst()
                .map(temporaryLimit1 -> createLimitViolation1(branch, temporaryLimit1, LimitViolationType.CURRENT, PerUnit.ib(branch.getBus1().getNominalV()), branch.getI1().eval()))
                .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));

            branch.getLimits1(LimitType.ACTIVE_POWER).stream()
                  .filter(temporaryLimit1 -> branch.getP1().eval() > temporaryLimit1.getValue())
                  .findFirst()
                  .map(temporaryLimit1 -> createLimitViolation1(branch, temporaryLimit1, LimitViolationType.ACTIVE_POWER, PerUnit.SB, branch.getP1().eval()))
                  .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));

            //Apparent power is not relevant for fictitious branches and may be NaN
            double apparentPower1 = branch.computeApparentPower1();
            if (!Double.isNaN(apparentPower1)) {
                branch.getLimits1(LimitType.APPARENT_POWER).stream()
                      .filter(temporaryLimit1 -> apparentPower1 > temporaryLimit1.getValue())
                      .findFirst()
                      .map(temporaryLimit1 -> createLimitViolation1(branch, temporaryLimit1, LimitViolationType.APPARENT_POWER, PerUnit.SB, apparentPower1))
                      .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));
            }

        }
        if (branch.getBus2() != null) {
            branch.getLimits2(LimitType.CURRENT).stream()
                .filter(temporaryLimit2 -> branch.getI2().eval() > temporaryLimit2.getValue())
                .findFirst() // only the most serious violation is added (the limits are sorted in descending gravity)
                .map(temporaryLimit2 -> createLimitViolation2(branch, temporaryLimit2, LimitViolationType.CURRENT, PerUnit.ib(branch.getBus2().getNominalV()), branch.getI2().eval()))
                .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));

            branch.getLimits2(LimitType.ACTIVE_POWER).stream()
                  .filter(temporaryLimit2 -> branch.getP2().eval() > temporaryLimit2.getValue())
                  .findFirst()
                  .map(temporaryLimit2 -> createLimitViolation2(branch, temporaryLimit2, LimitViolationType.ACTIVE_POWER, PerUnit.SB, branch.getP2().eval()))
                  .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));

            //Apparent power is not relevant for fictitious branches and may be NaN
            double apparentPower2 = branch.computeApparentPower2();
            if (!Double.isNaN(apparentPower2)) {
                branch.getLimits2(LimitType.APPARENT_POWER).stream()
                      .filter(temporaryLimit2 -> apparentPower2 > temporaryLimit2.getValue())
                      .findFirst()
                      .map(temporaryLimit2 -> createLimitViolation2(branch, temporaryLimit2, LimitViolationType.APPARENT_POWER, PerUnit.SB, apparentPower2))
                      .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));
            }
        }
    }

    protected static LimitViolation createLimitViolation1(LfBranch branch, LfBranch.LfLimit temporaryLimit1,
                                                          LimitViolationType type, double scale, double value) {
        return new LimitViolation(branch.getId(), type, null,
                temporaryLimit1.getAcceptableDuration(), temporaryLimit1.getValue() * scale,
                (float) 1., value * scale, Branch.Side.ONE);
    }

    protected static LimitViolation createLimitViolation2(LfBranch branch, LfBranch.LfLimit temporaryLimit2,
                                                          LimitViolationType type, double scale, double value) {
        return new LimitViolation(branch.getId(), type, null,
                temporaryLimit2.getAcceptableDuration(), temporaryLimit2.getValue() * scale,
                (float) 1., value * scale, Branch.Side.TWO);
    }

    protected static Pair<String, Branch.Side> getSubjectSideId(LimitViolation limitViolation) {
        return Pair.of(limitViolation.getSubjectId(), limitViolation.getSide());
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param bus branch of interest
     * @param violations list on which the violation limits encountered are added
     */
    protected void detectBusViolations(LfBus bus, Map<Pair<String, Branch.Side>, LimitViolation> violations) {
        // detect violation limits on a bus
        double scale = bus.getNominalV();
        double busV = bus.getV();
        if (!Double.isNaN(bus.getHighVoltageLimit()) && busV > bus.getHighVoltageLimit()) {
            LimitViolation limitViolation1 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.HIGH_VOLTAGE, bus.getHighVoltageLimit() * scale,
                    (float) 1., busV * scale);
            violations.put(getSubjectSideId(limitViolation1), limitViolation1);
        }
        if (!Double.isNaN(bus.getLowVoltageLimit()) && busV < bus.getLowVoltageLimit()) {
            LimitViolation limitViolation2 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.LOW_VOLTAGE, bus.getLowVoltageLimit() * scale,
                    (float) 1., busV * scale);
            violations.put(getSubjectSideId(limitViolation2), limitViolation2);
        }
    }

    public static void distributedMismatch(LfNetwork network, double mismatch, LoadFlowParameters loadFlowParameters,
                                           OpenLoadFlowParameters openLoadFlowParameters) {
        if (loadFlowParameters.isDistributedSlack() && Math.abs(mismatch) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(loadFlowParameters.getBalanceType(), openLoadFlowParameters.isLoadPowerFactorConstant());
            activePowerDistribution.run(network, mismatch);
        }
    }

    /**
     * Compares two limit violations
     * @param violation1 first limit violation
     * @param violation2 second limit violation
     * @return true if violation2 is weaker than or equivalent to violation1, otherwise false
     */
    protected static boolean violationWeakenedOrEquivalent(LimitViolation violation1, LimitViolation violation2,
                                                           SecurityAnalysisParameters.IncreasedViolationsParameters violationsParameters) {
        if (violation2 != null && violation1.getLimitType() == violation2.getLimitType()) {
            if (violation2.getLimit() < violation1.getLimit()) {
                // the limit violated is smaller hence the violation is weaker, for flow violations only.
                // for voltage limits, we have only one limit by limit type.
                return true;
            }
            if (violation2.getLimit() == violation1.getLimit()) {
                // the limit violated is the same: we consider the violations equivalent if the new value is close to previous one.
                if (isFlowViolation(violation2)) {
                    return Math.abs(violation2.getValue()) <= Math.abs(violation1.getValue()) * (1 + violationsParameters.getFlowProportionalThreshold());
                } else if (violation2.getLimitType() == LimitViolationType.HIGH_VOLTAGE) {
                    double value = Math.min(violationsParameters.getHighVoltageAbsoluteThreshold(), violation1.getValue() * violationsParameters.getHighVoltageProportionalThreshold());
                    return violation2.getValue() <= violation1.getValue() + value;
                } else if (violation2.getLimitType() == LimitViolationType.LOW_VOLTAGE) {
                    return violation2.getValue() >= violation1.getValue() - Math.min(violationsParameters.getLowVoltageAbsoluteThreshold(), violation1.getValue() * violationsParameters.getLowVoltageProportionalThreshold());
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    protected static boolean isFlowViolation(LimitViolation limit) {
        return limit.getLimitType() == LimitViolationType.CURRENT || limit.getLimitType() == LimitViolationType.ACTIVE_POWER || limit.getLimitType() == LimitViolationType.APPARENT_POWER;
    }

    protected void addMonitorInfo(LfNetwork network, StateMonitor monitor, Collection<BranchResult> branchResultConsumer,
                                  Collection<BusResults> busResultsConsumer, Collection<ThreeWindingsTransformerResult> threeWindingsTransformerResultConsumer,
                                  Map<String, BranchResult> preContingencyBranchResults, String contingencyId) {
        network.getBranches().stream().filter(lfBranch -> monitor.getBranchIds().contains(lfBranch.getId()))
                .filter(lfBranch -> !lfBranch.isDisabled())
                .forEach(lfBranch -> {
                    BranchResult branchResult;
                    if (contingencyId == null) {
                        branchResult = lfBranch.createBranchResult(Double.NaN, Double.NaN);
                        preContingencyBranchResults.put(lfBranch.getId(), branchResult);
                    } else {
                        double preContingencyP1 = preContingencyBranchResults.get(lfBranch.getId()) != null ? preContingencyBranchResults.get(lfBranch.getId()).getP1() : Double.NaN;
                        double branchInContingencyP1 = preContingencyBranchResults.get(contingencyId) != null ? preContingencyBranchResults.get(contingencyId).getP1() : Double.NaN;
                        branchResult = lfBranch.createBranchResult(preContingencyP1, branchInContingencyP1);
                    }
                    branchResultConsumer.add(branchResult);
                });
        network.getBuses().stream().filter(lfBus -> monitor.getVoltageLevelIds().contains(lfBus.getVoltageLevelId()))
                .filter(lfBus -> !lfBus.isDisabled())
                .forEach(lfBus -> busResultsConsumer.add(lfBus.createBusResult()));
        monitor.getThreeWindingsTransformerIds().stream().filter(id -> network.getBusById(id + "_BUS0") != null && !network.getBusById(id + "_BUS0").isDisabled())
                .forEach(id -> threeWindingsTransformerResultConsumer.add(createThreeWindingsTransformerResult(id, network)));
    }

    private ThreeWindingsTransformerResult createThreeWindingsTransformerResult(String threeWindingsTransformerId, LfNetwork network) {
        LfBranch leg1 = network.getBranchById(LfLegBranch.getId(threeWindingsTransformerId, 1));
        LfBranch leg2 = network.getBranchById(LfLegBranch.getId(threeWindingsTransformerId, 2));
        LfBranch leg3 = network.getBranchById(LfLegBranch.getId(threeWindingsTransformerId, 3));
        double i1Base = PerUnit.ib(leg1.getBus1().getNominalV());
        double i2Base = PerUnit.ib(leg2.getBus1().getNominalV());
        double i3Base = PerUnit.ib(leg3.getBus1().getNominalV());
        return new ThreeWindingsTransformerResult(threeWindingsTransformerId,
                leg1.getP1().eval() * PerUnit.SB, leg1.getQ1().eval() * PerUnit.SB, leg1.getI1().eval() * i1Base,
                leg2.getP1().eval() * PerUnit.SB, leg2.getQ1().eval() * PerUnit.SB, leg2.getI1().eval() * i2Base,
                leg3.getP1().eval() * PerUnit.SB, leg3.getQ1().eval() * PerUnit.SB, leg3.getI1().eval() * i3Base);
    }
}
