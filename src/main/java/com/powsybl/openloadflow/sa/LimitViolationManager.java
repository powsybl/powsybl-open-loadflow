/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.*;
import com.powsybl.security.limitreduction.LimitReduction;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Limit violation manager. A reference limit violation manager could be specified to only report violations that
 * are more severe than reference one.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LimitViolationManager {

    private final LimitViolationManager reference;

    private final LimitReductionManager limitReductionManager;

    private SecurityAnalysisParameters.IncreasedViolationsParameters parameters;

    private final Map<Object, LimitViolation> violations = new LinkedHashMap<>();

    public LimitViolationManager(LimitViolationManager reference, List<LimitReduction> limitReductions,
                                 SecurityAnalysisParameters.IncreasedViolationsParameters parameters) {
        this.reference = reference;
        if (reference != null) {
            this.parameters = Objects.requireNonNull(parameters);
        }
        this.limitReductionManager = LimitReductionManager.create(limitReductions);
    }

    public LimitViolationManager(List<LimitReduction> limitReductions) {
        this(null, limitReductions, null);
    }

    public List<LimitViolation> getLimitViolations() {
        return new ArrayList<>(violations.values());
    }

    /**
     * Detect violations on branches and on buses
     * @param network network on which the violation limits are checked
     */
    public void detectViolations(LfNetwork network) {
        Objects.requireNonNull(network);

        // Detect violation limits on branches
        network.getBranches().stream().filter(b -> !b.isDisabled()).forEach(this::detectBranchViolations);

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

    private void addLimitViolation(LimitViolation limitViolation, Object key) {
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
        addLimitViolation(limitViolation, getSubjectIdSide(limitViolation));
    }

    private void addBusLimitViolation(LimitViolation limitViolation, LfBus bus) {
        addLimitViolation(limitViolation, bus.getId());
    }

    private void addVoltageAngleLimitViolation(LimitViolation limitViolation, LfNetwork.LfVoltageAngleLimit voltageAngleLimit) {
        addLimitViolation(limitViolation, voltageAngleLimit.getId());
    }

    private void detectBranchSideViolations(LfBranch branch, LfBus bus,
                                            TriFunction<LfBranch, LimitType, LimitReductionManager, List<LfBranch.LfLimit>> limitsGetter,
                                            Function<LfBranch, Evaluable> iGetter,
                                            Function<LfBranch, Evaluable> pGetter,
                                            ToDoubleFunction<LfBranch> sGetter,
                                            TwoSides side) {
        List<LfBranch.LfLimit> limits = limitsGetter.apply(branch, LimitType.CURRENT, limitReductionManager);
        if (!limits.isEmpty()) {
            double i = iGetter.apply(branch).eval();
            limits.stream()
                    .filter(temporaryLimit -> i > temporaryLimit.getReducedValue())
                    .findFirst()
                    .map(temporaryLimit -> createLimitViolation(branch, temporaryLimit, LimitViolationType.CURRENT, PerUnit.ib(bus.getNominalV()), i, side))
                    .ifPresent(this::addBranchLimitViolation);
        }

        limits = limitsGetter.apply(branch, LimitType.ACTIVE_POWER, limitReductionManager);
        if (!limits.isEmpty()) {
            double p = pGetter.apply(branch).eval();
            limits.stream()
                    .filter(temporaryLimit -> Math.abs(p) > temporaryLimit.getReducedValue())
                    .findFirst()
                    .map(temporaryLimit -> createLimitViolation(branch, temporaryLimit, LimitViolationType.ACTIVE_POWER, PerUnit.SB, p, side))
                    .ifPresent(this::addBranchLimitViolation);
        }

        limits = limitsGetter.apply(branch, LimitType.APPARENT_POWER, limitReductionManager);
        if (!limits.isEmpty()) {
            //Apparent power is not relevant for fictitious branches and may be NaN
            double s = sGetter.applyAsDouble(branch);
            if (!Double.isNaN(s)) {
                limits.stream()
                        .filter(temporaryLimit -> s > temporaryLimit.getReducedValue())
                        .findFirst()
                        .map(temporaryLimit -> createLimitViolation(branch, temporaryLimit, LimitViolationType.APPARENT_POWER, PerUnit.SB, s, side))
                        .ifPresent(this::addBranchLimitViolation);
            }
        }
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param branch branch of interest
     */
    private void detectBranchViolations(LfBranch branch) {
        // detect violation limits on a branch
        // Only detect the most serious one (findFirst) : limit violations are ordered by severity
        if (branch.getBus1() != null) {
            detectBranchSideViolations(branch, branch.getBus1(), LfBranch::getLimits1, LfBranch::getI1, LfBranch::getP1, LfBranch::computeApparentPower1, TwoSides.ONE);
        }

        if (branch.getBus2() != null) {
            detectBranchSideViolations(branch, branch.getBus2(), LfBranch::getLimits2, LfBranch::getI2, LfBranch::getP2, LfBranch::computeApparentPower2, TwoSides.TWO);
        }
    }

    private static LimitViolation createLimitViolation(LfBranch branch, LfBranch.LfLimit temporaryLimit,
                                                       LimitViolationType type, double scale, double value,
                                                       TwoSides side) {
        return new LimitViolation(branch.getMainOriginalId(), null, type, temporaryLimit.getName(),
                temporaryLimit.getAcceptableDuration(), temporaryLimit.getValue() * scale,
                temporaryLimit.getReduction(), value * scale,
                branch.getOriginalSide().orElse(side.toThreeSides()));
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
            LimitViolation limitViolation1 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.HIGH_VOLTAGE, bus.getHighVoltageLimit() * scale,
                    (float) 1., busV * scale, createViolationLocation(bus));
            addBusLimitViolation(limitViolation1, bus);
        }
        if (!Double.isNaN(bus.getLowVoltageLimit()) && busV < bus.getLowVoltageLimit()) {
            LimitViolation limitViolation2 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.LOW_VOLTAGE, bus.getLowVoltageLimit() * scale,
                    (float) 1., busV * scale, createViolationLocation(bus));
            addBusLimitViolation(limitViolation2, bus);
        }
    }

    public static ViolationLocation createViolationLocation(LfBus bus) {
        List<Integer> nodes = bus.getNodes();
        if (nodes.isEmpty()) {
            return null;
        }

        return new NodeBreakerViolationLocation(bus.getVoltageLevelId(), nodes);
    }

    /**
     * Detect violation limits on one voltage angle limit and add them to the given list
     * @param limit voltage angle limit of interest
     */
    private void detectVoltageAngleLimitViolations(LfNetwork.LfVoltageAngleLimit limit) {
        double difference = limit.getTo().getAngle() - limit.getFrom().getAngle();
        if (!Double.isNaN(limit.getHighValue()) && difference > limit.getHighValue()) {
            LimitViolation limitViolation1 = new LimitViolation(limit.getId(), LimitViolationType.HIGH_VOLTAGE_ANGLE, Math.toDegrees(limit.getHighValue()),
                    1., Math.toDegrees(difference));
            addVoltageAngleLimitViolation(limitViolation1, limit);
        }
        if (!Double.isNaN(limit.getLowValue()) && difference < limit.getLowValue()) {
            LimitViolation limitViolation2 = new LimitViolation(limit.getId(), LimitViolationType.LOW_VOLTAGE_ANGLE, Math.toDegrees(limit.getLowValue()),
                    1., Math.toDegrees(difference));
            addVoltageAngleLimitViolation(limitViolation2, limit);
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
                    return violation2.getValue() >= violation1.getValue() - Math.min(violationsParameters.getLowVoltageAbsoluteThreshold(), violation1.getValue() * violationsParameters.getLowVoltageProportionalThreshold());
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
