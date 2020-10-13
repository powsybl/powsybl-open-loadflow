/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.nr.AcLoadFlowObserver;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.SlackBusSelector;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowParameters {

    private final SlackBusSelector slackBusSelector;

    private VoltageInitializer voltageInitializer;

    private final NewtonRaphsonStoppingCriteria stoppingCriteria;

    private final List<OuterLoop> outerLoops;

    private final MatrixFactory matrixFactory;

    private final AcLoadFlowObserver observer;

    private final boolean voltageRemoteControl;

    private final boolean phaseControl;

    private final boolean minImpedance;

    private final boolean twtSplitShuntAdmittance;

    private final boolean breakers;

    public AcLoadFlowParameters(SlackBusSelector slackBusSelector, VoltageInitializer voltageInitializer,
                                NewtonRaphsonStoppingCriteria stoppingCriteria, List<OuterLoop> outerLoops,
                                MatrixFactory matrixFactory, AcLoadFlowObserver observer, boolean voltageRemoteControl,
                                boolean phaseControl, boolean minImpedance, boolean twtSplitShuntAdmittance,
                                boolean breakers) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.observer = Objects.requireNonNull(observer);
        this.voltageRemoteControl = voltageRemoteControl;
        this.phaseControl = phaseControl;
        this.minImpedance = minImpedance;
        this.twtSplitShuntAdmittance = twtSplitShuntAdmittance;
        this.breakers = breakers;
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

    public AcLoadFlowObserver getObserver() {
        return observer;
    }

    public boolean isVoltageRemoteControl() {
        return voltageRemoteControl;
    }

    public boolean isPhaseControl() {
        return phaseControl;
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
}
