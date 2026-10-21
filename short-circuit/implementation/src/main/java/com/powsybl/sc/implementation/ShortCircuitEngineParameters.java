/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;

import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitEngineParameters {
    public enum VoltageProfileType {
        CALCULATED, // use the computed values at nodes to compute Zth and Eth
        NOMINAL // use the nominal voltage values at nodes to get Zth and Eth
    }

    public enum PeriodType {
        SUB_TRANSIENT, //uses subTransient parameters x"d
        TRANSIENT,     //uses transient parameters x'd
        STEADY_STATE
    }

    public enum AnalysisType {
        SELECTIVE, // short circuit analysis for List<ShortCircuitFault> faults in input
        SYSTEMATIC // short circuit analysis for all busses of input grid
    }

    private final LoadFlowParameters loadFlowParameters;

    private List<ShortCircuitFault> shortCircuitFaults;

    private final MatrixFactory matrixFactory;

    private final VoltageProfileType vProfile;

    private final boolean ignoreShunts;

    private final AnalysisType analysisType;

    private boolean voltageUpdate;

    private PeriodType periodType;

    private ShortCircuitNorm norm;

    public ShortCircuitEngineParameters(LoadFlowParameters loadFlowParameters, MatrixFactory matrixFactory, AnalysisType analysisType,
            List<ShortCircuitFault> faults, boolean isVoltageExport, VoltageProfileType vProfile, boolean ignoreShunts, PeriodType periodType, ShortCircuitNorm norm) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.shortCircuitFaults = Objects.requireNonNull(faults);
        this.voltageUpdate = isVoltageExport;
        this.ignoreShunts = ignoreShunts;
        this.vProfile = vProfile;
        this.analysisType = analysisType;
        this.periodType = periodType;
        this.norm = norm;
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return loadFlowParameters;
    }

    public List<ShortCircuitFault> getShortCircuitFaults() {
        return shortCircuitFaults;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public VoltageProfileType getVoltageProfileType() {
        return vProfile;
    }

    public boolean isIgnoreShunts() {
        return ignoreShunts;
    }

    public AnalysisType getAnalysisType() {
        return analysisType;
    }

    public void setShortCircuitFaults(List<ShortCircuitFault> faults) {
        shortCircuitFaults = faults;
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public ShortCircuitNorm getNorm() {
        return norm;
    }

    public boolean isVoltageUpdate() {
        return voltageUpdate;
    }

    public void setVoltageUpdate(boolean bool) {
        voltageUpdate = bool;
    }
}
