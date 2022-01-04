/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfShuntImpl extends AbstractElement implements LfShunt {

    protected static final Logger LOGGER = LoggerFactory.getLogger(LfShuntImpl.class);

    private final List<ShuntCompensator> fixedShunts = new ArrayList<>();

    private final List<ShuntCompensator> controllerShunts = new ArrayList<>();

    private double b;

    protected LfBus bus;

    private List<ControllerLfShunt> controllerLfShunts = new ArrayList<>();

    private double zb;

    private class ControllerLfShunt {

        private Double bAmplitude;

        private Integer position;

        private List<Double> sections;

        private String id;

        public ControllerLfShunt(List<Double> sections, Integer position, String id) {
            this.sections = sections;
            this.position = position;
            double bMin = Math.min(sections.get(0), sections.get(sections.size() - 1));
            double bMax = Math.max(sections.get(0), sections.get(sections.size() - 1));
            this.bAmplitude = Math.abs(bMax - bMin);
            this.id = id;
        }

        public List<Double> getSections() {
            return this.sections;
        }

        public Integer getPosition() {
            return this.position;
        }

        public void setPosition(Integer position) {
            this.position = position;
        }

        public double getB() {
            return this.sections.get(this.position);
        }

        public double getBAmplitude() {
            return this.bAmplitude;
        }

        public String getId() {
            return this.id;
        }
    }

    public LfShuntImpl(List<ShuntCompensator> shuntCompensators, LfNetwork network) {
        super(network);
        if (shuntCompensators.isEmpty()) {
            throw new IllegalArgumentException("Empty shunt compensator list");
        }
        for (ShuntCompensator sc : shuntCompensators) {
            if (sc.isVoltageRegulatorOn()) {
                this.controllerShunts.add(sc);
            } else {
                this.fixedShunts.add(sc);
            }
        }
        double nominalV = shuntCompensators.get(0).getTerminal().getVoltageLevel().getNominalV(); // has to be the same for all shunts
        this.zb = nominalV * nominalV / PerUnit.SB;
        b = shuntCompensators.stream()
                .mapToDouble(ShuntCompensator::getB)
                .map(aB -> aB * zb)
                .sum();

        if (!this.controllerShunts.isEmpty()) {
            controllerShunts.stream().forEach(shuntCompensator -> {
                List<Double> sections = new ArrayList<>();
                sections.add(0.0);
                ShuntCompensatorModel model = shuntCompensator.getModel();
                switch (shuntCompensator.getModelType()) {
                    case LINEAR:
                        ShuntCompensatorLinearModel linearModel = (ShuntCompensatorLinearModel) model;
                        for (int section = 0; section <= shuntCompensator.getMaximumSectionCount(); section++) {
                            sections.add(linearModel.getBPerSection() * section * zb);
                        }
                        break;
                    case NON_LINEAR:
                        ShuntCompensatorNonLinearModel nonLinearModel = (ShuntCompensatorNonLinearModel) model;
                        for (int section = 0; section < shuntCompensator.getMaximumSectionCount(); section++) {
                            sections.add(nonLinearModel.getAllSections().get(section).getB() * zb);
                        }
                        break;
                }
                controllerLfShunts.add(new ControllerLfShunt(sections, shuntCompensator.getSectionCount(), shuntCompensator.getId()));
            });
        }
    }

    @Override
    public ElementType getType() {
        return ElementType.SHUNT_COMPENSATOR;
    }

    @Override
    public String getId() {
        return this.controllerLfShunts.isEmpty() ? bus.getId() + "_shunt_compensators" : bus.getId() + "_controller_shunt_compensators";
    }

    @Override
    public double getB() {
        return b;
    }

    @Override
    public void setB(double b) {
        this.b = b;
    }

    @Override
    public void setBus(LfBus bus) {
        this.bus = bus;
    }

    private void roundBToClosestSection(double b, ControllerLfShunt shunt) {
        List<Double> sections = shunt.getSections();
        // find tap position with the closest b value
        double smallestDistance = Math.abs(b - sections.get(shunt.getPosition()));
        for (int s = 0; s < sections.size(); s++) {
            double distance = Math.abs(b - sections.get(s));
            if (distance < smallestDistance) {
                shunt.setPosition(s);
                smallestDistance = distance;
            }
        }
        LOGGER.info("Round B shift of shunt '{}': {} -> {}", shunt.getId(), b, shunt.getB());
    }

    @Override
    public double dispatchB() {
        List<ControllerLfShunt> sortedShunts = controllerLfShunts.stream()
                .sorted(Comparator.comparing(ControllerLfShunt::getBAmplitude))
                .collect(Collectors.toList());
        double residueB = b;
        int remainingShunts = sortedShunts.size();
        for (int i = 0; i < sortedShunts.size(); i++) {
            double bToDispatchByShunt = residueB / remainingShunts--;
            roundBToClosestSection(bToDispatchByShunt, sortedShunts.get(i));
            residueB -= sortedShunts.get(i).getB();
        }
        this.b = controllerLfShunts.stream().mapToDouble(ControllerLfShunt::getB).sum();
        return residueB;
    }

    @Override
    public void updateState() {
        double vSquare = bus.getV() * bus.getV() * bus.getNominalV() * bus.getNominalV();
        for (ShuntCompensator sc : fixedShunts) {
            sc.getTerminal().setQ(-sc.getB() * vSquare);
        }
        if (!this.controllerLfShunts.isEmpty()) {
            for (int i = 0; i < controllerShunts.size(); i++) {
                ShuntCompensator sc = controllerShunts.get(i);
                sc.getTerminal().setQ(-controllerLfShunts.get(i).getB() * vSquare / this.zb);
                sc.setSectionCount(controllerLfShunts.get(i).getPosition());
            }
        }
    }
}
