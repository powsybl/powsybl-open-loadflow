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
public abstract class AbstractLfBus implements LfBus {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfBus.class);

    private static final double POWER_EPSILON_SI = 1e-4;
    private static final double Q_DISPATCH_EPSILON = 1e-3;
    private static final double TARGET_V_EPSILON = 1e-2;

    private int num = -1;

    private boolean slack = false;

    protected double v;

    protected double angle;

    protected double calculatedQ = Double.NaN;

    protected boolean voltageControlCapacility = false;

    protected boolean voltageControl = false;

    protected int voltageControlSwitchOffCount = 0;

    protected double initialLoadTargetP = 0;

    protected double loadTargetP = 0;

    protected int positiveLoadCount = 0;

    protected double loadTargetQ = 0;

    protected double generationTargetQ = 0;

    protected double targetV = Double.NaN;

    protected LfBus controlledBus;

    protected final List<LfBus> controllerBuses = new ArrayList<>();

    protected final List<LfGenerator> generators = new ArrayList<>();

    protected final List<LfShunt> shunts = new ArrayList<>();

    protected final List<Load> loads = new ArrayList<>();

    protected final List<Battery> batteries = new ArrayList<>();

    protected final List<LccConverterStation> lccCss = new ArrayList<>();

    protected final List<LfBranch> branches = new ArrayList<>();

    protected AbstractLfBus(double v, double angle) {
        this.v = v;
        this.angle = angle;
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public void setNum(int num) {
        this.num = num;
    }

    @Override
    public boolean isSlack() {
        return slack;
    }

    @Override
    public void setSlack(boolean slack) {
        this.slack = slack;
    }

    @Override
    public double getTargetP() {
        return getGenerationTargetP() - getLoadTargetP();
    }

    @Override
    public double getTargetQ() {
        return getGenerationTargetQ() - getLoadTargetQ();
    }

    @Override
    public boolean hasVoltageControlCapability() {
        return voltageControlCapacility;
    }

    @Override
    public boolean hasVoltageControl() {
        return voltageControl;
    }

    @Override
    public void setVoltageControl(boolean voltageControl) {
        if (this.voltageControl && !voltageControl) {
            voltageControlSwitchOffCount++;
        }
        this.voltageControl = voltageControl;
    }

    @Override
    public int getVoltageControlSwitchOffCount() {
        return voltageControlSwitchOffCount;
    }

    @Override
    public Optional<LfBus> getControlledBus() {
        return Optional.ofNullable(controlledBus);
    }

    public void setControlledBus(AbstractLfBus controlledBus) {
        Objects.requireNonNull(controlledBus);
        // check that remote control bus is still the same
        if (this.controlledBus != null && this.controlledBus.getNum() != controlledBus.getNum()) {
            throw new PowsyblException("Generators " + getGeneratorIds()
                    + " connected to bus '" + getId() + "' must control the voltage of the same bus");
        }

        if (voltageControl) {
            // check that targetV has a plausible value (wrong nominal voltage issue)
            double targetVPu = targetV / controlledBus.getNominalV();
            if (targetVPu < PlausibleValues.MIN_TARGET_VOLTAGE_PU || targetVPu > PlausibleValues.MAX_TARGET_VOLTAGE_PU) {
                throw new PowsyblException("Controller bus '" + getId() + "' has an inconsistent remote target voltage: "
                        + targetVPu + " pu");
            }

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
                                LOGGER.error("Bus '{}' control voltage of bus '{}' which is already controlled by at least the bus '{}' with a different target voltage: {} (kept) and {} (ignored)",
                                        getId(), controlledBus.getId(), otherControllerBus.getId(), otherTargetV, this.targetV);
                                this.targetV = otherTargetV;
                            }
                        });
            }
        }

        this.controlledBus = controlledBus;
        controlledBus.addControllerBus(this);
    }

    @Override
    public List<LfBus> getControllerBuses() {
        return controllerBuses;
    }

    public void addControllerBus(LfBus controllerBus) {
        Objects.requireNonNull(controllerBus);
        controllerBuses.add(controllerBus);
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
        initialLoadTargetP += load.getP0();
        loadTargetP += load.getP0();
        loadTargetQ += load.getQ0();
        if (load.getP0() >= 0) {
            positiveLoadCount++;
        }
    }

    void addBattery(Battery battery) {
        batteries.add(battery);
        initialLoadTargetP += battery.getP0();
        loadTargetP += battery.getP0();
        loadTargetQ += battery.getQ0();
    }

    void addLccConverterStation(LccConverterStation lccCs) {
        lccCss.add(lccCs);
        HvdcLine line = lccCs.getHvdcLine();
        // The active power setpoint is always positive.
        // If the converter station is at side 1 and is rectifier, p should be positive.
        // If the converter station is at side 1 and is inverter, p should be negative.
        // If the converter station is at side 2 and is rectifier, p should be positive.
        // If the converter station is at side 2 and is inverter, p should be negative.
        boolean isConverterStationRectifier = HvdcConverterStations.isRectifier(lccCs);
        double p = (isConverterStationRectifier ? 1 : -1) * line.getActivePowerSetpoint() *
                (1 + (isConverterStationRectifier ? 1 : -1) * lccCs.getLossFactor() / 100); // A LCC station has active losses.
        double q = Math.abs(p * Math.tan(Math.acos(lccCs.getPowerFactor()))); // A LCC station always consumes reactive power.
        loadTargetP += p;
        loadTargetQ += q;
    }

    private void add(LfGenerator generator, boolean voltageControl, double targetV, double targetQ,
                     LfNetworkLoadingReport report) {
        generators.add(generator);
        boolean modifiedVoltageControl = voltageControl;
        double maxRangeQ = generator.getMaxRangeQ();
        if (voltageControl && maxRangeQ < PlausibleValues.MIN_REACTIVE_RANGE / PerUnit.SB) {
            LOGGER.trace("Discard generator '{}' from voltage control because max reactive range ({}) is too small",
                    generator.getId(), maxRangeQ);
            report.generatorsDiscardedFromVoltageControlBecauseMaxReactiveRangeIsTooSmall++;
            modifiedVoltageControl = false;
        }
        if (voltageControl && Math.abs(generator.getTargetP()) < POWER_EPSILON_SI && generator.getMinP() > POWER_EPSILON_SI) {
            LOGGER.trace("Discard generator '{}' from voltage control because not started (targetP={} MW, minP={} MW)",
                    generator.getId(), generator.getTargetP(), generator.getMinP());
            report.generatorsDiscardedFromVoltageControlBecauseNotStarted++;
            modifiedVoltageControl = false;
        }
        if (modifiedVoltageControl) {
            this.targetV = checkTargetV(targetV);
            this.voltageControl = true;
            this.voltageControlCapacility = true;
        } else {
            if (!Double.isNaN(targetQ)) {
                generationTargetQ += targetQ;
            }
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
                    staticVarCompensator.getVoltageSetPoint() * scaleV, -staticVarCompensator.getReactivePowerSetPoint(),
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
    public int getPositiveLoadCount() {
        return positiveLoadCount;
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
        return targetV / (controlledBus != null ? controlledBus.getNominalV() : getNominalV());
    }

    @Override
    public double getV() {
        return v / getNominalV();
    }

    @Override
    public void setV(double v) {
        this.v = v * getNominalV();
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
    public double getCalculatedQ() {
        return calculatedQ / PerUnit.SB;
    }

    @Override
    public void setCalculatedQ(double calculatedQ) {
        this.calculatedQ = calculatedQ * PerUnit.SB;
    }

    @Override
    public List<LfShunt> getShunts() {
        return shunts;
    }

    @Override
    public List<LfGenerator> getGenerators() {
        return generators;
    }

    @Override
    public List<LfBranch> getBranches() {
        return branches;
    }

    @Override
    public void addBranch(LfBranch branch) {
        branches.add(Objects.requireNonNull(branch));
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
        // update generator reactive power
        updateGeneratorsState(voltageControl ? calculatedQ + loadTargetQ : generationTargetQ, reactiveLimits);

        // update load power
        double factor = initialLoadTargetP != 0 ? loadTargetP / initialLoadTargetP : 1;
        for (Load load : loads) {
            load.getTerminal()
                    .setP(load.getP0() >= 0 ? factor * load.getP0() : load.getP0())
                    .setQ(load.getQ0());
        }

        // update battery power (which are not part of slack distribution)
        for (Battery battery : batteries) {
            battery.getTerminal()
                    .setP(battery.getP0())
                    .setQ(battery.getQ0());
        }

        // update lcc converter station power
        for (LccConverterStation lccCs : lccCss) {
            boolean isConverterStationRectifier = HvdcConverterStations.isRectifier(lccCs);
            HvdcLine line = lccCs.getHvdcLine();
            double p = (isConverterStationRectifier ? 1 : -1) * line.getActivePowerSetpoint() *
                    (1 + (isConverterStationRectifier ? 1 : -1) * lccCs.getLossFactor() / 100); // A LCC station has active losses.
            double q = Math.abs(p * Math.tan(Math.acos(lccCs.getPowerFactor()))); // A LCC station always consumes reactive power.
            lccCs.getTerminal()
                    .setP(p)
                    .setQ(q);
        }
    }
}
