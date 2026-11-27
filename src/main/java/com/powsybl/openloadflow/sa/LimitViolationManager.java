/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationBuilder;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LimitScalingManager;
import com.powsybl.openloadflow.network.util.NetworkStateBranchFlows;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.*;
import com.powsybl.security.limitscaling.LimitScaling;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Predicate;

/**
 * Limit violation manager. A reference limit violation manager could be specified to only report violations that
 * are more severe than reference one.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LimitViolationManager {

    private final LimitViolationManager reference;

    private final LimitScalingManager limitScalingManager;

    private SecurityAnalysisParameters.IncreasedViolationsParameters parameters;

    private final Map<Pair<Object, String>, LimitViolation> violations = new LinkedHashMap<>(); // All limit violations indexed by network element and OperationalLimitsGroup (if it exists)

    public LimitViolationManager(LimitViolationManager reference, List<LimitScaling> limitScalings,
                                 SecurityAnalysisParameters.IncreasedViolationsParameters parameters) {
        this.reference = reference;
        if (reference != null) {
            this.parameters = Objects.requireNonNull(parameters);
        }
        this.limitScalingManager = LimitScalingManager.create(limitScalings);
    }

    public LimitViolationManager(List<LimitScaling> limitScalings) {
        this(null, limitScalings, null);
    }

    public List<LimitViolation> getLimitViolations() {
        return new ArrayList<>(violations.values());
    }

    /**
     * Detect violations on branches and on buses
     * @param network network on which the violation limits are checked
     * @param branchFlows branch flows of the current network state
     */
    public void detectViolations(LfNetwork network, NetworkStateBranchFlows branchFlows) {
        detectViolations(network, LfElement::isDisabled, branchFlows);
    }

    /**
     * Detect violations on branches and on buses
     * @param network network on which the violation limits are checked
     * @param isBranchDisabled predicate to evaluate if a branch of the network is disabled or not
     * @param branchFlows branch flows of the current network state
     */
    public void detectViolations(LfNetwork network, Predicate<LfBranch> isBranchDisabled, NetworkStateBranchFlows branchFlows) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(branchFlows);

        // Detect violation limits on branches
        network.getBranches().stream().filter(b -> !isBranchDisabled.test(b)).forEach(b -> detectBranchViolations(b, branchFlows));

        // Detect violation limits on buses
        network.getBuses().stream().filter(b -> !b.isDisabled()).forEach(this::detectBusViolations);

        // Detect voltage angle limits
        network.getVoltageAngleLimits().stream()
                .filter(limit -> !limit.getFrom().isDisabled() && !limit.getTo().isDisabled())
                .forEach(this::detectVoltageAngleLimitViolations);
    }

    private static Pair<String, ThreeSides> getSubjectIdSide(LimitViolation limitViolation) {
        return Pair.of(limitViolation.getSubjectId(), limitViolation.getSide());
    }

    private void addLimitViolation(LimitViolation limitViolation, Pair<Object, String> key) {
        if (reference != null) {
            var referenceLimitViolation = reference.violations.get(key);
            if (referenceLimitViolation == null || !violationWeakenedOrEquivalent(referenceLimitViolation, limitViolation, parameters)) {
                violations.put(key, limitViolation);
            }
        } else {
            violations.put(key, limitViolation);
        }
    }

    private void addBranchLimitViolation(LimitViolation limitViolation) {
        addLimitViolation(limitViolation, Pair.of(getSubjectIdSide(limitViolation), limitViolation.getOperationalLimitsGroupId()));
    }

    private void addBusLimitViolation(LimitViolation limitViolation, LfBus bus) {
        addLimitViolation(limitViolation, Pair.of(bus.getId(), limitViolation.getOperationalLimitsGroupId()));
    }

    private void addVoltageAngleLimitViolation(LimitViolation limitViolation, LfNetwork.LfVoltageAngleLimit voltageAngleLimit) {
        addLimitViolation(limitViolation, Pair.of(voltageAngleLimit.getId(), limitViolation.getOperationalLimitsGroupId()));
    }

    private void detectBranchCurrentViolations(LfBranch branch, LfBus bus, double i, LfBranch.LfLimitsGroup limitsGroup, TwoSides side) {
        List<LfBranch.LfLimit> limits = limitsGroup.getSortedLimits();
        String operationalLimitsGroupId = limitsGroup.getOperationalLimitsGroupId();
        for (LfBranch.LfLimit limit : limits) {
            if (i > limit.getScaledValue()) {
                addBranchLimitViolation(createLimitViolation(branch, operationalLimitsGroupId, limit, LimitViolationType.CURRENT, PerUnit.ib(bus.getNominalV()), i, side));
                break;
            }
        }
    }

    private void detectBranchActivePowerViolations(LfBranch branch, double p, LfBranch.LfLimitsGroup limitsGroup, TwoSides side) {
        List<LfBranch.LfLimit> limits = limitsGroup.getSortedLimits();
        String operationalLimitsGroupId = limitsGroup.getOperationalLimitsGroupId();
        for (LfBranch.LfLimit limit : limits) {
            if (Math.abs(p) > limit.getScaledValue()) {
                addBranchLimitViolation(createLimitViolation(branch, operationalLimitsGroupId, limit, LimitViolationType.ACTIVE_POWER, PerUnit.SB, p, side));
                break;
            }
        }
    }

    private void detectBranchApparentPowerViolations(LfBranch branch, double s, LfBranch.LfLimitsGroup limitsGroup, TwoSides side) {
        List<LfBranch.LfLimit> limits = limitsGroup.getSortedLimits();
        String operationalLimitsGroupId = limitsGroup.getOperationalLimitsGroupId();
        //Apparent power is not relevant for fictitious branches and may be NaN
        if (!Double.isNaN(s)) {
            for (LfBranch.LfLimit limit : limits) {
                if (s > limit.getScaledValue()) {
                    addBranchLimitViolation(createLimitViolation(branch, operationalLimitsGroupId, limit, LimitViolationType.APPARENT_POWER, PerUnit.SB, s, side));
                    break;
                }
            }
        }
    }

    private record BranchSideLimits(List<LfBranch.LfLimitsGroup> current, List<LfBranch.LfLimitsGroup> activePower,
                                    List<LfBranch.LfLimitsGroup> apparentPower) {

        private static final BranchSideLimits EMPTY = new BranchSideLimits(List.of(), List.of(), List.of());

        private boolean isEmpty() {
            return current.isEmpty() && activePower.isEmpty() && apparentPower.isEmpty();
        }
    }

    private BranchSideLimits getBranchSideLimits(LfBranch branch, LfBus bus,
                                                 TriFunction<LfBranch, LimitType, LimitScalingManager, List<LfBranch.LfLimitsGroup>> limitsGetter) {
        if (bus == null) {
            return BranchSideLimits.EMPTY;
        }
        return new BranchSideLimits(limitsGetter.apply(branch, LimitType.CURRENT, limitScalingManager),
                                    limitsGetter.apply(branch, LimitType.ACTIVE_POWER, limitScalingManager),
                                    limitsGetter.apply(branch, LimitType.APPARENT_POWER, limitScalingManager));
    }

    /**
     * Flows are given as {@link Evaluable} rather than values so that only the limited quantities are evaluated: most
     * branches only have current limits, and AC active/reactive power evaluation adds a small performance cost.
     */
    private void detectBranchSideViolations(LfBranch branch, LfBus bus, BranchSideLimits limits, Evaluable i, Evaluable p, Evaluable q, TwoSides side) {
        if (!limits.current().isEmpty()) {
            double iValue = i.eval();
            for (LfBranch.LfLimitsGroup limitsGroup : limits.current()) {
                detectBranchCurrentViolations(branch, bus, iValue, limitsGroup, side);
            }
        }
        if (!limits.activePower().isEmpty()) {
            double pValue = p.eval();
            for (LfBranch.LfLimitsGroup limitsGroup : limits.activePower()) {
                detectBranchActivePowerViolations(branch, pValue, limitsGroup, side);
            }
        }
        if (!limits.apparentPower().isEmpty()) {
            double s = Math.hypot(p.eval(), q.eval());
            for (LfBranch.LfLimitsGroup limitsGroup : limits.apparentPower()) {
                detectBranchApparentPowerViolations(branch, s, limitsGroup, side);
            }
        }
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param branch branch of interest
     * @param branchFlows branch flows of the current network state
     */
    private void detectBranchViolations(LfBranch branch, NetworkStateBranchFlows branchFlows) {
        BranchSideLimits limits1 = getBranchSideLimits(branch, branch.getBus1(), LfBranch::getLimits1);
        BranchSideLimits limits2 = getBranchSideLimits(branch, branch.getBus2(), LfBranch::getLimits2);
        if (limits1.isEmpty() && limits2.isEmpty()) {
            // limits are fetched before flows so that zero impedance flows, computed on the whole zero impedance
            // network at first request, are never computed for branches without limits
            return;
        }

        // detect violation limits on a branch
        // Only detect the most serious one (findFirst) : limit violations are ordered by severity
        if (branchFlows.isZeroImpedance(branch)) {
            // zero impedance branches have no flow evaluables: all their flows are computed per zero impedance network
            LfBranch.LfBranchResults flows = branchFlows.getFlows(branch);
            detectBranchSideViolations(branch, branch.getBus1(), limits1, flows::i1, flows::p1, flows::q1, TwoSides.ONE);
            detectBranchSideViolations(branch, branch.getBus2(), limits2, flows::i2, flows::p2, flows::q2, TwoSides.TWO);
        } else {
            // NetworkStateBranchFlows.getFlows would also work for impedant branches, but it eagerly evaluates the six
            // flows (p, q, i on both sides). Passing the branch evaluables instead defers evaluation to the quantities
            // that actually have limits, typically only the currents: see detectBranchSideViolations
            detectBranchSideViolations(branch, branch.getBus1(), limits1, branch.getI1(), branch.getP1(), branch.getQ1(), TwoSides.ONE);
            detectBranchSideViolations(branch, branch.getBus2(), limits2, branch.getI2(), branch.getP2(), branch.getQ2(), TwoSides.TWO);
        }
    }

    private static LimitViolation createLimitViolation(LfBranch branch, String operationalLimitsGroupId, LfBranch.LfLimit limit,
                                                       LimitViolationType type, double scale, double value,
                                                       TwoSides side) {
        return new LimitViolationBuilder()
                .subject(branch.getMainOriginalId())
                .operationalLimitsGroupId(operationalLimitsGroupId)
                .type(type)
                .limitName(limit.getName())
                .duration(limit.getAcceptableDuration())
                .limit(limit.getValue() * scale)
                .scaling(limit.getScaling())
                .value(value * scale)
                .side(branch.getOriginalSide().orElse(side.toThreeSides()))
                .build();
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param bus branch of interest
     */
    private void detectBusViolations(LfBus bus) {
        // detect violation limits on a bus
        double scale = bus.getNominalV();
        double busV = bus.getV();
        if (!Double.isNaN(bus.getHighVoltageLimit()) && busV > bus.getHighVoltageLimit()) {
            LimitViolation limitViolationHigh = new LimitViolationBuilder()
                    .subject(bus.getVoltageLevelId())
                    .type(LimitViolationType.HIGH_VOLTAGE)
                    .limit(bus.getHighVoltageLimit() * scale)
                    .value(busV * scale)
                    .violationLocation(bus.getViolationLocation())
                    .build();
            addBusLimitViolation(limitViolationHigh, bus);
        }
        if (!Double.isNaN(bus.getLowVoltageLimit()) && busV < bus.getLowVoltageLimit()) {
            LimitViolation limitViolationLow = new LimitViolationBuilder()
                    .subject(bus.getVoltageLevelId())
                    .type(LimitViolationType.LOW_VOLTAGE)
                    .limit(bus.getLowVoltageLimit() * scale)
                    .value(busV * scale)
                    .violationLocation(bus.getViolationLocation())
                    .build();
            addBusLimitViolation(limitViolationLow, bus);
        }
    }

    /**
     * Detect violation limits on one voltage angle limit and add them to the given list
     * @param limit voltage angle limit of interest
     */
    private void detectVoltageAngleLimitViolations(LfNetwork.LfVoltageAngleLimit limit) {
        double difference = limit.getTo().getAngle() - limit.getFrom().getAngle();
        if (!Double.isNaN(limit.getHighValue()) && difference > limit.getHighValue()) {
            LimitViolation limitViolationHigh = new LimitViolationBuilder()
                    .subject(limit.getId())
                    .type(LimitViolationType.HIGH_VOLTAGE_ANGLE)
                    .limit(Math.toDegrees(limit.getHighValue()))
                    .value(Math.toDegrees(difference))
                    .build();
            addVoltageAngleLimitViolation(limitViolationHigh, limit);
        }
        if (!Double.isNaN(limit.getLowValue()) && difference < limit.getLowValue()) {
            LimitViolation limitViolationLow = new LimitViolationBuilder()
                    .subject(limit.getId())
                    .type(LimitViolationType.LOW_VOLTAGE_ANGLE)
                    .limit(Math.toDegrees(limit.getLowValue()))
                    .value(Math.toDegrees(difference))
                    .build();
            addVoltageAngleLimitViolation(limitViolationLow, limit);
        }
    }

    /**
     * Compares two limit violations
     * @param violation1 first limit violation
     * @param violation2 second limit violation
     * @return true if violation2 is weaker than or equivalent to violation1, otherwise false
     */
    public static boolean violationWeakenedOrEquivalent(LimitViolation violation1, LimitViolation violation2,
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
                    return violation2.getValue() >= violation1.getValue() - Math.min(violationsParameters.getLowVoltageAbsoluteThreshold(),
                        violation1.getValue() * violationsParameters.getLowVoltageProportionalThreshold());
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isFlowViolation(LimitViolation limit) {
        return limit.getLimitType() == LimitViolationType.CURRENT || limit.getLimitType() == LimitViolationType.ACTIVE_POWER || limit.getLimitType() == LimitViolationType.APPARENT_POWER;
    }
}
