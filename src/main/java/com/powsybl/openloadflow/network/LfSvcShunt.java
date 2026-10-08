/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.PerUnit;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shunt modeling the susceptance of the static var compensators of a bus: their standby automaton B0 and, when at a
 * reactive limit, their Bmin or Bmax so that their reactive power follows the voltage.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public final class LfSvcShunt extends AbstractLfShunt {

    private final LfBus bus;

    private double b;

    public LfSvcShunt(LfBus bus) {
        super(bus.getNetwork());
        this.bus = Objects.requireNonNull(bus);
    }

    @Override
    public LfSvcShunt copy(LfBus copyBus) {
        LfSvcShunt svcShunt = new LfSvcShunt(copyBus);
        svcShunt.b = b;
        svcShunt.setDisabled(disabled);
        return svcShunt;
    }

    private Stream<LfStaticVarCompensator> getSvcs() {
        return bus.getGenerators().stream()
                .filter(LfStaticVarCompensator.class::isInstance)
                .map(LfStaticVarCompensator.class::cast);
    }

    /**
     * Recomputes the susceptance from the enabled static var compensators of the bus.
     */
    public void update() {
        double zb = PerUnit.zb(bus.getNominalV());
        setB(getSvcs().filter(svc -> !svc.isDisabled())
                .mapToDouble(LfStaticVarCompensator::getB)
                .sum() * zb);
    }

    @Override
    public ElementType getType() {
        return ElementType.SHUNT_COMPENSATOR;
    }

    @Override
    public String getId() {
        return bus.getId() + "_svc_shunt";
    }

    @Override
    public List<String> getOriginalIds() {
        return getSvcs().map(LfGenerator::getOriginalId).toList();
    }

    @Override
    public double getB() {
        return b;
    }

    @Override
    public void setB(double b) {
        if (b != this.b) {
            this.b = b;
            for (LfNetworkListener listener : getNetwork().getListeners()) {
                listener.onShuntSusceptanceChange(this, b);
            }
        }
    }

    private static UnsupportedOperationException createUnsupportedForSvcShuntException() {
        return new UnsupportedOperationException("Unsupported for a SVC shunt");
    }

    @Override
    public double getG() {
        return 0;
    }

    @Override
    public void setG(double g) {
        throw createUnsupportedForSvcShuntException();
    }

    @Override
    public boolean hasVoltageControlCapability() {
        return false;
    }

    @Override
    public void setVoltageControlCapability(boolean voltageControlCapability) {
        throw createUnsupportedForSvcShuntException();
    }

    @Override
    public boolean isVoltageControlEnabled() {
        return false;
    }

    @Override
    public void setVoltageControlEnabled(boolean voltageControlEnabled) {
        throw createUnsupportedForSvcShuntException();
    }

    @Override
    public Optional<ShuntVoltageControl> getVoltageControl() {
        return Optional.empty();
    }

    @Override
    public void setVoltageControl(ShuntVoltageControl voltageControl) {
        throw createUnsupportedForSvcShuntException();
    }

    @Override
    public double dispatchB() {
        throw createUnsupportedForSvcShuntException();
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        // nothing to do
    }

    @Override
    public void reInit() {
        // nothing to do
    }

    @Override
    public List<Controller> getControllers() {
        return Collections.emptyList();
    }
}
