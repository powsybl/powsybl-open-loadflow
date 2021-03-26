/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.openloadflow.network.*;
import com.powsybl.iidm.network.ShuntCompensatorLinearModel;
import com.powsybl.iidm.network.ShuntCompensatorModel;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModel;
import com.powsybl.openloadflow.network.AbstractElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.util.Evaluable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfShuntImpl extends AbstractElement implements LfShunt {

    protected static final Logger LOGGER = LoggerFactory.getLogger(LfShuntImpl.class);

    private final ShuntCompensator shuntCompensator;

    private double b;

    private Evaluable q = NAN;

    private boolean hasVoltageControl = false;

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

    private int position = 0;

    public LfShuntImpl(ShuntCompensator shuntCompensator, LfNetwork network) {
        super(network);
        this.shuntCompensator = Objects.requireNonNull(shuntCompensator);
        double nominalV = shuntCompensator.getTerminal().getVoltageLevel().getNominalV();
        double zb = nominalV * nominalV / PerUnit.SB;
        hasVoltageControl = shuntCompensator.isVoltageRegulatorOn();

        sections.add(new Section(0, 0)); // position 0 means disconnected.
        position = shuntCompensator.getSectionCount();

        ShuntCompensatorModel model = shuntCompensator.getModel();
        switch (shuntCompensator.getModelType()) {
            case LINEAR:
                ShuntCompensatorLinearModel linearModel = (ShuntCompensatorLinearModel) model;
                for (int section = 1; section <= shuntCompensator.getMaximumSectionCount(); section++) {
                    sections.add(new Section(linearModel.getBPerSection() * section * zb,
                            linearModel.getGPerSection() * section * zb));
                }
                break;
            case NON_LINEAR:
                ShuntCompensatorNonLinearModel nonLinearModel = (ShuntCompensatorNonLinearModel) model;
                for (int section = 0; section < shuntCompensator.getMaximumSectionCount(); section++) {
                    sections.add(new Section(nonLinearModel.getAllSections().get(section).getB() * zb,
                            nonLinearModel.getAllSections().get(section).getG() * zb));
                }
                break;
        }
        b = getSection().getB();
    }

    @Override
    public ElementType getType() {
        return ElementType.SHUNT_COMPENSATOR;
    }

    @Override
    public String getId() {
        return shuntCompensator.getId();
    }

    @Override
    public double getB() {
        return getSection().getB();
    }

    @Override
    public void setB(double b) {
        double previousB = this.b;
        this.b = b;
        roundBToClosestSection();
        LOGGER.info("Round B shift of shunt '{}': {} -> {}", shuntCompensator.getId(), previousB, this.b);
    }

    @Override
    public void setQ(Evaluable q) {
        this.q = Objects.requireNonNull(q);
    }

    @Override
    public Evaluable getQ() {
        return q;
    }

    private void roundBToClosestSection() {
        // find tap position with the closest b value
        double smallestDistance = Math.abs(b - getSection().getB());
        for (int s = 0; s < sections.size(); s++) {
            double distance = Math.abs(b - sections.get(s).getB());
            if (distance < smallestDistance) {
                position = s;
                smallestDistance = distance;
            }
        }
        b = getSection().getB();
    }

    private Section getSection() {
        return sections.get(position);
    }

    @Override
    public boolean hasVoltageControl() {
        return hasVoltageControl;
    }

    @Override
    public void setVoltageControl(boolean hasVoltageControl) {
        this.hasVoltageControl = hasVoltageControl;
    }

    @Override
    public double getMinB() {
        return Math.min(sections.get(0).getB(), sections.get(sections.size() - 1).getB());
    }

    @Override
    public double getMaxB() {
        return Math.max(sections.get(0).getB(), sections.get(sections.size() - 1).getB());
    }

    @Override
    public double getAmplitudeB() {
        return Math.abs(getMaxB() - getMinB());
    }

    @Override
    public void updateState() {
        shuntCompensator.getTerminal().setQ(q.eval() * PerUnit.SB);
        if (shuntCompensator.isVoltageRegulatorOn()) {
            shuntCompensator.setSectionCount(position);
        }
    }
}
