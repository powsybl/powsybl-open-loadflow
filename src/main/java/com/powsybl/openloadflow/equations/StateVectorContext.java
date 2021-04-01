/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import net.jafama.DoubleWrapper;
import net.jafama.FastMath;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class StateVectorContext {

    private final double[] cosX;

    private final double[] sinX;

    public StateVectorContext(Collection<Variable> variables, double[] x) {
        Objects.requireNonNull(variables);
        Objects.requireNonNull(x);
        cosX = new double[variables.size()];
        sinX = new double[variables.size()];
        Arrays.fill(cosX, Double.NaN);
        Arrays.fill(sinX, Double.NaN);
        DoubleWrapper wrapper = new DoubleWrapper();
        for (Variable variable : variables) {
            int row = variable.getRow();
            sinX[row] = FastMath.sinAndCos(x[row], wrapper);
            cosX[row] = wrapper.value;
        }
    }

    public double cos(int row) {
        return cosX[row];
    }

    public double sin(int row) {
        return sinX[row];
    }

    /**
     * cos(ph1 - ph2 + c)
     */
    public double cosPh1MinusPh2PlusC(int row1, int row2, double c) {
        double cos1 = cosX[row1];
        double sin1 = sinX[row1];
        double cos2 = cosX[row2];
        double sin2 = -sinX[row2];
        double cosC = FastMath.cos(c);
        double sinC = FastMath.sin(c);
        return cos1 * cos2 * cosC - cos1 * sin2 * sinC - sin1 * cos2 * sinC - sin1 * sin2 * cosC;
    }

    /**
     * sin(ph1 - ph2 + c)
     */
    public double sinPh1MinusPh2PlusC(int row1, int row2, double c) {
        double cos1 = cosX[row1];
        double sin1 = sinX[row1];
        double cos2 = cosX[row2];
        double sin2 = -sinX[row2];
        double cosC = FastMath.cos(c);
        double sinC = FastMath.sin(c);
        return sin1 * cos2 * cosC + cos1 * sin2 * cosC + cos1 * cos2 * sinC - sin1 * sin2 * sinC;
    }

    /**
     * cos(ph + c)
     */
    public double cosPhPlusC(int row, double c) {
        double cos = cosX[row];
        double sin = sinX[row];
        double cosC = FastMath.cos(c);
        double sinC = FastMath.sin(c);
        return cos * cosC - sin * sinC;
    }

    /**
     * sin(ph + c)
     */
    public double sinPhPlusC(int row, double c) {
        double cos = cosX[row];
        double sin = sinX[row];
        double cosC = FastMath.cos(c);
        double sinC = FastMath.sin(c);
        return sin * cosC + sinC * cos;
    }
}
