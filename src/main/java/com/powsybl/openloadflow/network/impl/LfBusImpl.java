/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.extensions.LoadAsymmetrical;
import com.powsybl.iidm.network.extensions.LoadConnectionType;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.extensions.AsymBusLoadType;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.network.extensions.iidm.BusAsymmetrical;
import com.powsybl.openloadflow.network.extensions.iidm.BusVariableType;
import com.powsybl.openloadflow.network.extensions.iidm.LoadAsymmetrical2;
import com.powsybl.openloadflow.network.extensions.iidm.LoadType;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BusResult;
import org.apache.commons.math3.complex.Complex;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfBusImpl extends AbstractLfBus {

    private final Ref<Bus> busRef;

    private final double nominalV;

    private final double lowVoltageLimit;

    private final double highVoltageLimit;

    private final boolean participating;

    private final boolean breakers;

    private final Country country;

    protected LfBusImpl(Bus bus, LfNetwork network, double v, double angle, LfNetworkParameters parameters,
                        boolean participating) {
        super(network, v, angle, parameters.isDistributedOnConformLoad());
        this.busRef = Ref.create(bus, parameters.isCacheEnabled());
        nominalV = bus.getVoltageLevel().getNominalV();
        lowVoltageLimit = bus.getVoltageLevel().getLowVoltageLimit();
        highVoltageLimit = bus.getVoltageLevel().getHighVoltageLimit();
        this.participating = participating;
        this.breakers = parameters.isBreakers();
        country = bus.getVoltageLevel().getSubstation().flatMap(Substation::getCountry).orElse(null);
    }

    private static void createAsym(Bus bus, LfBusImpl lfBus) {
        LfAsymLoad loadDelta0 = null;
        LfAsymLoad loadDelta1 = null;
        LfAsymLoad loadDelta2 = null;
        LfAsymLoad loadWye0 = null;
        LfAsymLoad loadWye1 = null;
        LfAsymLoad loadWye2 = null;

        for (Load load : bus.getLoads()) {
            var extension = load.getExtension(LoadAsymmetrical.class);
            var extension2 = load.getExtension(LoadAsymmetrical2.class);
            if (extension != null && extension2 != null) {

                String unknownLoad = "unknown load type at Bus : ";
                if (extension.getConnectionType() == LoadConnectionType.DELTA) {
                    if (extension2.getLoadType() == LoadType.CONSTANT_POWER) {
                        loadDelta0 = addSabcToLoad(lfBus, loadDelta0, extension, AsymBusLoadType.CONSTANT_POWER, LegConnectionType.DELTA);
                    } else if (extension2.getLoadType() == LoadType.CONSTANT_CURRENT) {
                        loadDelta1 = addSabcToLoad(lfBus, loadDelta1, extension, AsymBusLoadType.CONSTANT_CURRENT, LegConnectionType.DELTA);
                    } else if (extension2.getLoadType() == LoadType.CONSTANT_IMPEDANCE) {
                        loadDelta2 = addSabcToLoad(lfBus, loadDelta2, extension, AsymBusLoadType.CONSTANT_IMPEDANCE, LegConnectionType.DELTA);
                    } else {
                        throw new IllegalStateException(unknownLoad + bus.getId());
                    }
                } else if (extension.getConnectionType() == LoadConnectionType.Y) {
                    if (extension2.getLoadType() == LoadType.CONSTANT_POWER) {
                        loadWye0 = addSabcToLoad(lfBus, loadWye0, extension, AsymBusLoadType.CONSTANT_POWER, LegConnectionType.Y_GROUNDED);
                    } else if (extension2.getLoadType() == LoadType.CONSTANT_CURRENT) {
                        loadWye1 = addSabcToLoad(lfBus, loadWye1, extension, AsymBusLoadType.CONSTANT_CURRENT, LegConnectionType.Y_GROUNDED);
                    } else if (extension2.getLoadType() == LoadType.CONSTANT_IMPEDANCE) {
                        loadWye2 = addSabcToLoad(lfBus, loadWye2, extension, AsymBusLoadType.CONSTANT_IMPEDANCE, LegConnectionType.Y_GROUNDED);
                    } else {
                        throw new IllegalStateException(unknownLoad + bus.getId());
                    }
                } else {
                    throw new IllegalStateException(unknownLoad + bus.getId());
                }

            } else if (Math.abs(load.getP0()) > 0.000001 && Math.abs(load.getQ0()) > 0.000001) {
                loadWye0 = new LfAsymLoad(lfBus, AsymBusLoadType.CONSTANT_POWER, LegConnectionType.Y_GROUNDED, new Complex(0., 0.),
                        new Complex(0., 0.), new Complex(0., 0.));
            }
        }

        boolean hasPhaseA = true;
        boolean hasPhaseB = true;
        boolean hasPhaseC = true;
        boolean isFortescueRep = true;
        boolean isPositiveSequenceAsCurrent = false;

        AsymBusVariableType asymBusVariableType = AsymBusVariableType.WYE;
        for (Bus busi : bus.getVoltageLevel().getBusBreakerView().getBuses()) {
            var extensionBus = busi.getExtension(BusAsymmetrical.class);
            if (extensionBus != null) {
                isFortescueRep = extensionBus.isFortescueRepresentation();
                isPositiveSequenceAsCurrent = extensionBus.isPositiveSequenceAsCurrent();
                if (extensionBus.getBusVariableType() == BusVariableType.DELTA) {
                    asymBusVariableType = AsymBusVariableType.DELTA;
                    if (!extensionBus.isHasPhaseA() || !extensionBus.isHasPhaseB() || !extensionBus.isHasPhaseC()) {
                        throw new IllegalStateException("Bus with both missing phases and delta variables not yet handled for Bus : " + bus.getId());
                    }
                    if (!isFortescueRep) {
                        throw new IllegalStateException("Bus with both missing phases and 3 phase representation not yet handled for Bus : " + bus.getId());
                    }
                } else {
                    if (!extensionBus.isHasPhaseA()) {
                        hasPhaseA = false;
                        isFortescueRep = false;
                    }
                    if (!extensionBus.isHasPhaseB()) {
                        hasPhaseB = false;
                        isFortescueRep = false;
                    }
                    if (!extensionBus.isHasPhaseC()) {
                        hasPhaseC = false;
                        isFortescueRep = false;
                    }
                }
                break;
            }
        }

        lfBus.setAsym(new LfAsymBus(asymBusVariableType, hasPhaseA, hasPhaseB, hasPhaseC,
                isFortescueRep, isPositiveSequenceAsCurrent,
                loadDelta0, loadDelta1, loadDelta2, loadWye0, loadWye1, loadWye2));
    }

    public static LfAsymLoad addSabcToLoad(LfBus lfBus, LfAsymLoad asymLoad, LoadAsymmetrical extension, AsymBusLoadType asymBusLoadType, LegConnectionType legConnectionType) {
        LfAsymLoad asymLoadNew;
        if (asymLoad == null) {
            asymLoadNew = new LfAsymLoad(lfBus, asymBusLoadType, legConnectionType,
                    new Complex(extension.getDeltaPa() / PerUnit.SB, extension.getDeltaQa() / PerUnit.SB),
                    new Complex(extension.getDeltaPb() / PerUnit.SB, extension.getDeltaQb() / PerUnit.SB),
                    new Complex(extension.getDeltaPc() / PerUnit.SB, extension.getDeltaQc() / PerUnit.SB));
        } else {
            asymLoad.addSabc(new Complex(extension.getDeltaPa() / PerUnit.SB, extension.getDeltaQa() / PerUnit.SB),
                    new Complex(extension.getDeltaPb() / PerUnit.SB, extension.getDeltaQb() / PerUnit.SB),
                    new Complex(extension.getDeltaPc() / PerUnit.SB, extension.getDeltaQc() / PerUnit.SB));
            asymLoadNew = asymLoad;
        }

        return asymLoadNew;
    }

    public static LfBusImpl create(Bus bus, LfNetwork network, LfNetworkParameters parameters, boolean participating) {
        Objects.requireNonNull(bus);
        Objects.requireNonNull(parameters);
        var lfBus = new LfBusImpl(bus, network, bus.getV(), Math.toRadians(bus.getAngle()), parameters, participating);
        if (parameters.isAsymmetrical()) {
            createAsym(bus, lfBus);
        }
        return lfBus;
    }

    private Bus getBus() {
        return busRef.get();
    }

    @Override
    public String getId() {
        return getBus().getId();
    }

    @Override
    public String getVoltageLevelId() {
        return getBus().getVoltageLevel().getId();
    }

    @Override
    public boolean isFictitious() {
        return false;
    }

    @Override
    public double getNominalV() {
        return nominalV;
    }

    @Override
    public double getLowVoltageLimit() {
        return lowVoltageLimit / nominalV;
    }

    @Override
    public double getHighVoltageLimit() {
        return highVoltageLimit / nominalV;
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var bus = getBus();
        bus.setV(Math.max(v, 0.0)).setAngle(Math.toDegrees(angle));

        // update slack bus
        if (slack && parameters.isWriteSlackBus()) {
            SlackTerminal.attach(bus);
        }

        super.updateState(parameters);
    }

    @Override
    public boolean isParticipating() {
        return participating;
    }

    @Override
    public List<BusResult> createBusResults() {
        var bus = getBus();
        if (breakers) {
            return List.of(new BusResult(getVoltageLevelId(), bus.getId(), v, Math.toDegrees(angle)));
        } else {
            return bus.getVoltageLevel().getBusBreakerView().getBusesFromBusViewBusId(bus.getId())
                    .stream().map(b -> new BusResult(getVoltageLevelId(), b.getId(), v, Math.toDegrees(angle))).collect(Collectors.toList());
        }
    }

    @Override
    public Optional<Country> getCountry() {
        return Optional.ofNullable(country);
    }

    @Override
    public double getTargetP() {
        LfAsymBus asymBus = this.getAsym();
        if (asymBus != null) {
            return getGenerationTargetP();
            // we use the detection of the asymmetry extension at bus to check if we are in asymmetrical calculation
            // in this case, load target is set to zero and the constant-balanced load model (in 3 phased representation) is replaced by a model depending on v1, v2, v0 (equivalent fortescue representation)
        }
        return getGenerationTargetP() - getLoadTargetP();
    }

    @Override
    public double getTargetQ() {
        LfAsymBus asymBus = this.getAsym();
        if (asymBus != null) {
            return getGenerationTargetQ();
            // we use the detection of the asymmetry extension at bus to check if we are in asymmetrical calculation
            // in this case, load target is set to zero and the constant power load model (in 3 phased representation) is replaced by a model depending on v1, v2, v0 (equivalent fortescue representation)
        }
        return getGenerationTargetQ() - getLoadTargetQ();
    }
}
