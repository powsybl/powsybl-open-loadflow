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
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BusResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractLfBus extends AbstractElement implements LfBus {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractLfBus.class);

    private static final double Q_DISPATCH_EPSILON = 1e-3;

    protected boolean slack = false;

    protected double v;

    protected Evaluable calculatedV = NAN;

    protected double angle;

    protected double calculatedQ = Double.NaN;

    private boolean hasGeneratorsWithSlope;

    protected boolean voltageControlEnabled = false;

    protected int voltageControlSwitchOffCount = 0;

    protected double loadTargetP = 0;

    protected double initialLoadTargetP = 0;

    protected double loadTargetQ = 0;

    protected double generationTargetQ = 0;

    protected final List<LfGenerator> generators = new ArrayList<>();

    protected LfShunt shunt;

    protected LfShunt controllerShunt;

    protected final LfLoads lfLoads = new LfLoads();

    protected boolean ensurePowerFactorConstantByLoad = false;

    protected final List<LccConverterStation> lccCss = new ArrayList<>();

    protected final List<LfBranch> branches = new ArrayList<>();

    private VoltageControl voltageControl;

    private ReactivePowerControl reactivePowerControl;

    protected TransformerVoltageControl transformerVoltageControl;

    protected ShuntVoltageControl shuntVoltageControl;

    protected Evaluable p = NAN;

    protected Evaluable q = NAN;

    protected double remoteVoltageControlReactivePercent = Double.NaN;

    protected AbstractLfBus(LfNetwork network, double v, double angle) {
        super(network);
        this.v = v;
        this.angle = angle;
    }

    @Override
    public ElementType getType() {
        return ElementType.BUS;
    }

    @Override
    public boolean isSlack() {
        network.updateSlack();
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
    public boolean hasVoltageControllerCapability() {
        return voltageControl != null && voltageControl.getControllerBuses().contains(this);
    }

    @Override
    public Optional<VoltageControl> getVoltageControl() {
        return Optional.ofNullable(voltageControl);
    }

    public void removeVoltageControl() {
        this.voltageControl = null;
    }

    @Override
    public void setVoltageControl(VoltageControl voltageControl) {
        this.voltageControl = Objects.requireNonNull(voltageControl);
        if (hasVoltageControllerCapability()) {
            this.voltageControlEnabled = true;
        } else if (!isVoltageControlled()) {
            throw new PowsyblException("Setting inconsistent voltage control to bus " + getId());
        }
    }

    @Override
    public Optional<ReactivePowerControl> getReactivePowerControl() {
        return Optional.ofNullable(reactivePowerControl);
    }

    @Override
    public void setReactivePowerControl(ReactivePowerControl reactivePowerControl) {
        this.reactivePowerControl = Objects.requireNonNull(reactivePowerControl);
    }

    @Override
    public boolean isVoltageControlled() {
        return voltageControl != null && voltageControl.getControlledBus() == this;
    }

    @Override
    public List<LfGenerator> getGeneratorsControllingVoltageWithSlope() {
        return generators.stream().filter(gen -> gen.hasVoltageControl() && gen.getSlope() != 0).collect(Collectors.toList());
    }

    @Override
    public boolean hasGeneratorsWithSlope() {
        return hasGeneratorsWithSlope;
    }

    @Override
    public void removeGeneratorSlopes() {
        hasGeneratorsWithSlope = false;
        generators.forEach(g -> g.setSlope(0));
    }

    @Override
    public boolean isVoltageControlEnabled() {
        return voltageControlEnabled;
    }

    @Override
    public void setVoltageControlEnabled(boolean voltageControlEnabled) {
        if (this.voltageControlEnabled != voltageControlEnabled) {
            if (this.voltageControlEnabled) {
                voltageControlSwitchOffCount++;
            }
            this.voltageControlEnabled = voltageControlEnabled;
            for (LfNetworkListener listener : network.getListeners()) {
                listener.onVoltageControlChange(this, voltageControlEnabled);
            }
        }
    }

    @Override
    public int getVoltageControlSwitchOffCount() {
        return voltageControlSwitchOffCount;
    }

    @Override
    public void setVoltageControlSwitchOffCount(int voltageControlSwitchOffCount) {
        this.voltageControlSwitchOffCount = voltageControlSwitchOffCount;
    }

    void addLoad(Load load, boolean distributedOnConformLoad) {
        double p0 = load.getP0();
        loadTargetP += p0;
        initialLoadTargetP += p0;
        loadTargetQ += load.getQ0();
        if (p0 < 0) {
            ensurePowerFactorConstantByLoad = true;
        }
        lfLoads.add(load, distributedOnConformLoad);
    }

    void addLccConverterStation(LccConverterStation lccCs) {
        // note that LCC converter station are out of the slack distribution.
        lccCss.add(lccCs);
        HvdcLine line = lccCs.getHvdcLine();
        double targetP = HvdcConverterStations.getLccConverterStationLoadTargetP(lccCs, line);
        loadTargetP += targetP;
        initialLoadTargetP += targetP;
        loadTargetQ += HvdcConverterStations.getLccConverterStationLoadTargetQ(lccCs, line);
    }

    protected void add(LfGenerator generator) {
        generators.add(generator);
        generator.setBus(this);
        if (!generator.hasVoltageControl() && !Double.isNaN(generator.getTargetQ())) {
            generationTargetQ += generator.getTargetQ() * PerUnit.SB;
        }
    }

    void addGenerator(Generator generator, boolean breakers, double plausibleActivePowerLimit, boolean reactiveLimits,
                      LfNetworkLoadingReport report) {
        add(LfGeneratorImpl.create(generator, breakers, plausibleActivePowerLimit, reactiveLimits, report));
    }

    void addStaticVarCompensator(StaticVarCompensator staticVarCompensator, boolean voltagePerReactivePowerControl,
                                 boolean breakers, boolean reactiveLimits, LfNetworkLoadingReport report) {
        if (staticVarCompensator.getRegulationMode() != StaticVarCompensator.RegulationMode.OFF) {
            LfStaticVarCompensatorImpl lfSvc = LfStaticVarCompensatorImpl.create(staticVarCompensator, this, voltagePerReactivePowerControl, breakers, reactiveLimits, report);
            add(lfSvc);
            if (lfSvc.getSlope() != 0) {
                hasGeneratorsWithSlope = true;
            }
        }
    }

    void addVscConverterStation(VscConverterStation vscCs, boolean breakers, boolean reactiveLimits, LfNetworkLoadingReport report) {
        add(LfVscConverterStationImpl.create(vscCs, breakers, reactiveLimits, report));
    }

    void addBattery(Battery generator, double plausibleActivePowerLimit, LfNetworkLoadingReport report) {
        add(LfBatteryImpl.create(generator, plausibleActivePowerLimit, report));
    }

    void setShuntCompensators(List<ShuntCompensator> shuntCompensators, boolean isShuntVoltageControl) {
        if (!isShuntVoltageControl && !shuntCompensators.isEmpty()) {
            shunt = new LfShuntImpl(shuntCompensators, network, this, false);
        } else {
            List<ShuntCompensator> controllerShuntCompensators = shuntCompensators.stream()
                    .filter(ShuntCompensator::isVoltageRegulatorOn)
                    .collect(Collectors.toList());
            if (!controllerShuntCompensators.isEmpty()) {
                controllerShunt = new LfShuntImpl(controllerShuntCompensators, network, this, true);
            }
            List<ShuntCompensator> fixedShuntCompensators = shuntCompensators.stream()
                    .filter(sc -> !sc.isVoltageRegulatorOn())
                    .collect(Collectors.toList());
            if (!fixedShuntCompensators.isEmpty()) {
                shunt = new LfShuntImpl(fixedShuntCompensators, network, this, false);
            }
        }
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
        double newGenerationTargetQ = generationTargetQ * PerUnit.SB;
        if (newGenerationTargetQ != this.generationTargetQ) {
            double oldGenerationTargetQ = this.generationTargetQ;
            this.generationTargetQ = newGenerationTargetQ;
            for (LfNetworkListener listener : network.getListeners()) {
                listener.onGenerationReactivePowerTargetChange(this, oldGenerationTargetQ, newGenerationTargetQ);
            }
        }
    }

    @Override
    public double getLoadTargetP() {
        return loadTargetP / PerUnit.SB;
    }

    @Override
    public double getInitialLoadTargetP() {
        return initialLoadTargetP / PerUnit.SB;
    }

    @Override
    public void setLoadTargetP(double loadTargetP) {
        double newLoadTargetP = loadTargetP * PerUnit.SB;
        if (newLoadTargetP != this.loadTargetP) {
            double oldLoadTargetP = this.loadTargetP;
            this.loadTargetP = newLoadTargetP;
            for (LfNetworkListener listener : network.getListeners()) {
                listener.onLoadActivePowerTargetChange(this, oldLoadTargetP, newLoadTargetP);
            }
        }
    }

    @Override
    public double getLoadTargetQ() {
        return loadTargetQ / PerUnit.SB;
    }

    @Override
    public void setLoadTargetQ(double loadTargetQ) {
        double newLoadTargetQ = loadTargetQ * PerUnit.SB;
        if (newLoadTargetQ != this.loadTargetQ) {
            double oldLoadTargetQ = this.loadTargetQ;
            this.loadTargetQ = newLoadTargetQ;
            for (LfNetworkListener listener : network.getListeners()) {
                listener.onLoadReactivePowerTargetChange(this, oldLoadTargetQ, newLoadTargetQ);
            }
        }
    }

    @Override
    public boolean ensurePowerFactorConstantByLoad() {
        return this.ensurePowerFactorConstantByLoad;
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
    public double getV() {
        return v / getNominalV();
    }

    @Override
    public void setV(double v) {
        this.v = v * getNominalV();
    }

    @Override
    public Evaluable getCalculatedV() {
        return calculatedV;
    }

    @Override
    public void setCalculatedV(Evaluable calculatedV) {
        this.calculatedV = Objects.requireNonNull(calculatedV);
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
    public Optional<LfShunt> getShunt() {
        return Optional.ofNullable(shunt);
    }

    @Override
    public Optional<LfShunt> getControllerShunt() {
        return Optional.ofNullable(controllerShunt);
    }

    @Override
    public List<LfGenerator> getGenerators() {
        return generators;
    }

    @Override
    public LfLoads getLfLoads() {
        return lfLoads;
    }

    @Override
    public List<LfBranch> getBranches() {
        return branches;
    }

    @Override
    public void addBranch(LfBranch branch) {
        branches.add(Objects.requireNonNull(branch));
    }

    private static double dispatchQ(List<LfGenerator> generatorsThatControlVoltage, boolean reactiveLimits, double qToDispatch) {
        double residueQ = 0;
        double calculatedQ = qToDispatch / generatorsThatControlVoltage.size();
        Iterator<LfGenerator> itG = generatorsThatControlVoltage.iterator();
        while (itG.hasNext()) {
            LfGenerator generator = itG.next();
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

    void updateGeneratorsState(double generationQ, boolean reactiveLimits) {
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
    public void updateState(boolean reactiveLimits, boolean writeSlackBus, boolean distributedOnConformLoad, boolean loadPowerFactorConstant) {
        // update generator reactive power
        updateGeneratorsState(voltageControlEnabled ? calculatedQ + loadTargetQ : generationTargetQ, reactiveLimits);

        // update load power
        lfLoads.updateState(getLoadTargetP() - getInitialLoadTargetP(), loadPowerFactorConstant);

        // update lcc converter station power
        for (LccConverterStation lccCs : lccCss) {
            HvdcLine line = lccCs.getHvdcLine();
            double pCs = HvdcConverterStations.getLccConverterStationLoadTargetP(lccCs, line); // A LCC station has active losses.
            double qCs = HvdcConverterStations.getLccConverterStationLoadTargetQ(lccCs, line); // A LCC station always consumes reactive power.
            lccCs.getTerminal()
                    .setP(pCs)
                    .setQ(qCs);
        }
    }

    @Override
    public Optional<TransformerVoltageControl> getTransformerVoltageControl() {
        return Optional.ofNullable(transformerVoltageControl);
    }

    @Override
    public boolean isTransformerVoltageControlled() {
        return transformerVoltageControl != null && transformerVoltageControl.getControlled() == this;
    }

    @Override
    public void setTransformerVoltageControl(TransformerVoltageControl transformerVoltageControl) {
        this.transformerVoltageControl = transformerVoltageControl;
    }

    @Override
    public Optional<ShuntVoltageControl> getShuntVoltageControl() {
        return Optional.ofNullable(shuntVoltageControl);
    }

    @Override
    public boolean isShuntVoltageControlled() {
        return shuntVoltageControl != null && shuntVoltageControl.getControlled() == this;
    }

    @Override
    public void setShuntVoltageControl(ShuntVoltageControl shuntVoltageControl) {
        this.shuntVoltageControl = shuntVoltageControl;
    }

    @Override
    public void setDisabled(boolean disabled) {
        super.setDisabled(disabled);
        if (shunt != null) {
            shunt.setDisabled(disabled);
        }
        if (controllerShunt != null) {
            controllerShunt.setDisabled(disabled);
        }
    }

    @Override
    public void setP(Evaluable p) {
        this.p = Objects.requireNonNull(p);
    }

    @Override
    public Evaluable getP() {
        return p;
    }

    @Override
    public void setQ(Evaluable q) {
        this.q = Objects.requireNonNull(q);
    }

    @Override
    public Evaluable getQ() {
        return q;
    }

    @Override
    public BusResults createBusResult() {
        return new BusResults(getVoltageLevelId(), getId(), v, getAngle());
    }

    @Override
    public Map<LfBus, List<LfBranch>> findNeighbors() {
        Map<LfBus, List<LfBranch>> neighbors = new LinkedHashMap<>(branches.size());
        for (LfBranch branch : branches) {
            if (branch.isConnectedAtBothSides()) {
                LfBus otherBus = branch.getBus1() == this ? branch.getBus2() : branch.getBus1();
                neighbors.computeIfAbsent(otherBus, k -> new ArrayList<>())
                        .add(branch);
            }
        }
        return neighbors;
    }

    @Override
    public double getRemoteVoltageControlReactivePercent() {
        return remoteVoltageControlReactivePercent;
    }

    @Override
    public void setRemoteVoltageControlReactivePercent(double remoteVoltageControlReactivePercent) {
        this.remoteVoltageControlReactivePercent = remoteVoltageControlReactivePercent;
    }
}
