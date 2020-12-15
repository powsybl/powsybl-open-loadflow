/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfShuntImpl implements LfShunt {

    private final ShuntCompensator shuntCompensator;

    private int num = -1;

    private final double b;

    private Evaluable q = NAN;

    class Section {

        private double b;

        private double g;

        public Section(double b, double g) {
            this.b = b;
            this.g = g;
        }

        public double getB() {
            return b;
        }

        public void setG(double g) {
            this.g = g;
        }
    }

    private final List<Section> sections = new ArrayList<>();

    private int position = 1;

    public LfShuntImpl(ShuntCompensator shuntCompensator) {
        this.shuntCompensator = Objects.requireNonNull(shuntCompensator);
        double nominalV = shuntCompensator.getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        b = shuntCompensator.getB() * zb;
        if (shuntCompensator.isVoltageRegulatorOn()) {
            position = shuntCompensator.getSectionCount() - 1;
            ShuntCompensatorModel model = shuntCompensator.getModel();
            switch (shuntCompensator.getModelType()) {
                case LINEAR:
                    ShuntCompensatorLinearModel linearModel = (ShuntCompensatorLinearModel) model;
                    for (int section = 1; section < shuntCompensator.getMaximumSectionCount(); section++) {
                        sections.add(new Section(linearModel.getBPerSection() * section * zb,
                                linearModel.getGPerSection() * section * zb));
                    }
                    break;
                case NON_LINEAR:
                    ShuntCompensatorNonLinearModel nonLinearModel = (ShuntCompensatorNonLinearModel) model;
                    for (int section = 1; section < shuntCompensator.getMaximumSectionCount(); section++) {
                        sections.add(new Section(nonLinearModel.getAllSections().get(section - 1).getB() * zb,
                                nonLinearModel.getAllSections().get(section - 1).getG() * zb));
                    }
                    break;
            }
        }
    }

    @Override
    public String getId() {
        return shuntCompensator.getId();
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public double getB() {
        return b;
    }

    @Override
    public void setQ(Evaluable q) {
        this.q = Objects.requireNonNull(q);
    }

    @Override
    public void updateState() {
        shuntCompensator.getTerminal().setQ(q.eval() * PerUnit.SB);
    }
}
