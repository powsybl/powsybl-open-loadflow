/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.loadflow.simple.network.AbstractLfBus;
import com.powsybl.loadflow.simple.network.LfReactiveDiagram;
import com.powsybl.loadflow.simple.network.LfShunt;
import com.powsybl.loadflow.simple.network.PerUnit;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBusImpl extends AbstractLfBus {

    private final Bus bus;

    private final double nominalV;

    private boolean voltageControl = false;

    private double loadTargetP = 0;

    private double loadTargetQ = 0;

    private double initialGenerationTargetP = 0;

    private double generationTargetP = 0;

    private double initialGenerationTargetQ = 0;

    private double generationTargetQ = 0;

    private double minP = Double.NaN;

    private double maxP = Double.NaN;

    private double participationFactor = 0;

    private double targetV = Double.NaN;

    private int neighbors = 0;

    private final List<LfShunt> shunts = new ArrayList<>();

    private final Map<Generator, Double> participatingGenerators = new HashMap<>();

    private final List<Generator> generators = new ArrayList<>();

    private LfReactiveDiagram reactiveDiagram;

    public LfBusImpl(Bus bus, int num, double v, double angle) {
        super(num, v, angle);
        this.bus = bus;
        nominalV = bus.getVoltageLevel().getNominalV();
    }

    public static LfBusImpl create(Bus bus, int num) {
        Objects.requireNonNull(bus);
        return new LfBusImpl(bus, num, bus.getV(), bus.getAngle());
    }

    @Override
    public String getId() {
        return bus.getId();
    }

    @Override
    public boolean hasVoltageControl() {
        return voltageControl;
    }

    @Override
    public void setVoltageControl(boolean voltageControl) {
        this.voltageControl = voltageControl;
    }

    private void checkTargetV(double targetV) {
        if (!Double.isNaN(this.targetV) && this.targetV != targetV) {
            throw new PowsyblException("Multiple generators connected to same bus with different target voltage");
        }
    }

    private void setActivePowerLimits(double minP, double maxP) {
        if (Double.isNaN(this.minP)) {
            this.minP = minP;
        } else {
            this.minP += minP;
        }
        if (Double.isNaN(this.maxP)) {
            this.maxP = maxP;
        } else {
            this.maxP += maxP;
        }
    }

    void addLoad(Load load) {
        this.loadTargetP += load.getP0();
        this.loadTargetQ += load.getQ0();
    }

    void addBattery(Battery battery) {
        loadTargetP += battery.getP0();
        loadTargetQ += battery.getQ0();
        setActivePowerLimits(battery.getMinP(), battery.getMaxP());
    }

    void addGenerator(Generator generator) {
        generators.add(generator);

        generationTargetP += generator.getTargetP();
        initialGenerationTargetP = generationTargetP;
        if (generator.isVoltageRegulatorOn()) {
            checkTargetV(generator.getTargetV());
            targetV = generator.getTargetV();
            voltageControl = true;
        } else {
            generationTargetQ += generator.getTargetQ();
            initialGenerationTargetQ = generationTargetQ;
        }
        setActivePowerLimits(generator.getMinP(), generator.getMaxP());

        if (reactiveDiagram == null) {
            reactiveDiagram = new LfReactiveDiagramImpl(generator.getReactiveLimits());
        } else {
            reactiveDiagram = LfReactiveDiagram.merge(reactiveDiagram, new LfReactiveDiagramImpl(generator.getReactiveLimits()));
        }

        // get participation factor from extension
        if (Math.abs(generator.getTargetP()) > 0) {
            ActivePowerControl<Generator> activePowerControl = generator.getExtension(ActivePowerControl.class);
            if (activePowerControl != null && activePowerControl.isParticipate() && activePowerControl.getDroop() != 0) {
                double f = generator.getMaxP() / activePowerControl.getDroop();
                participationFactor += f;
                participatingGenerators.put(generator, f);
            }
        }
    }

    void addStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
        if (staticVarCompensator.getRegulationMode() == StaticVarCompensator.RegulationMode.VOLTAGE) {
            checkTargetV(staticVarCompensator.getVoltageSetPoint());
            targetV = staticVarCompensator.getVoltageSetPoint();
            voltageControl = true;
        } else if (staticVarCompensator.getRegulationMode() == StaticVarCompensator.RegulationMode.REACTIVE_POWER) {
            throw new UnsupportedOperationException("SVC with reactive power regulation not supported");
        }
    }

    void addVscConverterStation(VscConverterStation vscCs) {
        HvdcLine line = vscCs.getHvdcLine();
        double targetP = line.getConverterStation1() == vscCs && line.getConvertersMode() == HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER
                ? line.getActivePowerSetpoint()
                : -line.getActivePowerSetpoint();
        generationTargetP += targetP;
        initialGenerationTargetP = generationTargetP;
        if (vscCs.isVoltageRegulatorOn()) {
            checkTargetV(vscCs.getVoltageSetpoint());
            targetV = vscCs.getVoltageSetpoint();
            voltageControl = true;
        } else {
            generationTargetQ += vscCs.getReactivePowerSetpoint();
            initialGenerationTargetQ = generationTargetQ;
        }

        setActivePowerLimits(-line.getMaxP(), line.getMaxP());

        if (reactiveDiagram == null) {
            reactiveDiagram = new LfReactiveDiagramImpl(vscCs.getReactiveLimits());
        } else {
            reactiveDiagram = LfReactiveDiagram.merge(reactiveDiagram, new LfReactiveDiagramImpl(vscCs.getReactiveLimits()));
        }
    }

    void addShuntCompensator(ShuntCompensator sc) {
        shunts.add(new LfShuntImpl(sc));
    }

    @Override
    public double getGenerationTargetP() {
        return generationTargetP / PerUnit.SB;
    }

    @Override
    public void setGenerationTargetP(double generationTargetP) {
        this.generationTargetP = generationTargetP * PerUnit.SB;
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

    @Override
    public double getMinP() {
        return minP / PerUnit.SB;
    }

    @Override
    public double getMaxP() {
        return maxP / PerUnit.SB;
    }

    @Override
    public double getParticipationFactor() {
        return participationFactor;
    }

    @Override
    public double getTargetV() {
        return targetV / nominalV;
    }

    void addNeighbor() {
        neighbors++;
    }

    @Override
    public int getNeighbors() {
        return neighbors;
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
    public Optional<LfReactiveDiagram> getReactiveDiagram() {
        return Optional.ofNullable(reactiveDiagram);
    }

    @Override
    public void updateState() {
        bus.setV(v).setAngle(angle);

        // update generator active power proportionally to the participation factor
        if (generationTargetP != initialGenerationTargetP) {
            for (Map.Entry<Generator, Double> e : participatingGenerators.entrySet()) {
                Generator generator = e.getKey();
                double generatorParticipationFactor = e.getValue();
                double newTargetP = generator.getTargetP() + (generationTargetP - initialGenerationTargetP) * generatorParticipationFactor / participationFactor;
                generator.getTerminal().setP(-newTargetP);
            }
        }

        // update generator reactive power
        if (voltageControl) {
            for (Generator generator : generators) {
                generator.getTerminal().setQ(q / generators.size());
            }
        } else {
            if (generationTargetQ != initialGenerationTargetQ) {
                for (Generator generator : generators) {
                    generator.getTerminal().setQ(generationTargetQ / generators.size());
                }
            }
        }
    }
}
