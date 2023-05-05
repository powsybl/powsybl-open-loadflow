/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.DenseMatrix;
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
        DenseMatrix mFinv = new DenseMatrix(6, 6);

        double t = 1. / 3.;
        //column 1
        mFinv.add(0, 0, t);
        mFinv.add(1, 1, t);

        mFinv.add(2, 0, t);
        mFinv.add(3, 1, t);

        mFinv.add(4, 0, t);
        mFinv.add(5, 1, t);

        //column 2
        mFinv.add(0, 2, t);
        mFinv.add(1, 3, t);

        mFinv.add(2, 2, -t / 2.);
        mFinv.add(2, 3, -t * Math.sqrt(3.) / 2.);
        mFinv.add(3, 2, t * Math.sqrt(3.) / 2.);
        mFinv.add(3, 3, -t / 2.);

        mFinv.add(4, 2, -t / 2.);
        mFinv.add(4, 3, t * Math.sqrt(3.) / 2.);
        mFinv.add(5, 2, -t * Math.sqrt(3.) / 2.);
        mFinv.add(5, 3, -t / 2.);

        //column 3
        mFinv.add(0, 4, t);
        mFinv.add(1, 5, t);

        mFinv.add(2, 4, -t / 2.);
        mFinv.add(2, 5, t * Math.sqrt(3.) / 2.);
        mFinv.add(3, 4, -t * Math.sqrt(3.) / 2.);
        mFinv.add(3, 5, -t / 2.);

        mFinv.add(4, 4, -t / 2.);
        mFinv.add(4, 5, -t * Math.sqrt(3.) / 2.);
        mFinv.add(5, 4, t * Math.sqrt(3.) / 2.);
        mFinv.add(5, 5, -t / 2.);

        return mFinv;
    }

    public static DenseMatrix createMatrix() {
        // [G1]   [ 1  1  1 ]   [Gh]
        // [G2] = [ 1  a²  a] * [Gd]
        // [G3]   [ 1  a  a²]   [Gi]

        DenseMatrix mFortescue = new DenseMatrix(6, 6);
        //column 1
        mFortescue.add(0, 0, 1.);
        mFortescue.add(1, 1, 1.);

        mFortescue.add(2, 0, 1.);
        mFortescue.add(3, 1, 1.);

        mFortescue.add(4, 0, 1.);
        mFortescue.add(5, 1, 1.);

        //column 2
        mFortescue.add(0, 2, 1.);
        mFortescue.add(1, 3, 1.);

        mFortescue.add(2, 2, -1. / 2.);
        mFortescue.add(2, 3, Math.sqrt(3.) / 2.);
        mFortescue.add(3, 2, -Math.sqrt(3.) / 2.);
        mFortescue.add(3, 3, -1. / 2.);

        mFortescue.add(4, 2, -1. / 2.);
        mFortescue.add(4, 3, -Math.sqrt(3.) / 2.);
        mFortescue.add(5, 2, Math.sqrt(3.) / 2.);
        mFortescue.add(5, 3, -1. / 2.);

        //column 3
        mFortescue.add(0, 4, 1.);
        mFortescue.add(1, 5, 1.);

        mFortescue.add(2, 4, -1. / 2.);
        mFortescue.add(2, 5, -Math.sqrt(3.) / 2.);
        mFortescue.add(3, 4, Math.sqrt(3.) / 2.);
        mFortescue.add(3, 5, -1. / 2.);

        mFortescue.add(4, 4, -1. / 2.);
        mFortescue.add(4, 5, Math.sqrt(3.) / 2.);
        mFortescue.add(5, 4, -Math.sqrt(3.) / 2.);
        mFortescue.add(5, 5, -1. / 2.);

        return mFortescue;
    }

    public static Vector2D getCartesianFromPolar(double magnitude, double angle) {
        double xValue = magnitude * Math.cos(angle);
        double yValue = magnitude * Math.sin(angle);
        return new Vector2D(xValue, yValue);
    }
}
