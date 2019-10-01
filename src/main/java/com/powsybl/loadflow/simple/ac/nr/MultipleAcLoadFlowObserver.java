/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.nr;

import com.powsybl.loadflow.simple.equations.EquationSystem;
import com.powsybl.math.matrix.Matrix;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MultipleAcLoadFlowObserver implements AcLoadFlowObserver {

    private final List<AcLoadFlowObserver> observers;

    public MultipleAcLoadFlowObserver(List<AcLoadFlowObserver> observers) {
        this.observers = Objects.requireNonNull(observers);
    }

    @Override
    public void beforeEquationSystemCreation() {
        observers.forEach(AcLoadFlowObserver::beforeEquationSystemCreation);
    }

    @Override
    public void afterEquationSystemCreation() {
        observers.forEach(AcLoadFlowObserver::afterEquationSystemCreation);
    }

    @Override
    public void beforeOuterLoopBody(int outerLoopIteration, String outerLoopName) {
        observers.forEach(o -> o.beforeOuterLoopBody(outerLoopIteration, outerLoopName));
    }

    @Override
    public void beforeVoltageInitializerPreparation(Class<?> voltageInitializerClass) {
        observers.forEach(o -> o.beforeVoltageInitializerPreparation(voltageInitializerClass));
    }

    @Override
    public void afterVoltageInitializerPreparation() {
        observers.forEach(AcLoadFlowObserver::afterVoltageInitializerPreparation);
    }

    @Override
    public void stateVectorInitialized(double[] x) {
        observers.forEach(o -> o.stateVectorInitialized(x));
    }

    @Override
    public void beginIteration(int iteration) {
        observers.forEach(o -> o.beginIteration(iteration));
    }

    @Override
    public void norm(double norm) {
        observers.forEach(o -> o.norm(norm));
    }

    @Override
    public void beforeEquationsUpdate(int iteration) {
        observers.forEach(o -> o.beforeEquationsUpdate(iteration));
    }

    @Override
    public void afterEquationsUpdate(EquationSystem equationSystem, int iteration) {
        observers.forEach(o -> o.afterEquationsUpdate(equationSystem, iteration));
    }

    @Override
    public void beforeEquationVectorUpdate(int iteration) {
        observers.forEach(o -> o.beforeEquationVectorUpdate(iteration));
    }

    @Override
    public void afterEquationVectorUpdate(EquationSystem equationSystem, int iteration) {
        observers.forEach(o -> o.afterEquationVectorUpdate(equationSystem, iteration));
    }

    @Override
    public void beforeJacobianBuild(int iteration) {
        observers.forEach(o -> o.beforeJacobianBuild(iteration));
    }

    @Override
    public void afterJacobianBuild(Matrix j, EquationSystem equationSystem, int iteration) {
        observers.forEach(o -> o.afterJacobianBuild(j, equationSystem, iteration));
    }

    @Override
    public void beforeLuDecomposition(int iteration) {
        observers.forEach(o -> o.beforeLuDecomposition(iteration));
    }

    @Override
    public void afterLuDecomposition(int iteration) {
        observers.forEach(o -> o.afterLuDecomposition(iteration));
    }

    @Override
    public void beforeLuSolve(int iteration) {
        observers.forEach(o -> o.beforeLuSolve(iteration));
    }

    @Override
    public void afterLuSolve(int iteration) {
        observers.forEach(o -> o.afterLuSolve(iteration));
    }

    @Override
    public void endIteration(int iteration) {
        observers.forEach(o -> o.endIteration(iteration));
    }

    @Override
    public void beforeOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName) {
        observers.forEach(o -> o.beforeOuterLoopStatusCheck(outerLoopIteration, outerLoopName));
    }

    @Override
    public void afterOuterLoopStatusCheck(int outerLoopIteration, String outerLoopName, boolean cont) {
        observers.forEach(o -> o.afterOuterLoopStatusCheck(outerLoopIteration, outerLoopName, cont));
    }

    @Override
    public void afterOuterLoopBody(int outerLoopIteration, String outerLoopName) {
        observers.forEach(o -> o.afterOuterLoopBody(outerLoopIteration, outerLoopName));
    }

    @Override
    public void beforeNetworkUpdate() {
        observers.forEach(AcLoadFlowObserver::beforeNetworkUpdate);
    }

    @Override
    public void afterNetworkUpdate() {
        observers.forEach(AcLoadFlowObserver::afterNetworkUpdate);
    }

    @Override
    public void beforePvBusesReactivePowerUpdate() {
        observers.forEach(AcLoadFlowObserver::beforePvBusesReactivePowerUpdate);
    }

    @Override
    public void afterPvBusesReactivePowerUpdate() {
        observers.forEach(AcLoadFlowObserver::afterPvBusesReactivePowerUpdate);
    }
}
