/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.util.SV;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.*;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public class LfAreaImpl extends AbstractPropertyBag implements LfArea {

    private final LfNetwork network;
    private final Ref<Area> areaRef;

    private double interchangeTarget;

    private final Set<LfBus> buses;

    private final Set<Boundary> boundaries;

    protected LfAreaImpl(Area area, Set<LfBus> buses, Set<Boundary> boundaries, LfNetwork network, LfNetworkParameters parameters) {
        this.network = network;
        this.areaRef = Ref.create(area, parameters.isCacheEnabled());
        this.interchangeTarget = area.getInterchangeTarget().orElse(0.0) / PerUnit.SB;
        this.buses = buses;
        this.boundaries = boundaries;
    }

    public static LfAreaImpl create(Area area, Set<LfBus> buses, Set<Boundary> boundaries, LfNetwork network, LfNetworkParameters parameters) {
        LfAreaImpl lfArea = new LfAreaImpl(area, buses, boundaries, network, parameters);
        lfArea.getBuses().forEach(bus -> bus.setArea(lfArea));
        return lfArea;
    }

    private Area getArea() {
        return areaRef.get();
    }

    @Override
    public String getId() {
        return getArea().getId();
    }

    @Override
    public double getInterchangeTarget() {
        return interchangeTarget;
    }

    @Override
    public void setInterchangeTarget(double interchangeTarget) {
        this.interchangeTarget = interchangeTarget;
    }

    @Override
    public double getInterchange() {
        return boundaries.stream().mapToDouble(Boundary::getP).sum();
    }

    @Override
    public Set<LfBus> getBuses() {
        return buses;
    }

    @Override
    public Set<Boundary> getBoundaries() {
        return boundaries;
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

    public static class BoundaryImpl implements Boundary {
        private final LfBranch branch;
        private final TwoSides side;

        public BoundaryImpl(LfBranch branch, TwoSides side) {
            this.branch = Objects.requireNonNull(branch);
            this.side = Objects.requireNonNull(side);
        }

        @Override
        public LfBranch getBranch() {
            return branch;
        }

        @Override
        public double getP() {
            if (branch.isDisabled()) {
                return 0.0;
            }
            if (branch instanceof LfTieLineBranch lfTieLineBranch) {
                if (side == TwoSides.ONE) {
                    DanglingLine danglingLine1 = lfTieLineBranch.getHalf1();
                    double nominalV1 = danglingLine1.getTerminal().getVoltageLevel().getNominalV();
                    return new SV(lfTieLineBranch.getP1().eval() * PerUnit.SB, lfTieLineBranch.getQ1().eval() * PerUnit.SB, lfTieLineBranch.getV1() * nominalV1, Math.toDegrees(lfTieLineBranch.getAngle1()), side)
                            .otherSideP(danglingLine1, true) / PerUnit.SB;
                } else if (side == TwoSides.TWO) {
                    DanglingLine danglingLine = lfTieLineBranch.getHalf2();
                    double nominalV2 = danglingLine.getTerminal().getVoltageLevel().getNominalV();
                    return new SV(lfTieLineBranch.getP2().eval() * PerUnit.SB, lfTieLineBranch.getQ2().eval() * PerUnit.SB, lfTieLineBranch.getV2() * nominalV2, Math.toDegrees(lfTieLineBranch.getAngle2()), side)
                            .otherSideP(danglingLine, true) / PerUnit.SB;
                }
            }
            return switch (side) {
                case ONE -> branch.getP1().eval();
                case TWO -> branch.getP2().eval();
            };
        }
    }
}
