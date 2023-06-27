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
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.openloadflow.network.extensions.AsymBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;
import com.powsybl.openloadflow.network.extensions.AsymBusLoadType;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.network.extensions.iidm.BusAsymmetrical;
import com.powsybl.openloadflow.network.extensions.iidm.BusVariableType;
import com.powsybl.openloadflow.network.extensions.iidm.LoadType;
import com.powsybl.openloadflow.network.extensions.iidm.LoadUnbalanced;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BusResult;

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

    private static void createAsymExt(Bus bus, LfBusImpl lfBus) {
        double totalDeltaPa = 0;
        double totalDeltaQa = 0;
        double totalDeltaPb = 0;
        double totalDeltaQb = 0;
        double totalDeltaPc = 0;
        double totalDeltaQc = 0;
        boolean isLoadAtBus = false;
        boolean isWyeLoad = false;
        boolean isDeltaLoad = false;
        AsymBusLoadType loadType = AsymBusLoadType.CONSTANT_POWER;
        LegConnectionType loadConnectionType;
        for (Load load : bus.getLoads()) {
            var extension = load.getExtension(LoadUnbalanced.class);
            if (extension != null) {
                if (extension.getConnectionType() == WindingConnectionType.Y) {
                    throw new IllegalStateException("non-grounded Y load not supported at Bus : " + bus.getId());
                } else if (extension.getConnectionType() == WindingConnectionType.DELTA) {
                    isDeltaLoad = true;
                    isLoadAtBus = true;
                } else if (extension.getConnectionType() == WindingConnectionType.Y_GROUNDED) {
                    isWyeLoad = true;
                    isLoadAtBus = true;
                } else {
                    throw new IllegalStateException("unknown load type at Bus : " + bus.getId());
                }
                totalDeltaPa += extension.getDeltaPa() / PerUnit.SB;
                totalDeltaQa += extension.getDeltaQa() / PerUnit.SB;
                totalDeltaPb += extension.getDeltaPb() / PerUnit.SB;
                totalDeltaQb += extension.getDeltaQb() / PerUnit.SB;
                totalDeltaPc += extension.getDeltaPc() / PerUnit.SB;
                totalDeltaQc += extension.getDeltaQc() / PerUnit.SB;
                if (extension.getLoadType() == LoadType.CONSTANT_CURRENT) {
                    loadType = AsymBusLoadType.CONSTANT_CURRENT;
                } else if (extension.getLoadType() == LoadType.CONSTANT_IMPEDANCE) {
                    loadType = AsymBusLoadType.CONSTANT_IMPEDANCE;
                }
            }
        }
        if (isDeltaLoad && isWyeLoad) {
            throw new IllegalStateException("load type Yg and Delta connected at same Bus : " + bus.getId() + " not supported, choose only one type of load");
        } else if (isDeltaLoad) {
            loadConnectionType = LegConnectionType.DELTA;
        } else if (isWyeLoad) {
            loadConnectionType = LegConnectionType.Y_GROUNDED;
        } else {
            if (isLoadAtBus) {
                throw new IllegalStateException("unknown load type at Bus : " + bus.getId());
            }
            loadConnectionType = LegConnectionType.Y_GROUNDED;
        }

        boolean hasPhaseA = true;
        boolean hasPhaseB = true;
        boolean hasPhaseC = true;
        boolean isFortescueRep = true;
        boolean isPositiveSequenceAsCurrent = false;

        // TODO: adapt for better efficiency
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
            }
            break;
        }

        AsymBus asymBus = new AsymBus(lfBus, asymBusVariableType, hasPhaseA, hasPhaseB, hasPhaseC, loadConnectionType, totalDeltaPa, totalDeltaQa, totalDeltaPb, totalDeltaQb, totalDeltaPc, totalDeltaQc, isFortescueRep, isPositiveSequenceAsCurrent, loadType);
        lfBus.setProperty(AsymBus.PROPERTY_ASYMMETRICAL, asymBus);
    }

    public static LfBusImpl create(Bus bus, LfNetwork network, LfNetworkParameters parameters, boolean participating) {
        Objects.requireNonNull(bus);
        Objects.requireNonNull(parameters);
        var lfBus = new LfBusImpl(bus, network, bus.getV(), Math.toRadians(bus.getAngle()), parameters, participating);
        if (parameters.isAsymmetrical()) {
            createAsymExt(bus, lfBus);
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
        AsymBus asymBus = (AsymBus) this.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus != null) {
            return getGenerationTargetP();
            // we use the detection of the asymmetry extension at bus to check if we are in asymmetrical calculation
            // in this case, load target is set to zero and the constant-balanced load model (in 3 phased representation) is replaced by a model depending on v1, v2, v0 (equivalent fortescue representation)
        }
        return getGenerationTargetP() - getLoadTargetP();
    }

    @Override
    public double getTargetQ() {
        AsymBus asymBus = (AsymBus) this.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus != null) {
            return getGenerationTargetQ();
            // we use the detection of the asymmetry extension at bus to check if we are in asymmetrical calculation
            // in this case, load target is set to zero and the constant power load model (in 3 phased representation) is replaced by a model depending on v1, v2, v0 (equivalent fortescue representation)
        }
        return getGenerationTargetQ() - getLoadTargetQ();
    }
}
