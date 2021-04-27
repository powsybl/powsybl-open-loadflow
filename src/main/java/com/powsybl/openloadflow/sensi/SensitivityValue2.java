/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityValue2 {

    private final Object factorContext;

    private final String contingencyId;

    private final double value;

    private final double functionReference;

    public SensitivityValue2(Object factorContext, String contingencyId, double value, double functionReference) {
        this.factorContext = factorContext;
        this.contingencyId = contingencyId;
        this.value = value;
        this.functionReference = functionReference;
    }

    public Object getFactorContext() {
        return factorContext;
    }

    public String getContingencyId() {
        return contingencyId;
    }

    public double getValue() {
        return value;
    }

    public double getFunctionReference() {
        return functionReference;
    }

    @Override
    public String toString() {
        return "SensitivityValue(" +
                "factorContext=" + factorContext +
                ", contingencyId='" + contingencyId + '\'' +
                ", value=" + value +
                ", functionReference=" + functionReference +
                ')';
    }
}
