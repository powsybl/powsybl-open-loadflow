/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.SecurityAnalysisParameters;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Limit violation manager. A reference limit violation manager could be specified to only report violations that
 * are more severe than reference one.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LimitViolationManager {

    private final LimitViolationManager reference;

    private SecurityAnalysisParameters.IncreasedViolationsParameters parameters;

    private final Map<Pair<String, Branch.Side>, LimitViolation> violations = new LinkedHashMap<>();

    public LimitViolationManager(LimitViolationManager reference, SecurityAnalysisParameters.IncreasedViolationsParameters parameters) {
        this.reference = reference;
        if (reference != null) {
            this.parameters = Objects.requireNonNull(parameters);
        }
    }

    public LimitViolationManager() {
        this(null, null);
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
    }

    private static Pair<String, Branch.Side> getSubjectIdSide(LimitViolation limitViolation) {
        return Pair.of(limitViolation.getSubjectId(), limitViolation.getSide());
    }

    private void addLimitViolation(LimitViolation limitViolation) {
        var subjectIdSide = getSubjectIdSide(limitViolation);
        if (reference != null) {
            var referenceLimitViolation = reference.violations.get(subjectIdSide);
            if (referenceLimitViolation == null || !violationWeakenedOrEquivalent(referenceLimitViolation, limitViolation, parameters)) {
                violations.put(subjectIdSide, limitViolation);
            }
        } else {
            violations.put(subjectIdSide, limitViolation);
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
            branch.getLimits1(LimitType.CURRENT).stream()
                    .filter(temporaryLimit1 -> branch.getI1().eval() > temporaryLimit1.getValue())
                    .findFirst()
                    .map(temporaryLimit1 -> createLimitViolation1(branch, temporaryLimit1, LimitViolationType.CURRENT, PerUnit.ib(branch.getBus1().getNominalV()), branch.getI1().eval()))
                    .ifPresent(this::addLimitViolation);

            branch.getLimits1(LimitType.ACTIVE_POWER).stream()
                    .filter(temporaryLimit1 -> branch.getP1().eval() > temporaryLimit1.getValue())
                    .findFirst()
                    .map(temporaryLimit1 -> createLimitViolation1(branch, temporaryLimit1, LimitViolationType.ACTIVE_POWER, PerUnit.SB, branch.getP1().eval()))
                    .ifPresent(this::addLimitViolation);

            //Apparent power is not relevant for fictitious branches and may be NaN
            double apparentPower1 = branch.computeApparentPower1();
            if (!Double.isNaN(apparentPower1)) {
                branch.getLimits1(LimitType.APPARENT_POWER).stream()
                        .filter(temporaryLimit1 -> apparentPower1 > temporaryLimit1.getValue())
                        .findFirst()
                        .map(temporaryLimit1 -> createLimitViolation1(branch, temporaryLimit1, LimitViolationType.APPARENT_POWER, PerUnit.SB, apparentPower1))
                        .ifPresent(this::addLimitViolation);
            }

        }
        if (branch.getBus2() != null) {
            branch.getLimits2(LimitType.CURRENT).stream()
                    .filter(temporaryLimit2 -> branch.getI2().eval() > temporaryLimit2.getValue())
                    .findFirst() // only the most serious violation is added (the limits are sorted in descending gravity)
                    .map(temporaryLimit2 -> createLimitViolation2(branch, temporaryLimit2, LimitViolationType.CURRENT, PerUnit.ib(branch.getBus2().getNominalV()), branch.getI2().eval()))
                    .ifPresent(this::addLimitViolation);

            branch.getLimits2(LimitType.ACTIVE_POWER).stream()
                    .filter(temporaryLimit2 -> branch.getP2().eval() > temporaryLimit2.getValue())
                    .findFirst()
                    .map(temporaryLimit2 -> createLimitViolation2(branch, temporaryLimit2, LimitViolationType.ACTIVE_POWER, PerUnit.SB, branch.getP2().eval()))
                    .ifPresent(this::addLimitViolation);

            //Apparent power is not relevant for fictitious branches and may be NaN
            double apparentPower2 = branch.computeApparentPower2();
            if (!Double.isNaN(apparentPower2)) {
                branch.getLimits2(LimitType.APPARENT_POWER).stream()
                        .filter(temporaryLimit2 -> apparentPower2 > temporaryLimit2.getValue())
                        .findFirst()
                        .map(temporaryLimit2 -> createLimitViolation2(branch, temporaryLimit2, LimitViolationType.APPARENT_POWER, PerUnit.SB, apparentPower2))
                        .ifPresent(this::addLimitViolation);
            }
        }
    }

    private static LimitViolation createLimitViolation1(LfBranch branch, LfBranch.LfLimit temporaryLimit1,
                                                        LimitViolationType type, double scale, double value) {
        return new LimitViolation(branch.getId(), type, null,
                temporaryLimit1.getAcceptableDuration(), temporaryLimit1.getValue() * scale,
                (float) 1., value * scale, Branch.Side.ONE);
    }

    private static LimitViolation createLimitViolation2(LfBranch branch, LfBranch.LfLimit temporaryLimit2,
                                                        LimitViolationType type, double scale, double value) {
        return new LimitViolation(branch.getId(), type, null,
                temporaryLimit2.getAcceptableDuration(), temporaryLimit2.getValue() * scale,
                (float) 1., value * scale, Branch.Side.TWO);
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
                    (float) 1., busV * scale);
            addLimitViolation(limitViolation1);
        }
        if (!Double.isNaN(bus.getLowVoltageLimit()) && busV < bus.getLowVoltageLimit()) {
            LimitViolation limitViolation2 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.LOW_VOLTAGE, bus.getLowVoltageLimit() * scale,
                    (float) 1., busV * scale);
            addLimitViolation(limitViolation2);
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
