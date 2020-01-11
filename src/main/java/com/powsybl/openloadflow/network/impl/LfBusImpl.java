/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBusImpl extends AbstractLfBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfBusImpl.class);

    private static final double REACTIVE_RANGE_THRESHOLD_PU = 1d / PerUnit.SB;
    private static final double POWER_EPSILON_SI = 1e-4;
    private static final double Q_DISPATCH_EPSILON = 1e-3;

    private final Bus bus;

    private final double nominalV;

    private boolean voltageControl = false;

    private double loadTargetP = 0;

    private double loadTargetQ = 0;

    private double generationTargetQ = 0;

    private double targetV = Double.NaN;

    private LfBus remoteControlTargetBus;

    private final List<LfBus> remoteControlSourceBuses = new ArrayList<>();

    private final List<LfGenerator> generators = new ArrayList<>();

    private final List<LfShunt> shunts = new ArrayList<>();

    private final List<Load> loads = new ArrayList<>();

    private final List<Battery> batteries = new ArrayList<>();

    protected LfBusImpl(Bus bus, double v, double angle) {
        super(v, angle);
        this.bus = bus;
        nominalV = bus.getVoltageLevel().getNominalV();
    }

    public static LfBusImpl create(Bus bus) {
        Objects.requireNonNull(bus);
        return new LfBusImpl(bus, bus.getV(), bus.getAngle());
    }

    @Override
    public String getId() {
        return bus.getId();
    }

    @Override
    public boolean isFictitious() {
        return false;
    }

    @Override
    public boolean hasVoltageControl() {
        return voltageControl;
    }

    @Override
    public void setVoltageControl(boolean voltageControl) {
        this.voltageControl = voltageControl;
    }

    @Override
    public Optional<LfBus> getRemoteControlTargetBus() {
        return Optional.ofNullable(remoteControlTargetBus);
    }

    public void setRemoteControlTargetBus(LfBusImpl remoteControlTargetBus) {
        Objects.requireNonNull(remoteControlTargetBus);
        // check that remote control bus is still the same
        if (this.remoteControlTargetBus != null && this.remoteControlTargetBus.getNum() != remoteControlTargetBus.getNum()) {
            throw new PowsyblException("Generators " + generators.stream().map(LfGenerator::getId).collect(Collectors.joining(", "))
                    + " connected to bus '" + getId() + "' must control the voltage of the same bus");
        }
        this.remoteControlTargetBus = remoteControlTargetBus;
        remoteControlTargetBus.addRemoteControlSource(this);
    }

    @Override
    public List<LfBus> getRemoteControlSourceBuses() {
        return remoteControlSourceBuses;
    }

    public void addRemoteControlSource(LfBus remoteControlSource) {
        Objects.requireNonNull(remoteControlSource);
        remoteControlSourceBuses.add(remoteControlSource);
    }

    private double checkTargetV(double targetV) {
        if (!Double.isNaN(this.targetV) && this.targetV != targetV) {
            throw new PowsyblException("Multiple generators connected to same bus with different target voltage");
        }
        return targetV;
    }

    void addLoad(Load load) {
        loads.add(load);
        this.loadTargetP += load.getP0();
        this.loadTargetQ += load.getQ0();
    }

    void addBattery(Battery battery) {
        batteries.add(battery);
        loadTargetP += battery.getP0();
        loadTargetQ += battery.getQ0();
    }

    private void add(LfGenerator generator, boolean voltageControl, double targetV, double targetQ) {
        generators.add(generator);
        boolean voltageControl2 = voltageControl;
        double maxRangeQ = generator.getMaxRangeQ();
        if (voltageControl && maxRangeQ < REACTIVE_RANGE_THRESHOLD_PU) {
            LOGGER.warn("Discard generator '{}' from voltage control because max reactive range ({}) is too small",
                    generator.getId(), maxRangeQ);
            voltageControl2 = false;
        }
        if (voltageControl && Math.abs(generator.getTargetP()) < POWER_EPSILON_SI && generator.getMinP() > POWER_EPSILON_SI) {
            LOGGER.warn("Discard generator '{}' from voltage control because not started (targetP={} MW, minP={} MW)",
                    generator.getId(), generator.getTargetP(), generator.getMinP());
            voltageControl2 = false;
        }
        if (voltageControl2) {
            this.targetV = checkTargetV(targetV);
            this.voltageControl = true;
        } else {
            generationTargetQ += targetQ;
        }
    }

    void addGenerator(Generator generator, double scaleV) {
        add(LfGeneratorImpl.create(generator), generator.isVoltageRegulatorOn(), generator.getTargetV() * scaleV,
                generator.getTargetQ());
    }

    void addStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
        if (staticVarCompensator.getRegulationMode() != StaticVarCompensator.RegulationMode.OFF) {
            add(LfStaticVarCompensatorImpl.create(staticVarCompensator),
                    staticVarCompensator.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE,
                    staticVarCompensator.getVoltageSetPoint(), staticVarCompensator.getReactivePowerSetPoint());
        }
    }

    void addVscConverterStation(VscConverterStation vscCs) {
        add(LfVscConverterStationImpl.create(vscCs), vscCs.isVoltageRegulatorOn(), vscCs.getVoltageSetpoint(),
                vscCs.getReactivePowerSetpoint());
    }

    void addShuntCompensator(ShuntCompensator sc) {
        shunts.add(new LfShuntImpl(sc));
    }

    @Override
    public double getGenerationTargetP() {
        return generators.stream().mapToDouble(LfGenerator::getTargetP).sum();
    }

    @Override
    public double getGenerationTargetQ() {
        return generationTargetQ / PerUnit.SB;
    }

    @Override
    public void setGenerationTargetQ(double generationTargetQ) {
        this.generationTargetQ = generationTargetQ * PerUnit.SB;
    }

    @Override
    public double getLoadTargetP() {
        return loadTargetP / PerUnit.SB;
    }

    @Override
    public double getLoadTargetQ() {
        return loadTargetQ / PerUnit.SB;
    }

    private double getLimitQ(ToDoubleFunction<LfGenerator> limitQ) {
        return generators.stream()
                .mapToDouble(generator -> generator.hasVoltageControl() ? limitQ.applyAsDouble(generator)
                                                                        : generator.getTargetQ())
                .sum();
    }

    @Override
    public double getMinQ() {
        return getLimitQ(LfGenerator::getMinQ);
    }

    @Override
    public double getMaxQ() {
        return getLimitQ(LfGenerator::getMaxQ);
    }

    @Override
    public double getTargetV() {
        return targetV / (remoteControlTargetBus != null ? remoteControlTargetBus.getNominalV() : nominalV);
    }

    @Override
    public double getV() {
        return v / nominalV;
    }

    @Override
    public void setV(double v) {
        this.v = v * nominalV;
    }

    @Override
    public double getAngle() {
        return angle;
    }

    @Override
    public void setAngle(double angle) {
        this.angle = angle;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public List<LfShunt> getShunts() {
        return shunts;
    }

    @Override
    public List<LfGenerator> getGenerators() {
        return generators;
    }

    private double dispatchQ(List<LfGenerator> generatorsThatControlVoltage, boolean reactiveLimits, double qToDispatch) {
        double residueQ = 0;
        Iterator<LfGenerator> itG = generatorsThatControlVoltage.iterator();
        while (itG.hasNext()) {
            LfGenerator generator = itG.next();
            double calculatedQ = qToDispatch / generatorsThatControlVoltage.size();
            if (reactiveLimits && calculatedQ < generator.getMinQ()) {
                generator.setCalculatedQ(generator.getCalculatedQ() + generator.getMinQ());
                residueQ += calculatedQ - generator.getMinQ();
                itG.remove();
            } else if (reactiveLimits && calculatedQ > generator.getMaxQ()) {
                generator.setCalculatedQ(generator.getCalculatedQ() + generator.getMaxQ());
                residueQ += calculatedQ - generator.getMaxQ();
                itG.remove();
            } else {
                generator.setCalculatedQ(generator.getCalculatedQ() + calculatedQ);
            }
        }
        return residueQ;
    }

    private void updateGeneratorsState(double generationQ, boolean reactiveLimits) {
        double qToDispatch = generationQ / PerUnit.SB;
        List<LfGenerator> generatorsThatControlVoltage = new LinkedList<>();
        for (LfGenerator generator : generators) {
            if (generator.hasVoltageControl()) {
                generatorsThatControlVoltage.add(generator);
            } else {
                qToDispatch -= generator.getTargetQ();
            }
        }

        for (LfGenerator generator : generatorsThatControlVoltage) {
            generator.setCalculatedQ(0);
        }
        while (!generatorsThatControlVoltage.isEmpty() && Math.abs(qToDispatch) > Q_DISPATCH_EPSILON) {
            qToDispatch = dispatchQ(generatorsThatControlVoltage, reactiveLimits, qToDispatch);
        }
    }

    @Override
    public void updateState(boolean reactiveLimits) {
        bus.setV(v).setAngle(angle);

        // update generator reactive power
        updateGeneratorsState(voltageControl ? calculatedQ + loadTargetQ : generationTargetQ, reactiveLimits);

        // update load power
        for (Load load : loads) {
            load.getTerminal()
                    .setP(load.getP0())
                    .setQ(load.getQ0());
        }

        // update battery power
        for (Battery battery : batteries) {
            battery.getTerminal()
                    .setP(battery.getP0())
                    .setQ(battery.getQ0());
        }
    }
}
