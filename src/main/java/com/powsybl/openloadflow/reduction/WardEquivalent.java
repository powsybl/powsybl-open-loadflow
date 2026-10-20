/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.adm.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.reduction.Reducer.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public class WardEquivalent {

    private static final Logger LOGGER = LoggerFactory.getLogger(WardEquivalent.class);

    //TODO return status?
    //TODO javadoc
    public void reduce(Network network, WardReductionParameters wardParameters) {
        // Create a LfNetwork
        LoadFlowParameters lfParameters = wardParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(lfParameters);
        AcLoadFlowParameters acLfParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, wardParameters.getMatrixFactory(), wardParameters.getConnectivityFactory());
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkLoaderImpl(), acLfParameters.getNetworkParameters());
        if (lfNetworks.size() > 1) {
            LOGGER.warn("More than one LfNetwork found, only the first one will be used");
        }
        LfNetwork lfNetwork = lfNetworks.getFirst();

        // Run an AC load flow and make sure the network is balanced
        AcLoadFlowContext acLfContext = new AcLoadFlowContext(lfNetwork, acLfParameters);
        AcLoadFlowResult acLfResult = new AcloadFlowEngine(acLfContext).run();
        if (!acLfResult.isSuccess()) {
            LOGGER.error("AC load flow failed. The network must be balanced to perform a reduction.");
            return;
        }

        // Compute internal, boundary and external zones
        ZoneDecomposition zones = new ZoneDecomposition(lfNetwork, wardParameters.getInternalVoltageLevelIds());

        // Create the ward admittance matrix
        WardAdmittance wardAdmittance = new WardAdmittance(lfNetwork, zones, wardParameters.getReductionType(), wardParameters.getMatrixFactory());

        // Reduce the network with equivalents
        List<Reducer.EquivalentBranch> equivalentBranches = findEquivalentBranches(zones.getBoundaryBuses(), wardAdmittance.getYeq().toDense());
        List<Reducer.EquivalentShunt> equivalentShunts = findEquivalentShunts(zones.getBoundaryBuses(), wardAdmittance.getYeq().toDense());
        List<Reducer.EquivalentInjection> equivalentInjections = findEquivalentInjections(zones.getBoundaryBuses(), wardAdmittance.getIeq().toDense());
        Reducer reducer = new Reducer(network, lfNetwork, zones, equivalentBranches, equivalentShunts, equivalentInjections);
        reducer.reduce();
    }

    private List<EquivalentBranch> findEquivalentBranches(List<LfBus> boundaryBuses, DenseMatrix yeq) {
        List<EquivalentBranch> equivalentBranches = new ArrayList<>();
        double zb;
        for (int i = 0; i < boundaryBuses.size(); i++) {
            for (int j = i + 1; j < boundaryBuses.size(); j++) {
                LfBus bus1 = boundaryBuses.get(i);
                LfBus bus2 = boundaryBuses.get(j);
                zb = bus1.getNominalV() * bus2.getNominalV() / PerUnit.SB;
                double g = -yeq.get(2 * i, 2 * j) / zb;
                double b = yeq.get(2 * i, 2 * j + 1) / zb;
                equivalentBranches.add(new EquivalentBranch(bus1, bus2, g, b));
            }
        }
        return equivalentBranches;
    }

    private List<EquivalentShunt> findEquivalentShunts(List<LfBus> boundaryBuses, DenseMatrix yeq) {
        List<EquivalentShunt> equivalentShunts = new ArrayList<>();
        double zb;
        for (int j = 0; j < boundaryBuses.size(); j++) {
            double g = 0.0;
            double b = 0.0;
            for (int i = 0; i < boundaryBuses.size(); i++) {
                g += yeq.get(2 * i, 2 * j);
                b += yeq.get(2 * i + 1, 2 * j);
            }
            LfBus bus = boundaryBuses.get(j);
            zb = PerUnit.zb(bus.getNominalV());
            g = g / zb;
            b = b / zb;
            equivalentShunts.add(new EquivalentShunt(boundaryBuses.get(j), g, b));
        }
        return equivalentShunts;
    }

    private List<EquivalentInjection> findEquivalentInjections(List<LfBus> boundaryBuses, DenseMatrix ieq) {
        List<EquivalentInjection> equivalentInjections = new ArrayList<>();
        for (int i = 0; i < boundaryBuses.size(); i++) {
            // P + j Q
            //   = S
            //   = U I*
            //   = (Ux + j Uy) (Ix - j Iy)
            //   = (Ux Ix + Uy Iy) + j (-Ux Iy + Uy Ix)
            LfBus bus = boundaryBuses.get(i);
            double ux = bus.getV() * Math.cos(bus.getAngle());
            double uy = bus.getV() * Math.sin(bus.getAngle());
            double ix = ieq.get(2 * i, 0);
            double iy = ieq.get(2 * i + 1, 0);
            double p = (ux * ix + uy * iy) * PerUnit.SB;
            double q = (-ux * iy + uy * ix) * PerUnit.SB;
            equivalentInjections.add(new EquivalentInjection(bus, p, q));
        }
        return equivalentInjections;
    }

}
