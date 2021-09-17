/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.iidm.network.Country;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.SlackBusSelector;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowParameters {

    private final SlackBusSelector slackBusSelector;

    private VoltageInitializer voltageInitializer;

    private final NewtonRaphsonStoppingCriteria stoppingCriteria;

    private final List<OuterLoop> outerLoops;

    private final MatrixFactory matrixFactory;

    private final boolean voltageRemoteControl;

    private final boolean phaseControl;

    private final boolean transformerVoltageControlOn;

    private final boolean minImpedance;

    private final boolean twtSplitShuntAdmittance;

    private final boolean breakers;

    private double plausibleActivePowerLimit;

    private final boolean forceA1Var;

    private final boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds;

    private final Set<String> branchesWithCurrent;

    private final boolean computeMainConnectedComponentOnly;

    private final Set<Country> countriesToBalance;

    private final boolean distributedOnConformLoad;

    private final boolean voltagePerReactivePowerControl;

    public AcLoadFlowParameters(SlackBusSelector slackBusSelector, VoltageInitializer voltageInitializer,
                                NewtonRaphsonStoppingCriteria stoppingCriteria, List<OuterLoop> outerLoops,
                                MatrixFactory matrixFactory, boolean voltageRemoteControl,
                                boolean phaseControl, boolean transformerVoltageControlOn, boolean minImpedance,
                                boolean twtSplitShuntAdmittance, boolean breakers, double plausibleActivePowerLimit,
                                boolean forceA1Var, boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds,
                                Set<String> branchesWithCurrent, boolean computeMainConnectedComponentOnly,
                                Set<Country> countriesToBalance, boolean distributedOnConformLoad,
                                boolean voltagePerReactivePowerControl) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.voltageRemoteControl = voltageRemoteControl;
        this.phaseControl = phaseControl;
        this.transformerVoltageControlOn = transformerVoltageControlOn;
        this.minImpedance = minImpedance;
        this.twtSplitShuntAdmittance = twtSplitShuntAdmittance;
        this.breakers = breakers;
        this.plausibleActivePowerLimit = plausibleActivePowerLimit;
        this.forceA1Var = forceA1Var;
        this.addRatioToLinesWithDifferentNominalVoltageAtBothEnds = addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
        this.branchesWithCurrent = branchesWithCurrent;
        this.computeMainConnectedComponentOnly = computeMainConnectedComponentOnly;
        this.countriesToBalance = countriesToBalance;
        this.distributedOnConformLoad = distributedOnConformLoad;
        this.voltagePerReactivePowerControl = voltagePerReactivePowerControl;
    }

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public VoltageInitializer getVoltageInitializer() {
        return voltageInitializer;
    }

    public void setVoltageInitializer(VoltageInitializer voltageInitializer) {
        this.voltageInitializer = voltageInitializer;
    }

    public NewtonRaphsonStoppingCriteria getStoppingCriteria() {
        return stoppingCriteria;
    }

    public List<OuterLoop> getOuterLoops() {
        return outerLoops;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public boolean isVoltageRemoteControl() {
        return voltageRemoteControl;
    }

    public boolean isPhaseControl() {
        return phaseControl;
    }

    public boolean isTransformerVoltageControlOn() {
        return transformerVoltageControlOn;
    }

    public boolean isMinImpedance() {
        return minImpedance;
    }

    public boolean isTwtSplitShuntAdmittance() {
        return twtSplitShuntAdmittance;
    }

    public boolean isBreakers() {
        return breakers;
    }

    public double getPlausibleActivePowerLimit() {
        return plausibleActivePowerLimit;
    }

    public boolean isForceA1Var() {
        return forceA1Var;
    }

    public boolean isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds() {
        return addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
    }

    public Set<String> getBranchesWithCurrent() {
        return branchesWithCurrent;
    }

    public boolean isComputeMainConnectedComponentOnly() {
        return computeMainConnectedComponentOnly;
    }

    public Set<Country> getCountriesToBalance() {
        return Collections.unmodifiableSet(countriesToBalance);
    }

    public boolean isDistributedOnConformLoad() {
        return  distributedOnConformLoad;
    }

    public boolean isVoltagePerReactivePowerControl() {
        return voltagePerReactivePowerControl;
    }
}
