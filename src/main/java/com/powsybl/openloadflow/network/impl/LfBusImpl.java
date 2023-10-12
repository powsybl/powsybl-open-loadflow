/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.LoadAsymmetrical;
import com.powsybl.iidm.network.extensions.LoadConnectionType;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.extensions.AsymBusLoadType;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.network.extensions.iidm.AsymmetricalBranchConnector;
import com.powsybl.openloadflow.network.extensions.iidm.BusVariableType;
import com.powsybl.openloadflow.network.extensions.iidm.LineAsymmetrical;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.results.BusResult;
import org.apache.commons.math3.complex.Complex;

import java.util.Collections;
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

    private final List<String> bbsIds;

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
        if (bus.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER) {
            bbsIds = bus.getConnectedTerminalStream()
                    .map(Terminal::getConnectable)
                    .filter(BusbarSection.class::isInstance)
                    .map(Connectable::getId)
                    .collect(Collectors.toList());
        } else {
            bbsIds = Collections.emptyList();
        }
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
            if (extension != null) {

                ZipLoadModel zipLoadModel = (ZipLoadModel) load.getModel().orElse(null);
                double c0p = 0.;
                double c0q = 0.;
                double c1p = 0.;
                double c1q = 0.;
                double c2p = 0.;
                double c2q = 0.;

                if (zipLoadModel == null) {
                    // if zipModel is null, we consider a constant power load
                    c0p = 1.;
                    c0q = 1.;
                } else {
                    c0p = zipLoadModel.getC0p();
                    c0q = zipLoadModel.getC0q();
                    c1p = zipLoadModel.getC1p();
                    c1q = zipLoadModel.getC1q();
                    c2p = zipLoadModel.getC2p();
                    c2q = zipLoadModel.getC2q();
                }

                double epsilon = 0.00000001;
                String unknownLoad = "unknown load type at Bus : ";
                if (extension.getConnectionType() == LoadConnectionType.DELTA) {
                    if (Math.abs(c0p) + Math.abs(c0q) > epsilon) {
                        loadDelta0 = addSabcToLoad(lfBus, loadDelta0, extension, AsymBusLoadType.CONSTANT_POWER, LegConnectionType.DELTA, c0p, c0q);
                    }
                    if (Math.abs(c1p) + Math.abs(c1q) > epsilon) {
                        loadDelta1 = addSabcToLoad(lfBus, loadDelta1, extension, AsymBusLoadType.CONSTANT_CURRENT, LegConnectionType.DELTA, c1p, c1q);
                    }
                    if (Math.abs(c2p) + Math.abs(c2q) > epsilon) {
                        loadDelta2 = addSabcToLoad(lfBus, loadDelta2, extension, AsymBusLoadType.CONSTANT_IMPEDANCE, LegConnectionType.DELTA, c2p, c2q);
                    }
                } else if (extension.getConnectionType() == LoadConnectionType.Y) {
                    if (Math.abs(c0p) + Math.abs(c0q) > epsilon) {
                        loadWye0 = addSabcToLoad(lfBus, loadWye0, extension, AsymBusLoadType.CONSTANT_POWER, LegConnectionType.Y_GROUNDED, c0p, c0q);
                    }
                    if (Math.abs(c1p) + Math.abs(c1q) > epsilon) {
                        loadWye1 = addSabcToLoad(lfBus, loadWye1, extension, AsymBusLoadType.CONSTANT_CURRENT, LegConnectionType.Y_GROUNDED, c1p, c1q);
                    }
                    if (Math.abs(c2p) + Math.abs(c2q) > epsilon) {
                        loadWye2 = addSabcToLoad(lfBus, loadWye2, extension, AsymBusLoadType.CONSTANT_IMPEDANCE, LegConnectionType.Y_GROUNDED, c2p, c2q);
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

            AsymmetricalBranchConnector connector = null;
            for (Line line : busi.getLines()) {
                var extension = line.getExtension(LineAsymmetrical.class);
                if (extension == null) {
                    continue;
                }
                // search for AsymmetricalBranchConnector attached to lines that will be used to set asymmetrical info to LfBus
                if (line.getTerminal1().getBusBreakerView().getBus() == busi) {
                    connector = extension.getAsymConnectorBus1();
                } else if (line.getTerminal2().getBusBreakerView().getBus() == busi) {
                    connector = extension.getAsymConnectorBus2();
                }
                if (connector != null) {
                    break;
                }
            }
            // TODO : if null, search in tfos

            if (connector != null) {
                isFortescueRep = connector.isFortescueRepresentation();
                isPositiveSequenceAsCurrent = connector.isPositiveSequenceAsCurrent();
                if (connector.getBusVariableType() == BusVariableType.DELTA) {
                    asymBusVariableType = AsymBusVariableType.DELTA;
                    if (!connector.isHasPhaseA() || !connector.isHasPhaseB() || !connector.isHasPhaseC()) {
                        throw new IllegalStateException("Bus with both missing phases and delta variables not yet handled for Bus : " + bus.getId());
                    }
                    if (!isFortescueRep) {
                        throw new IllegalStateException("Bus with both missing phases and 3 phase representation not yet handled for Bus : " + bus.getId());
                    }
                } else {
                    if (!connector.isHasPhaseA()) {
                        hasPhaseA = false;
                        isFortescueRep = false;
                    }
                    if (!connector.isHasPhaseB()) {
                        hasPhaseB = false;
                        isFortescueRep = false;
                    }
                    if (!connector.isHasPhaseC()) {
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

    public static LfAsymLoad addSabcToLoad(LfBus lfBus, LfAsymLoad asymLoad, LoadAsymmetrical extension, AsymBusLoadType asymBusLoadType, LegConnectionType legConnectionType, double cp, double cq) {
        LfAsymLoad asymLoadNew;
        if (asymLoad == null) {
            asymLoadNew = new LfAsymLoad(lfBus, asymBusLoadType, legConnectionType,
                    new Complex(extension.getDeltaPa() / PerUnit.SB * cp, extension.getDeltaQa() / PerUnit.SB * cq),
                    new Complex(extension.getDeltaPb() / PerUnit.SB * cp, extension.getDeltaQb() / PerUnit.SB * cq),
                    new Complex(extension.getDeltaPc() / PerUnit.SB * cp, extension.getDeltaQc() / PerUnit.SB * cq));
        } else {
            asymLoad.addSabc(new Complex(extension.getDeltaPa() / PerUnit.SB * cp, extension.getDeltaQa() / PerUnit.SB * cq),
                    new Complex(extension.getDeltaPb() / PerUnit.SB * cp, extension.getDeltaQb() / PerUnit.SB * cq),
                    new Complex(extension.getDeltaPc() / PerUnit.SB * cp, extension.getDeltaQc() / PerUnit.SB * cq));
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
            if (bbsIds.isEmpty()) {
                return List.of(new BusResult(getVoltageLevelId(), bus.getId(), v, Math.toDegrees(angle)));
            } else {
                return bbsIds.stream()
                        .map(bbsId -> new BusResult(getVoltageLevelId(), bbsId, v, Math.toDegrees(angle)))
                        .collect(Collectors.toList());
            }
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
