/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.util.LimitViolationUtils;
import com.powsybl.openloadflow.sa.LimitReductionManager;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BranchResult;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface LfBranch extends LfElement {

    enum BranchType {
        LINE,
        TRANSFO_2,
        TRANSFO_3_LEG_1,
        TRANSFO_3_LEG_2,
        TRANSFO_3_LEG_3,
        DANGLING_LINE,
        SWITCH,
        TIE_LINE
    }

    record LfBranchResults(double p1, double p2, double q1, double q2, double i1, double i2) {
    }

    class LfLimitsGroup {

        private List<LfLimit> sortedLimits;

        public LfLimitsGroup(List<LfLimit> sortedLimits) {
            this.sortedLimits = sortedLimits;
        }

        public List<LfLimit> getSortedLimits() {
            return sortedLimits;
        }

        private static double getScaleForLimitType(LimitType type, LfBus bus) {
            return switch (type) {
                case ACTIVE_POWER, APPARENT_POWER -> 1.0 / PerUnit.SB;
                case CURRENT -> 1.0 / PerUnit.ib(bus.getNominalV());
                default ->
                        throw new UnsupportedOperationException(String.format("Getting scale for limit type %s is not supported.", type));
            };
        }

        /**
         * Create the list of LfLimits from a LoadingLimits and a list of reductions.
         * The resulting list will contain the permanent limit
         * This list is returned in a LfLimitsGroup object
         */
        public static LfLimitsGroup createSortedLimitsList(LoadingLimits loadingLimits, LfBus bus, double[] limitReductions) {
            List<LfLimit> sortedLimits = new ArrayList<>(3);
            if (loadingLimits != null) {
                double toPerUnit = getScaleForLimitType(loadingLimits.getLimitType(), bus);

                int i = 0;
                for (LoadingLimits.TemporaryLimit temporaryLimit : loadingLimits.getTemporaryLimits()) {
                    if (temporaryLimit.getAcceptableDuration() != 0) {
                        // it is not useful to add a limit with acceptable duration equal to zero as the only value plausible
                        // for this limit is infinity.
                        // https://javadoc.io/doc/com.powsybl/powsybl-core/latest/com/powsybl/iidm/network/CurrentLimits.html
                        double reduction = limitReductions.length == 0 ? 1d : limitReductions[i + 1]; // Temporary limit's reductions are stored starting from index 1 in `limitReductions`
                        double originalValuePerUnit = temporaryLimit.getValue() * toPerUnit;
                        sortedLimits.add(0, LfLimit.createTemporaryLimit(temporaryLimit.getName(), temporaryLimit.getAcceptableDuration(),
                                originalValuePerUnit, reduction));
                    }
                    i++;
                }
                double reduction = limitReductions.length == 0 ? 1d : limitReductions[0];
                sortedLimits.add(LfLimit.createPermanentLimit(loadingLimits.getPermanentLimit() * toPerUnit, reduction));
            }
            if (sortedLimits.size() > 1) {
                // we only make that fix if there is more than a permanent limit attached to the branch.
                for (int i = sortedLimits.size() - 1; i > 0; i--) {
                    // From the permanent limit to the most serious temporary limit.
                    sortedLimits.get(i).setAcceptableDuration(sortedLimits.get(i - 1).getAcceptableDuration());
                }
                sortedLimits.get(0).setAcceptableDuration(0);
            }
            return new LfLimitsGroup(sortedLimits);
        }

    }

    class LfLimit {

        private final String name;

        private int acceptableDuration;

        private final double value;

        private final double reduction;

        public LfLimit(String name, int acceptableDuration, double value, double reduction) {
            this.name = name;
            this.acceptableDuration = acceptableDuration;
            this.value = value;
            this.reduction = reduction;
        }

        public static LfLimit createTemporaryLimit(String name, int acceptableDuration, double originalValuePerUnit, Double reduction) {
            return new LfLimit(name, acceptableDuration, originalValuePerUnit, reduction);
        }

        public static LfLimit createPermanentLimit(double originalValuePerUnit, Double reduction) {
            return new LfLimit(LimitViolationUtils.PERMANENT_LIMIT_NAME, Integer.MAX_VALUE, originalValuePerUnit, reduction);
        }

        public String getName() {
            return name;
        }

        public int getAcceptableDuration() {
            return acceptableDuration;
        }

        public double getValue() {
            return value;
        }

        public double getReducedValue() {
            return value * reduction;
        }

        public void setAcceptableDuration(int acceptableDuration) {
            this.acceptableDuration = acceptableDuration;
        }

        public double getReduction() {
            return reduction;
        }
    }

    static int[] createIndex(LfNetwork network, List<LfBranch> branches) {
        int[] branchIndex = new int[network.getBranches().size()];
        for (int i = 0; i < branches.size(); i++) {
            LfBranch branch = branches.get(i);
            branchIndex[branch.getNum()] = i;
        }
        return branchIndex;
    }

    Optional<ThreeSides> getOriginalSide();

    BranchType getBranchType();

    LfBus getBus1();

    LfBus getBus2();

    void setP1(Evaluable p1);

    Evaluable getP1();

    void setP2(Evaluable p2);

    Evaluable getP2();

    Evaluable getQ1();

    void setQ1(Evaluable q1);

    Evaluable getQ2();

    void setQ2(Evaluable q2);

    PiModel getPiModel();

    void setI1(Evaluable i1);

    void setI2(Evaluable i2);

    Evaluable getI1();

    Evaluable getI2();

    Evaluable getOpenP1();

    void setOpenP1(Evaluable openP1);

    Evaluable getOpenQ1();

    void setOpenQ1(Evaluable openQ1);

    Evaluable getOpenI1();

    void setOpenI1(Evaluable openI1);

    Evaluable getOpenP2();

    void setOpenP2(Evaluable openP2);

    Evaluable getOpenQ2();

    void setOpenQ2(Evaluable openQ2);

    Evaluable getOpenI2();

    void setOpenI2(Evaluable openI2);

    Evaluable getClosedP1();

    void setClosedP1(Evaluable closedP1);

    Evaluable getClosedQ1();

    void setClosedQ1(Evaluable closedQ1);

    Evaluable getClosedI1();

    void setClosedI1(Evaluable closedI1);

    Evaluable getClosedP2();

    void setClosedP2(Evaluable closedP2);

    Evaluable getClosedQ2();

    void setClosedQ2(Evaluable closedQ2);

    Evaluable getClosedI2();

    void setClosedI2(Evaluable closedI2);

    void addAdditionalOpenP1(Evaluable openP1);

    List<Evaluable> getAdditionalOpenP1();

    void addAdditionalClosedP1(Evaluable closedP1);

    List<Evaluable> getAdditionalClosedP1();

    void addAdditionalOpenQ1(Evaluable openQ1);

    List<Evaluable> getAdditionalOpenQ1();

    void addAdditionalClosedQ1(Evaluable closedQ1);

    List<Evaluable> getAdditionalClosedQ1();

    void addAdditionalOpenP2(Evaluable openP2);

    List<Evaluable> getAdditionalOpenP2();

    void addAdditionalClosedP2(Evaluable closedP2);

    List<Evaluable> getAdditionalClosedP2();

    void addAdditionalOpenQ2(Evaluable openQ2);

    List<Evaluable> getAdditionalOpenQ2();

    void addAdditionalClosedQ2(Evaluable closedQ2);

    List<Evaluable> getAdditionalClosedQ2();

    List<LfLimitsGroup> getLimits1(LimitType type, LimitReductionManager limitReductionManager);

    default List<LfLimitsGroup> getLimits2(LimitType type, LimitReductionManager limitReductionManager) {
        return Collections.emptyList();
    }

    double[] getLimitReductions(TwoSides side, LimitReductionManager limitReductionManager, LoadingLimits limits);

    void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport);

    void updateFlows(double p1, double q1, double p2, double q2);

    // transformer phase control

    boolean hasPhaseControllerCapability();

    Optional<TransformerPhaseControl> getPhaseControl();

    void setPhaseControl(TransformerPhaseControl phaseControl);

    boolean isPhaseController();

    boolean isPhaseControlled();

    boolean isPhaseControlEnabled();

    void setPhaseControlEnabled(boolean phaseControlEnabled);

    // transformer voltage control

    Optional<TransformerVoltageControl> getVoltageControl();

    boolean isVoltageControlEnabled();

    void setVoltageControlEnabled(boolean voltageControlEnabled);

    boolean isVoltageController();

    void setVoltageControl(TransformerVoltageControl transformerVoltageControl);

    // transformer reactive power control

    Optional<TransformerReactivePowerControl> getTransformerReactivePowerControl();

    void setTransformerReactivePowerControl(TransformerReactivePowerControl transformerReactivePowerControl);

    boolean isTransformerReactivePowerController();

    boolean isTransformerReactivePowerControlled();

    List<BranchResult> createBranchResult(double preContingencyBranchP1, double preContingencyBranchOfContingencyP1,
                                          boolean createExtension, Map<String, LfBranchResults> zeroImpedanceFlows,
                                          LoadFlowModel loadFlowModel);

    double computeApparentPower1();

    double computeApparentPower2();

    boolean isZeroImpedance(LoadFlowModel loadFlowModel);

    void setSpanningTreeEdge(LoadFlowModel loadFlowModel, boolean spanningTreeEdge);

    boolean isSpanningTreeEdge(LoadFlowModel loadFlowModel);

    Evaluable getA1();

    void setA1(Evaluable a1);

    static double getA(LfBranch branch) {
        Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        return PiModel.A2 - piModel.getA1();
    }

    static double getDiscretePhaseControlTarget(LfBranch branch, TransformerPhaseControl.Unit unit) {
        Objects.requireNonNull(branch);
        Objects.requireNonNull(unit);
        Optional<TransformerPhaseControl> phaseControl = branch.getPhaseControl().filter(dpc -> branch.isPhaseControlled());
        if (phaseControl.isEmpty()) {
            throw new PowsyblException("Branch '" + branch.getId() + "' is not phase-controlled");
        }
        if (phaseControl.get().getUnit() != unit) {
            throw new PowsyblException("Branch '" + branch.getId() + "' has not a target in " + unit);
        }
        return phaseControl.get().getTargetValue();
    }

    Optional<GeneratorReactivePowerControl> getGeneratorReactivePowerControl();

    void setGeneratorReactivePowerControl(GeneratorReactivePowerControl generatorReactivePowerControl);

    boolean isConnectedAtBothSides();

    boolean isConnectedSide1();

    void setConnectedSide1(boolean connectedSide1);

    boolean isConnectedSide2();

    void setConnectedSide2(boolean connectedSide2);

    boolean isDisconnectionAllowedSide1();

    void setDisconnectionAllowedSide1(boolean disconnectionAllowedSide1);

    void setDisconnectionAllowedSide2(boolean disconnectionAllowedSide2);

    boolean isDisconnectionAllowedSide2();

    void setMinZ(double lowImpedanceThreshold);

    LfAsymLine getAsymLine();

    void setAsymLine(LfAsymLine asymLine);

    boolean isAsymmetric();
}
