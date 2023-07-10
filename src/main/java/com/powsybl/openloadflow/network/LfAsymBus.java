/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.LegConnectionType;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.EvaluableConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LfAsymBus {

    private static final String LOAD_CONFIG_NOT_SUPPORTED = "Load with constant current and fortescue representation not yet handled : ";

    private LfBus bus;

    private final AsymBusVariableType asymBusVariableType; // available variables at node delta = {vab, vbc, vca} and wye = {va, vb, vc}
    private final boolean isPositiveSequenceAsCurrent; // if true, the balance in the positive sequence is current and not power
    private final boolean isFortescueRepresentation; // true if Fortescue, false if three phase variables
    private final boolean hasPhaseA;
    private final boolean hasPhaseB;
    private final boolean hasPhaseC;

    // load can be expressed as S = S0 * (V/V0)^k
    private final LfAsymLoad loadDelta0; // k = 0 : Constant power Delta load S = S0 = P0+j.Q0
    private final LfAsymLoad loadDelta1; // k = 1 : Constant current Delta load S = S0 * (V/V0)
    private final LfAsymLoad loadDelta2; // k = 2 : Constant impedance Delta load S = S0 * (V/V0)^2
    private final LfAsymLoad loadWye0; // k = 0 : Constant power Wye load S = S0 = P0+j.Q0
    private final LfAsymLoad loadWye1; // k = 1 : Constant current Wye load S = S0 * (V/V0)
    private final LfAsymLoad loadWye2; // k = 2 : Constant impedance Wye load S = S0 * (V/V0)^2

    private double vz = 0;
    private double angleZ = 0;

    private double vn = 0;
    private double angleN = 0;

    private double bzEquiv = 0.; // equivalent shunt in zero and negative sequences induced by all equipment connected to the bus (generating units, loads modelled as shunts etc.)
    private double gzEquiv = 0.;
    private double bnEquiv = 0.;
    private double gnEquiv = 0.;

    private Evaluable ixZ = EvaluableConstants.NAN;
    private Evaluable iyZ = EvaluableConstants.NAN;

    private Evaluable ixN = EvaluableConstants.NAN;
    private Evaluable iyN = EvaluableConstants.NAN;

    public LfAsymBus(AsymBusVariableType asymBusVariableType, boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC,
                     boolean isFortescueRepresentation, boolean isPositiveSequenceAsCurrent,
                     LfAsymLoad loadDelta0, LfAsymLoad loadDelta1, LfAsymLoad loadDelta2, LfAsymLoad loadWye0, LfAsymLoad loadWye1, LfAsymLoad loadWye2) {
        // Load info
        this.loadDelta0 = loadDelta0;
        this.loadDelta1 = loadDelta1;
        this.loadDelta2 = loadDelta2;
        this.loadWye0 = loadWye0;
        this.loadWye1 = loadWye1;
        this.loadWye2 = loadWye2;

        // bus representation info
        this.asymBusVariableType = asymBusVariableType;
        this.hasPhaseA = hasPhaseA;
        this.hasPhaseB = hasPhaseB;
        this.hasPhaseC = hasPhaseC;
        this.isFortescueRepresentation = isFortescueRepresentation; // if one phase is missing, the representation must be 3 phase
        this.isPositiveSequenceAsCurrent = isPositiveSequenceAsCurrent; // true if the balance at bus is current and not Power by default in this load flow
    }

    public void setBus(LfBus bus) {
        this.bus = Objects.requireNonNull(bus);
    }

    public LfAsymLoad getLoadDelta0() {
        return loadDelta0;
    }

    public LfAsymLoad getLoadDelta1() {
        return loadDelta1;
    }

    public LfAsymLoad getLoadDelta2() {
        return loadDelta2;
    }

    public LfAsymLoad getLoadWye0() {
        return loadWye0;
    }

    public LfAsymLoad getLoadWye1() {
        return loadWye1;
    }

    public LfAsymLoad getLoadWye2() {
        return loadWye2;
    }

    public double getAngleZ() {
        return angleZ;
    }

    public void setAngleZ(double angleZ) {
        this.angleZ = angleZ;
    }

    public double getAngleN() {
        return angleN;
    }

    public void setAngleN(double angleN) {
        this.angleN = angleN;
    }

    public double getVz() {
        return vz;
    }

    public void setVz(double vz) {
        this.vz = vz;
    }

    public double getVn() {
        return vn;
    }

    public void setVn(double vn) {
        this.vn = vn;
    }

    public Evaluable getIxZ() {
        return ixZ;
    }

    public void setIxZ(Evaluable ixZ) {
        this.ixZ = ixZ;
    }

    public Evaluable getIxN() {
        return ixN;
    }

    public void setIxN(Evaluable ixN) {
        this.ixN = ixN;
    }

    public Evaluable getIyZ() {
        return iyZ;
    }

    public void setIyZ(Evaluable iyZ) {
        this.iyZ = iyZ;
    }

    public Evaluable getIyN() {
        return iyN;
    }

    public void setIyN(Evaluable iyN) {
        this.iyN = iyN;
    }

    public double getBnEquiv() {
        return bnEquiv;
    }

    public void setBnEquiv(double bnEquiv) {
        this.bnEquiv = bnEquiv;
    }

    public double getBzEquiv() {
        return bzEquiv;
    }

    public void setBzEquiv(double bzEquiv) {
        this.bzEquiv = bzEquiv;
    }

    public double getGzEquiv() {
        return gzEquiv;
    }

    public void setGzEquiv(double gzEquiv) {
        this.gzEquiv = gzEquiv;
    }

    public double getGnEquiv() {
        return gnEquiv;
    }

    public void setGnEquiv(double gnEquiv) {
        this.gnEquiv = gnEquiv;
    }

    public AsymBusVariableType getAsymBusVariableType() {
        return asymBusVariableType;
    }

    public boolean isHasPhaseC() {
        return hasPhaseC;
    }

    public boolean isHasPhaseB() {
        return hasPhaseB;
    }

    public boolean isHasPhaseA() {
        return hasPhaseA;
    }

    public boolean isFortescueRepresentation() {
        return isFortescueRepresentation;
    }

    public boolean isPositiveSequenceAsCurrent() {
        return isPositiveSequenceAsCurrent;
    }

    public int getNbMissingPhases() {
        return 3 - getNbExistingPhases();
    }

    public int getNbExistingPhases() {
        int nbPhases = 0;
        if (hasPhaseA) {
            nbPhases++;
        }
        if (hasPhaseB) {
            nbPhases++;
        }
        if (hasPhaseC) {
            nbPhases++;
        }
        return nbPhases;
    }

    // init ABC voltage values
    public static Complex getVa0() {
        return new Complex(1., 0.);
    }

    public static Complex getVb0() {
        return ComplexUtils.polar2Complex(1., -2. * Math.PI / 3.);
    }

    public static Complex getVc0() {
        return ComplexUtils.polar2Complex(1., 2. * Math.PI / 3.);
    }

    public static Complex getVab0() {
        return getVa0().add(getVb0().multiply(-1.));
    }

    public static Complex getVbc0() {
        return getVb0().add(getVc0().multiply(-1.));
    }

    public static Complex getVca0() {
        return getVc0().add(getVa0().multiply(-1.));
    }

    public Complex getIaTarget(LegConnectionType loadConnectionType) {
        Complex sA = new Complex(0., 0.);
        Complex sC = new Complex(0., 0.);
        if (loadConnectionType == LegConnectionType.Y || loadConnectionType == LegConnectionType.Y_GROUNDED) {
            if (loadWye1 != null) {
                sA = new Complex(loadWye1.getPa(), loadWye1.getQa());
            }
            return (sA.multiply(getVa0().reciprocal())).conjugate();
        } else {
            // We suppose sA = sAB and sC = sCA
            if (loadDelta1 != null) {
                sA = new Complex(loadDelta1.getPa(), loadDelta1.getQa());
                sC = new Complex(loadDelta1.getPc(), loadDelta1.getQc());
            }
            return (sA.multiply(getVab0().reciprocal()).add(sC.multiply(getVca0().reciprocal().multiply(-1.)))).conjugate();
        }
    }

    public Complex getIbTarget(LegConnectionType loadConnectionType) {
        Complex sA = new Complex(0., 0.);
        Complex sB = new Complex(0., 0.);
        if (loadConnectionType == LegConnectionType.Y || loadConnectionType == LegConnectionType.Y_GROUNDED) {
            if (loadWye1 != null) {
                sB = new Complex(loadWye1.getPb(), loadWye1.getQb());
            }
            return (sB.multiply(getVb0().reciprocal())).conjugate();
        } else {
            if (loadDelta1 != null) {
                sA = new Complex(loadDelta1.getPa(), loadDelta1.getQa());
                sB = new Complex(loadDelta1.getPb(), loadDelta1.getQb());
            }
            return (sB.multiply(getVbc0().reciprocal()).add(sA.multiply(getVab0().reciprocal().multiply(-1.)))).conjugate();
        }
    }

    public Complex getIcTarget(LegConnectionType loadConnectionType) {
        Complex sB = new Complex(0., 0.);
        Complex sC = new Complex(0., 0.);
        if (loadConnectionType == LegConnectionType.Y || loadConnectionType == LegConnectionType.Y_GROUNDED) {
            if (loadWye1 != null) {
                sC = new Complex(loadWye1.getPc(), loadWye1.getQc());
            }
            return (sC.multiply(getVc0().reciprocal())).conjugate();
        } else {
            if (loadDelta1 != null) {
                sC = new Complex(loadDelta1.getPc(), loadDelta1.getQc());
                sB = new Complex(loadDelta1.getPb(), loadDelta1.getQb());
            }
            return (sC.multiply(getVca0().reciprocal()).add(sB.multiply(getVbc0().reciprocal().multiply(-1.)))).conjugate();
        }
    }

    public Complex getIzeroTarget(LegConnectionType loadConnectionType) {
        if (isFortescueRepresentation) {
            throw new IllegalStateException(LOAD_CONFIG_NOT_SUPPORTED + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIaTarget(loadConnectionType);
        } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIbTarget(loadConnectionType);
        } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
            return getIaTarget(loadConnectionType);
        } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
            return getIaTarget(loadConnectionType);
        } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
            return new Complex(0., 0.);
        } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
            return new Complex(0., 0.);
        } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
            return new Complex(0., 0.);
        } else {
            throw new IllegalStateException("unknow abc target load config : " + bus.getId());
        }
    }

    public Complex getIpositiveTarget(LegConnectionType loadConnectionType) {
        if (isFortescueRepresentation) {
            throw new IllegalStateException(LOAD_CONFIG_NOT_SUPPORTED + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            System.out.println(" Ipositive Target = " + getIbTarget(loadConnectionType));
            return getIbTarget(loadConnectionType);
        } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIcTarget(loadConnectionType);
        } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
            return getIcTarget(loadConnectionType);
        } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
            return getIbTarget(loadConnectionType);
        } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
            return getIcTarget(loadConnectionType);
        } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
            return getIbTarget(loadConnectionType);
        } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
            return getIaTarget(loadConnectionType);
        } else {
            throw new IllegalStateException("unknow abc target load config : " + bus.getId());
        }
    }

    public Complex getInegativeTarget(LegConnectionType loadConnectionType) {
        if (isFortescueRepresentation) {
            throw new IllegalStateException(LOAD_CONFIG_NOT_SUPPORTED + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIcTarget(loadConnectionType);
        } else {
            return new Complex(0., 0.);
        }
    }
}
