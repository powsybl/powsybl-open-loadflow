/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;

import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class TheveninEquivalentParameters {

    public enum TheveninVoltageProfileType {
        CALCULATED, // use the computed values at nodes to compute Zth and Eth
        NOMINAL // use the nominal voltage values at nodes to get Zth and Eth
    }

    public enum TheveninPeriodType {
        THEVENIN_SUB_TRANSIENT, //uses subTransient parameters x"d
        THEVENIN_TRANSIENT,     //uses transient parameters x'd
        THEVENIN_STEADY_STATE
    }

    public static final double XSUBTRANSIENT = 0.2; //default value if data not available

    private final boolean voltageUpdate;

    private final AcLoadFlowParameters acLoadFlowParameters;

    private final MatrixFactory matrixFactory;

    private final List<CalculationLocation> theveninCalculationLocation;

    private final boolean theveninIgnoreShunts;

    private final TheveninVoltageProfileType theveninVoltageProfileType;

    private final TheveninPeriodType theveninPeriodType;

    public TheveninEquivalentParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory, List<CalculationLocation> voltageLevels,
            boolean voltageUpdate, TheveninVoltageProfileType theveninVoltageProfileType, TheveninPeriodType theveninPeriodType, boolean theveninIgnoreShunts) {
        this.acLoadFlowParameters = Objects.requireNonNull(acLoadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.theveninCalculationLocation = Objects.requireNonNull(voltageLevels);
        this.voltageUpdate = voltageUpdate;
        this.theveninIgnoreShunts = theveninIgnoreShunts;
        this.theveninVoltageProfileType = theveninVoltageProfileType;
        this.theveninPeriodType = theveninPeriodType;
    }

    public AcLoadFlowParameters getAcLoadFlowParameters() {
        return acLoadFlowParameters;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public List<CalculationLocation> getLocations() {
        return theveninCalculationLocation;
    }

    public boolean isVoltageUpdate() {
        return voltageUpdate;
    }

    public boolean isTheveninIgnoreShunts() {
        return theveninIgnoreShunts;
    }

    public TheveninVoltageProfileType getTheveninVoltageProfileType() {
        return theveninVoltageProfileType;
    }

    public TheveninPeriodType getTheveninPeriodType() {
        return theveninPeriodType;
    }

}
