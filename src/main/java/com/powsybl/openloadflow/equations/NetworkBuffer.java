/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.AbstractLfNetworkListener;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import net.jafama.FastMath;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class NetworkBuffer<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractLfNetworkListener implements EquationSystemListener<V, E> {

    protected final LfNetwork network;

    protected final EquationSystem<V, E> equationSystem;

    protected final VariableSet<V> variableSet;

    protected double[] b1;
    protected double[] b2;
    protected double[] g1;
    protected double[] g2;
    protected double[] y;
    protected double[] ksi;
    protected double[] sinKsi;
    protected double[] cosKsi;
    protected double[] a1;
    protected double[] r1;

    public NetworkBuffer(LfNetwork network, EquationSystem<V, E> equationSystem, VariableSet<V> variableSet) {
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.variableSet = Objects.requireNonNull(variableSet);
        this.network.addListener(this);
        this.equationSystem.addListener(this);
        init();
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public double b1(int num) {
        return b1[num];
    }

    public double b2(int num) {
        return b2[num];
    }

    public double g1(int num) {
        return g1[num];
    }

    public double g2(int num) {
        return g2[num];
    }

    public double y(int num) {
        return y[num];
    }

    public double ksi(int num) {
        return ksi[num];
    }

    public double sinKsi(int num) {
        return sinKsi[num];
    }

    public double cosKsi(int num) {
        return cosKsi[num];
    }

    public double a1(int num) {
        return a1[num];
    }

    public double r1(int num) {
        return r1[num];
    }

    private void init() {
        List<LfBranch> branches = network.getBranches();
        int branchCount = branches.size();
        b1 = new double[branchCount];
        b2 = new double[branchCount];
        g1 = new double[branchCount];
        g2 = new double[branchCount];
        y = new double[branchCount];
        ksi = new double[branchCount];
        sinKsi = new double[branchCount];
        cosKsi = new double[branchCount];
        a1 = new double[branchCount];
        r1 = new double[branchCount];
        for (int i = 0; i < branchCount; i++) {
            LfBranch branch = branches.get(i);
            PiModel piModel = branch.getPiModel();
//            if (piModel.getR() == 0 && piModel.getX() == 0) {
//                throw new IllegalArgumentException("Non impedant branch not supported: " + branch.getId());
//            }
            b1[i] = piModel.getB1();
            b2[i] = piModel.getB2();
            g1[i] = piModel.getG1();
            g2[i] = piModel.getG2();
            y[i] = 1 / piModel.getZ();
            ksi[i] = piModel.getKsi();
            cosKsi[i] = FastMath.cos(ksi[i]);
            sinKsi[i] = FastMath.sin(ksi[i]);
            a1[i] = piModel.getA1();
            r1[i] = piModel.getR1();
        }
    }

    @Override
    public void onPhaseControlTapPositionChange(PiModel piModel, int oldPosition, int newPosition) {
        List<LfBranch> branches = network.getBranches();
        a1 = new double[branches.size()];
        for (int i = 0; i < branches.size(); i++) {
            a1[i] = branches.get(i).getPiModel().getA1();
        }
    }

    @Override
    public void onVoltageControlTapPositionChange(PiModel piModel, int oldPosition, int newPosition) {
        List<LfBranch> branches = network.getBranches();
        r1 = new double[branches.size()];
        for (int i = 0; i < branches.size(); i++) {
            r1[i] = branches.get(i).getPiModel().getR1();
        }
    }

    @Override
    public void onEquationChange(Equation<V, E> equation, EquationEventType eventType) {
        // nothing to do
    }

    @Override
    public void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType) {
        // nothing to do
    }

    @Override
    public void onStateUpdate(double[] x) {
        // nothing to do
    }

    @Override
    public void onIndexUpdate() {

    }
}
