/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorLinearModel;
import com.powsybl.iidm.network.ShuntCompensatorModel;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModel;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.Range;
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

    private final List<Ref<ShuntCompensator>> shuntCompensatorsRefs;

    private final LfBus bus;

    private ShuntVoltageControl voltageControl;

    protected Double shuntVoltageControlTargetDeadband;

    private boolean voltageControlCapability;

    private boolean voltageControlEnabled = false;

    private final List<Controller> controllers = new ArrayList<>();

    private double b;

    private final double zb;

    private double g;

    public static class Controller {

        private final String id;

        private final List<Double> sectionsB;

        private final List<Double> sectionsG;

        private int position;

        private final double bMagnitude;

        public Controller(String id, List<Double> sectionsB, List<Double> sectionsG, int position) {
            this.id = Objects.requireNonNull(id);
            this.sectionsB = Objects.requireNonNull(sectionsB);
            this.sectionsG = Objects.requireNonNull(sectionsG);
            this.position = position;
            double bMin = Math.min(sectionsB.get(0), sectionsB.get(sectionsB.size() - 1));
            double bMax = Math.max(sectionsB.get(0), sectionsB.get(sectionsB.size() - 1));
            this.bMagnitude = Math.abs(bMax - bMin);
        }

        public String getId() {
            return id;
        }

        public List<Double> getSectionsB() {
            return sectionsB;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public double getB() {
            return sectionsB.get(this.position);
        }

        public double getG() {
            return sectionsG.get(this.position);
        }

        public double getBMagnitude() {
            return bMagnitude;
        }

        public enum Direction {
            INCREASE(AllowedDirection.INCREASE),
            DECREASE(AllowedDirection.DECREASE);

            private final AllowedDirection allowedDirection;

            Direction(AllowedDirection allowedDirection) {
                this.allowedDirection = allowedDirection;
            }

            public AllowedDirection getAllowedDirection() {
                return allowedDirection;
            }
        }

        public enum AllowedDirection {
            INCREASE,
            DECREASE,
            BOTH
        }

        private Range<Integer> getAllowedPositionRange(AllowedDirection allowedDirection) {
            switch (allowedDirection) {
                case INCREASE:
                    return Range.between(position, sectionsB.size() - 1);
                case DECREASE:
                    return Range.between(0, position);
                case BOTH:
                    return Range.between(0, sectionsB.size() - 1);
                default:
                    throw new IllegalStateException("Unknown direction: " + allowedDirection);
            }
        }

        public Optional<Direction> updateTapPositionB(double deltaB, int maxTapShift, AllowedDirection allowedDirection) {
            // an increase allowed direction means that the tap could increase.
            // a decrease allowed direction means that the tap could decrease.
            double newB = getB() + deltaB;
            Range<Integer> positionRange = getAllowedPositionRange(allowedDirection);

            int oldTapPosition = position;
            // find tap position with the closest b value without exceeding the maximum of taps to switch.
            double smallestDistance = Math.abs(deltaB);
            for (int p = positionRange.getMinimum(); p <= positionRange.getMaximum(); p++) {
                if (Math.abs(p - oldTapPosition) > maxTapShift) {
                    // we are not allowed in one outer loop run to go further than maxTapShift positions
                    continue;
                }
                double distance = Math.abs(newB - sectionsB.get(p));
                if (distance < smallestDistance) {
                    position = p;
                    smallestDistance = distance;
                }
            }

            boolean hasChanged = position != oldTapPosition;
            if (hasChanged) {
                return Optional.of(position - oldTapPosition > 0 ? Direction.INCREASE : Direction.DECREASE);
            }
            return Optional.empty();
        }
    }

    public LfShuntImpl(List<ShuntCompensator> shuntCompensators, LfNetwork network, LfBus bus, boolean voltageControlCapability) {
        // if withVoltageControl equals to true, all shunt compensators that are listed must control voltage.
        // if withVoltageControl equals to false, all shunt compensators that are listed will be treated as fixed shunt
        // compensators.
        super(network);
        shuntCompensatorsRefs = Objects.requireNonNull(shuntCompensators).stream().map(Ref::new).collect(Collectors.toList());
        if (shuntCompensators.isEmpty()) {
            throw new IllegalArgumentException("Empty shunt compensator list");
        }
        this.bus = Objects.requireNonNull(bus);
        this.voltageControlCapability = voltageControlCapability;
        double nominalV = shuntCompensators.get(0).getTerminal().getVoltageLevel().getNominalV(); // has to be the same for all shunts
        zb = PerUnit.zb(nominalV);
        b = zb * shuntCompensators.stream()
                .mapToDouble(ShuntCompensator::getB)
                .sum();
        g = zb * shuntCompensators.stream()
                .mapToDouble(ShuntCompensator::getG)
                .sum();

        if (voltageControlCapability) {
            shuntCompensators.forEach(shuntCompensator -> {
                List<Double> sectionsB = new ArrayList<>(1);
                List<Double> sectionsG = new ArrayList<>(1);
                sectionsB.add(0.0);
                sectionsG.add(0.0);
                ShuntCompensatorModel model = shuntCompensator.getModel();
                switch (shuntCompensator.getModelType()) {
                    case LINEAR:
                        ShuntCompensatorLinearModel linearModel = (ShuntCompensatorLinearModel) model;
                        for (int section = 1; section <= shuntCompensator.getMaximumSectionCount(); section++) {
                            sectionsB.add(linearModel.getBPerSection() * section * zb);
                            sectionsG.add(linearModel.getGPerSection() * section * zb);
                        }
                        break;
                    case NON_LINEAR:
                        ShuntCompensatorNonLinearModel nonLinearModel = (ShuntCompensatorNonLinearModel) model;
                        for (int section = 0; section < shuntCompensator.getMaximumSectionCount(); section++) {
                            sectionsB.add(nonLinearModel.getAllSections().get(section).getB() * zb);
                            sectionsG.add(nonLinearModel.getAllSections().get(section).getG() * zb);
                        }
                        break;
                }
                controllers.add(new Controller(shuntCompensator.getId(), sectionsB, sectionsG, shuntCompensator.getSectionCount()));
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
    public List<String> getOriginalIds() {
        return shuntCompensatorsRefs.stream().map(scRef -> scRef.get().getId()).collect(Collectors.toList());
    }

    @Override
    public double getB() {
        return b;
    }

    @Override
    public void setB(double b) {
        this.b = b;
    }

    public void updateB() {
        setB(controllers.stream().mapToDouble(Controller::getB).sum());
    }

    @Override
    public double getG() {
        return g;
    }

    public void updateG() {
        setG(controllers.stream().mapToDouble(Controller::getG).sum());
    }

    @Override
    public void setG(double g) {
        this.g = g;
    }

    @Override
    public boolean hasVoltageControlCapability() {
        return voltageControlCapability;
    }

    @Override
    public void setVoltageControlCapability(boolean voltageControlCapability) {
        this.voltageControlCapability = voltageControlCapability;
    }

    @Override
    public boolean isVoltageControlEnabled() {
        return voltageControlEnabled;
    }

    @Override
    public void setVoltageControlEnabled(boolean voltageControlEnabled) {
        if (this.voltageControlEnabled != voltageControlEnabled) {
            this.voltageControlEnabled = voltageControlEnabled;
            for (LfNetworkListener listener : network.getListeners()) {
                listener.onShuntVoltageControlChange(this, voltageControlEnabled);
            }
        }
    }

    @Override
    public Optional<ShuntVoltageControl> getVoltageControl() {
        return Optional.ofNullable(voltageControl);
    }

    @Override
    public void setVoltageControl(ShuntVoltageControl voltageControl) {
        this.voltageControl = voltageControl;
    }

    public Optional<Double> getShuntVoltageControlTargetDeadband() {
        return Optional.ofNullable(shuntVoltageControlTargetDeadband);
    }

    public void setShuntVoltageControlTargetDeadband(Double shuntVoltageControlTargetDeadband) {
        this.shuntVoltageControlTargetDeadband = shuntVoltageControlTargetDeadband;
    }

    public List<Controller> getControllers() {
        return controllers;
    }

    private void roundBToClosestSection(double b, Controller controller) {
        List<Double> sections = controller.getSectionsB();
        // find tap position with the closest b value
        double smallestDistance = Math.abs(b - sections.get(controller.getPosition()));
        for (int s = 0; s < sections.size(); s++) {
            double distance = Math.abs(b - sections.get(s));
            if (distance < smallestDistance) {
                controller.setPosition(s);
                smallestDistance = distance;
            }
        }
        LOGGER.trace("Round B shift of shunt '{}': {} -> {}", controller.getId(), b, controller.getB());
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
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        if (parameters.isDc()) {
            for (var scRef : shuntCompensatorsRefs) {
                var sc = scRef.get();
                sc.getTerminal().setP(0);
            }
        } else {
            double vSquare = bus.getV() * bus.getV() * bus.getNominalV() * bus.getNominalV();
            if (!voltageControlCapability) {
                for (var scRef : shuntCompensatorsRefs) {
                    var sc = scRef.get();
                    sc.getTerminal().setP(sc.getG() * vSquare);
                    sc.getTerminal().setQ(-sc.getB() * vSquare);
                }
            } else {
                for (int i = 0; i < shuntCompensatorsRefs.size(); i++) {
                    ShuntCompensator sc = shuntCompensatorsRefs.get(i).get();
                    sc.getTerminal().setP(controllers.get(i).getG() * vSquare / zb);
                    sc.getTerminal().setQ(-controllers.get(i).getB() * vSquare / zb);
                    sc.setSectionCount(controllers.get(i).getPosition());
                }
            }
        }
    }
}
