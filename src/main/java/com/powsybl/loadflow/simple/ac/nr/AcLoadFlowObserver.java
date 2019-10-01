/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.nr;

import com.powsybl.loadflow.simple.equations.EquationSystem;
import com.powsybl.math.matrix.Matrix;

import java.util.Arrays;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface AcLoadFlowObserver {

    static AcLoadFlowObserver of(AcLoadFlowObserver... observers) {
        return of(Arrays.asList(observers));
    }

    static AcLoadFlowObserver of(List<AcLoadFlowObserver> observers) {
        return new MultipleAcLoadFlowObserver(observers);
    }

    void beforeEquationSystemCreation();

    void afterEquationSystemCreation();

    void beforeVoltageInitializerPreparation(Class<?> voltageInitializerClass);

    void afterVoltageInitializerPreparation();

    void stateVectorInitialized(double[] x);

    void beginIteration(int iteration);

    void norm(double norm);

    void beforeEquationsUpdate(int iteration);

    void afterEquationsUpdate(EquationSystem equationSystem, int iteration);

    void beforeEquationVectorUpdate(int iteration);

    void afterEquationVectorUpdate(EquationSystem equationSystem, int iteration);

    void beforeJacobianBuild(int iteration);

    void afterJacobianBuild(Matrix j, EquationSystem equationSystem, int iteration);

    void beforeLuDecomposition(int iteration);

    void afterLuDecomposition(int iteration);

    void beforeLuSolve(int iteration);

    void afterLuSolve(int iteration);

    void endIteration(int iteration);

    void beforeOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName);

    void afterOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName, boolean stable);

    void beforeOuterLoopBody(int outerLoopIteration, String outerLoopName);

    void afterOuterLoopBody(int outerLoopIteration, String outerLoopName);

    void beforePvBusesReactivePowerUpdate();

    void afterPvBusesReactivePowerUpdate();

    void beforeNetworkUpdate();

    void afterNetworkUpdate();
}
