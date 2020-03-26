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
import net.jafama.FastMath;
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
    private static final double TARGET_V_EPSILON = 1e-2;

    private final Bus bus;

    private final double nominalV;

    private boolean voltageControl = false;

    private double initialLoadTargetP = 0;

    private double loadTargetP = 0;

    private int loadCount = 0;

    private double loadTargetQ = 0;

    private double generationTargetQ = 0;

    private double targetV = Double.NaN;

    private LfBus controlledBus;

    private final List<LfBus> controlledBuses = new ArrayList<>();

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
    public Optional<LfBus> getControlledBus() {
        return Optional.ofNullable(controlledBus);
    }

    public void setControlledBus(LfBusImpl controlledBus) {
        Objects.requireNonNull(controlledBus);
        // check that remote control bus is still the same
        if (this.controlledBus != null && this.controlledBus.getNum() != controlledBus.getNum()) {
            throw new PowsyblException("Generators " + getGeneratorIds()
                    + " connected to bus '" + getId() + "' must control the voltage of the same bus");
        }

        if (voltageControl) {
            // check target voltage consistency between local and remote control
            if (controlledBus.hasVoltageControl()) { // controlled bus has also local voltage control
                double localTargetV = controlledBus.getTargetV() * controlledBus.getNominalV();
                if (FastMath.abs(this.targetV - localTargetV) > TARGET_V_EPSILON) {
                    throw new PowsyblException("Bus '" + controlledBus.getId()
                            + "' controlled by bus '" + getId() + "' has also a local voltage control with a different value: "
                            + localTargetV + " and " + this.targetV);
                }
            }

            // check that if target voltage is consistent with other already existing controller buses
            List<LfBus> otherControllerBuses = controlledBus.getControllerBuses();
            if (!otherControllerBuses.isEmpty()) {
                // just need to check first bus that control voltage
                otherControllerBuses.stream()
                        .filter(LfBus::hasVoltageControl)
                        .findFirst()
                        .ifPresent(otherControllerBus -> {
                            double otherTargetV = otherControllerBus.getTargetV() * controlledBus.getNominalV();
                            if (FastMath.abs(otherTargetV - this.targetV) > TARGET_V_EPSILON) {
                                throw new PowsyblException("Bus '" + getId() + "' control voltage of bus '" + controlledBus.getId()
                                        + "' which is already controlled by at least the bus '" + otherControllerBus.getId()
                                        + "' with a different target voltage: " + otherTargetV + " and " + this.targetV);
                            }
                        });
            }
        }

        this.controlledBus = controlledBus;
        controlledBus.addControlledBus(this);
    }

    @Override
    public List<LfBus> getControllerBuses() {
        return controlledBuses;
    }

    public void addControlledBus(LfBus controlledBus) {
        Objects.requireNonNull(controlledBus);
        controlledBuses.add(controlledBus);
    }

    private double checkTargetV(double targetV) {
        if (!Double.isNaN(this.targetV) && FastMath.abs(this.targetV - targetV) > TARGET_V_EPSILON) {
            throw new PowsyblException("Generators " + getGeneratorIds() + " are connected to the same bus '" + getId()
                    + "' with a different target voltages: " + targetV + " and " + this.targetV);
        }
        return targetV;
    }

    private List<String> getGeneratorIds() {
        return generators.stream().map(LfGenerator::getId).collect(Collectors.toList());
    }

    void addLoad(Load load) {
        loads.add(load);
        initialLoadTargetP = load.getP0();
        loadTargetP += load.getP0();
        loadTargetQ += load.getQ0();
        loadCount++;
    }

    void addBattery(Battery battery) {
        batteries.add(battery);
        initialLoadTargetP += battery.getP0();
        loadTargetP += battery.getP0();
        loadTargetQ += battery.getQ0();
    }

    private void add(LfGenerator generator, boolean voltageControl, double targetV, double targetQ,
                     LfNetworkLoadingReport report) {
        generators.add(generator);
        boolean voltageControl2 = voltageControl;
        double maxRangeQ = generator.getMaxRangeQ();
        if (voltageControl && maxRangeQ < REACTIVE_RANGE_THRESHOLD_PU) {
            LOGGER.trace("Discard generator '{}' from voltage control because max reactive range ({}) is too small",
                    generator.getId(), maxRangeQ);
            report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall++;
            voltageControl2 = false;
        }
        if (voltageControl && Math.abs(generator.getTargetP()) < POWER_EPSILON_SI && generator.getMinP() > POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from voltage control because not started (targetP={} MW, minP={} MW)",
                    generator.getId(), generator.getTargetP(), generator.getMinP());
            report.generatorsDiscardedFromVoltageControlBecauseNotStarted++;
            voltageControl2 = false;
        }
        if (voltageControl2) {
            this.targetV = checkTargetV(targetV);
            this.voltageControl = true;
        } else {
            generationTargetQ += targetQ;
        }
    }

    void addGenerator(Generator generator, double scaleV, LfNetworkLoadingReport report) {
        add(LfGeneratorImpl.create(generator, report), generator.isVoltageRegulatorOn(), generator.getTargetV() * scaleV,
                generator.getTargetQ(), report);
    }

    void addStaticVarCompensator(StaticVarCompensator staticVarCompensator, double scaleV, LfNetworkLoadingReport report) {
        if (staticVarCompensator.getRegulationMode() != StaticVarCompensator.RegulationMode.OFF) {
            add(LfStaticVarCompensatorImpl.create(staticVarCompensator),
                    staticVarCompensator.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE,
                    staticVarCompensator.getVoltageSetPoint() * scaleV, staticVarCompensator.getReactivePowerSetPoint(),
                    report);
        }
    }

    void addVscConverterStation(VscConverterStation vscCs, LfNetworkLoadingReport report) {
        add(LfVscConverterStationImpl.create(vscCs), vscCs.isVoltageRegulatorOn(), vscCs.getVoltageSetpoint(),
                vscCs.getReactivePowerSetpoint(), report);
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
    public void setLoadTargetP(double loadTargetP) {
        this.loadTargetP = loadTargetP * PerUnit.SB;
    }

    @Override
    public int getLoadCount() {
        return loadCount; }

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
        return targetV / (controlledBus != null ? controlledBus.getNominalV() : nominalV);
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
            if (initialLoadTargetP != 0) {
                load.setP0(load.getP0() * loadTargetP / initialLoadTargetP);
            }
            load.getTerminal()
                    .setP(load.getP0())
                    .setQ(load.getQ0());
        }

        // update battery power
        for (Battery battery : batteries) {
            if (initialLoadTargetP != 0) {
                battery.setP0(battery.getP0() * loadTargetP / initialLoadTargetP);
            }
            battery.getTerminal()
                    .setP(battery.getP0())
                    .setQ(battery.getQ0());
        }
    }
}
