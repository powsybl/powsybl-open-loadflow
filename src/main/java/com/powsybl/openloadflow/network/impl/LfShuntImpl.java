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

    private static final Logger LOGGER = LoggerFactory.getLogger(LfShuntImpl.class);

    private final List<ShuntCompensator> shuntCompensators;

    private final LfBus bus;

    private final boolean withVoltageControl;

    private final List<Controller> controllers = new ArrayList<>();

    private double b;

    private final double zb;

    private static class Controller {

        private final String id;

        private final List<Double> sections;

        private int position;

        private final double bMagnitude;

        public Controller(String id, List<Double> sections, int position) {
            this.id = Objects.requireNonNull(id);
            this.sections = Objects.requireNonNull(sections);
            this.position = position;
            double bMin = Math.min(sections.get(0), sections.get(sections.size() - 1));
            double bMax = Math.max(sections.get(0), sections.get(sections.size() - 1));
            this.bMagnitude = Math.abs(bMax - bMin);
        }

        public List<Double> getSections() {
            return sections;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public double getB() {
            return sections.get(this.position);
        }

        public double getBMagnitude() {
            return bMagnitude;
        }

        public String getId() {
            return id;
        }
    }

    public LfShuntImpl(List<ShuntCompensator> shuntCompensators, LfNetwork network, LfBus bus, boolean withVoltageControl) {
        // if withVoltageControl equals to true, all shunt compensators that are listed must control voltage.
        // if withVoltageControl equals to false, all shunt compensators that are listed will be treated as fixed shunt
        // compensators.
        super(network);
        this.shuntCompensators = Objects.requireNonNull(shuntCompensators);
        if (shuntCompensators.isEmpty()) {
            throw new IllegalArgumentException("Empty shunt compensator list");
        }
        this.bus = Objects.requireNonNull(bus);
        this.withVoltageControl = withVoltageControl;
        double nominalV = shuntCompensators.get(0).getTerminal().getVoltageLevel().getNominalV(); // has to be the same for all shunts
        zb = nominalV * nominalV / PerUnit.SB;
        b = shuntCompensators.stream()
                .mapToDouble(ShuntCompensator::getB)
                .map(aB -> aB * zb)
                .sum();

        if (withVoltageControl) {
            shuntCompensators.forEach(shuntCompensator -> {
                List<Double> sections = new ArrayList<>(1);
                sections.add(0.0);
                ShuntCompensatorModel model = shuntCompensator.getModel();
                switch (shuntCompensator.getModelType()) {
                    case LINEAR:
                        ShuntCompensatorLinearModel linearModel = (ShuntCompensatorLinearModel) model;
                        for (int section = 1; section <= shuntCompensator.getMaximumSectionCount(); section++) {
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
                controllers.add(new Controller(shuntCompensator.getId(), sections, shuntCompensator.getSectionCount()));
            });
        }
    }

    @Override
    public ElementType getType() {
        return ElementType.SHUNT_COMPENSATOR;
    }

    @Override
    public String getId() {
        return controllers.isEmpty() ? bus.getId() + "_shunt_compensators" : bus.getId() + "_controller_shunt_compensators";
    }

    @Override
    public double getB() {
        return b;
    }

    @Override
    public void setB(double b) {
        this.b = b;
    }

    private void roundBToClosestSection(double b, Controller controller) {
        List<Double> sections = controller.getSections();
        // find tap position with the closest b value
        double smallestDistance = Math.abs(b - sections.get(controller.getPosition()));
        for (int s = 0; s < sections.size(); s++) {
            double distance = Math.abs(b - sections.get(s));
            if (distance < smallestDistance) {
                controller.setPosition(s);
                smallestDistance = distance;
            }
        }
        LOGGER.info("Round B shift of shunt '{}': {} -> {}", controller.getId(), b, controller.getB());
    }

    @Override
    public double dispatchB() {
        List<Controller> sortedControllers = controllers.stream()
                .sorted(Comparator.comparing(Controller::getBMagnitude))
                .collect(Collectors.toList());
        double residueB = b;
        int remainingControllers = sortedControllers.size();
        for (Controller sortedController : sortedControllers) {
            double bToDispatchByController = residueB / remainingControllers--;
            roundBToClosestSection(bToDispatchByController, sortedController);
            residueB -= sortedController.getB();
        }
        b = controllers.stream().mapToDouble(Controller::getB).sum();
        return residueB;
    }

    @Override
    public void updateState() {
        double vSquare = bus.getV() * bus.getV() * bus.getNominalV() * bus.getNominalV();
        if (!withVoltageControl) {
            for (ShuntCompensator sc : shuntCompensators) {
                sc.getTerminal().setQ(-sc.getB() * vSquare);
            }
        } else {
            for (int i = 0; i < shuntCompensators.size(); i++) {
                ShuntCompensator sc = shuntCompensators.get(i);
                sc.getTerminal().setQ(-controllers.get(i).getB() * vSquare / zb);
                sc.setSectionCount(controllers.get(i).getPosition());
            }
        }
    }
}
