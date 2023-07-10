/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.DenseMatrix;
import net.jafama.FastMath;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class Fortescue {

    public enum SequenceType {
        POSITIVE(1),
        NEGATIVE(2),
        ZERO(0);

        private final int num;

        SequenceType(int num) {
            this.num = num;
        }

        public int getNum() {
            return num;
        }
    }

    private Fortescue() {
    }

    public static DenseMatrix createInverseMatrix() {
        return createComplexMatrix(true).getRealCartesianMatrix();
    }

    public static DenseMatrix createMatrix() {
        // [G1]   [ 1  1  1 ]   [Gh]
        // [G2] = [ 1  a²  a] * [Gd]
        // [G3]   [ 1  a  a²]   [Gi]

        return createComplexMatrix(false).getRealCartesianMatrix();
    }

    public static ComplexMatrix createComplexMatrix(boolean isInverse) {
        // [G1]   [ 1  1  1 ]   [Gh]
        // [G2] = [ 1  a²  a] * [Gd]
        // [G3]   [ 1  a  a²]   [Gi]

        Complex a = new Complex(-0.5, FastMath.sqrt(3.) / 2);
        Complex a2 = a.multiply(a);

        double t = 1.;
        Complex c1 = a;
        Complex c2 = a2;
        if (isInverse) {
            t = 1. / 3.;
            c1 = a2.multiply(t);
            c2 = a.multiply(t);
        }
        Complex unit = new Complex(t, 0);

        ComplexMatrix complexMatrix = new ComplexMatrix(3, 3);
        complexMatrix.set(1, 1, unit);
        complexMatrix.set(1, 2, unit);
        complexMatrix.set(1, 3, unit);

        complexMatrix.set(2, 1, unit);
        complexMatrix.set(2, 2, c2);
        complexMatrix.set(2, 3, c1);

        complexMatrix.set(3, 1, unit);
        complexMatrix.set(3, 2, c1);
        complexMatrix.set(3, 3, c2);

        return complexMatrix;
    }

    public static Vector2D getCartesianFromPolar(double magnitude, double angle) {
        double xValue = magnitude * Math.cos(angle);
        double yValue = magnitude * Math.sin(angle);
        return new Vector2D(xValue, yValue);
    }
}
