/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.network.extensions.AsymBusLoadType;
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

    private LfBus bus;

    private final AsymBusVariableType asymBusVariableType; // available variables at node delta = {vab, vbc, vca} and wye = {va, vb, vc}
    private final boolean isPositiveSequenceAsCurrent; // if true, the balance in the positive sequence is current and not power
    private final boolean isFortescueRepresentation; // true if Fortescue, false if three phase variables
    private final boolean hasPhaseA;
    private final boolean hasPhaseB;
    private final boolean hasPhaseC;

    private final LegConnectionType loadConnectionType; // how loads are connected between each other
    private final AsymBusLoadType loadType;
    private final double totalDeltaPa;
    private final double totalDeltaQa;
    private final double totalDeltaPb;
    private final double totalDeltaQb;
    private final double totalDeltaPc;
    private final double totalDeltaQc;

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

    public LfAsymBus(AsymBusVariableType asymBusVariableType, boolean hasPhaseA, Boolean hasPhaseB, boolean hasPhaseC, LegConnectionType loadConnectionType, double totalDeltaPa, double totalDeltaQa, double totalDeltaPb, double totalDeltaQb, double totalDeltaPc, double totalDeltaQc,
                     boolean isFortescueRepresentation, boolean isPositiveSequenceAsCurrent, AsymBusLoadType loadType) {
        // Load info
        this.loadType = loadType;
        this.loadConnectionType = loadConnectionType;
        this.totalDeltaPa = totalDeltaPa;
        this.totalDeltaQa = totalDeltaQa;
        this.totalDeltaPb = totalDeltaPb;
        this.totalDeltaQb = totalDeltaQb;
        this.totalDeltaPc = totalDeltaPc;
        this.totalDeltaQc = totalDeltaQc;

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

    public double getPa() {
        return bus.getLoadTargetP() + totalDeltaPa;
    }

    public double getPb() {
        return bus.getLoadTargetP() + totalDeltaPb;
    }

    public double getPc() {
        return bus.getLoadTargetP() + totalDeltaPc;
    }

    public double getQa() {
        return bus.getLoadTargetQ() + totalDeltaQa;
    }

    public double getQb() {
        return bus.getLoadTargetQ() + totalDeltaQb;
    }

    public double getQc() {
        return bus.getLoadTargetQ() + totalDeltaQc;
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

    public LegConnectionType getLoadConnectionType() {
        return loadConnectionType;
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

    public AsymBusLoadType getLoadType() {
        return loadType;
    }

    public int getNbExistingPhases() {
        int nb = 0;
        if (!hasPhaseA) {
            nb = nb + 1;
        }
        if (!hasPhaseB) {
            nb = nb + 1;
        }
        if (!hasPhaseC) {
            nb = nb + 1;
        }
        return nb;
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

    public Complex getSa() {
        if (!hasPhaseA) {
            return new Complex(0., 0.);
        }
        return new Complex(getPa(), getQa());
    }

    public Complex getSb() {
        if (!hasPhaseB) {
            return new Complex(0., 0.);
        }
        return new Complex(getPb(), getQb());
    }

    public Complex getSc() {
        if (!hasPhaseC) {
            return new Complex(0., 0.);
        }
        return new Complex(getPc(), getQc());
    }

    /*public static AsymBus getAsymBus(LfBus bus) {
        AsymBus asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        return asymBus;
    }*/

    public Complex getIaTarget() {
        Complex sA = new Complex(0., 0.);
        Complex sC = new Complex(0., 0.);
        if (loadType == AsymBusLoadType.CONSTANT_CURRENT) {
            sA = getSa();
            sC = getSc();
        }
        if (loadConnectionType == LegConnectionType.Y || loadConnectionType == LegConnectionType.Y_GROUNDED) {
            return (sA.multiply(getVa0().reciprocal())).conjugate();
        } else {
            // We suppose sA = sAB and sC = sCA
            return (sA.multiply(getVab0().reciprocal()).add(sC.multiply(getVca0().reciprocal().multiply(-1.)))).conjugate();
        }
    }

    public Complex getIbTarget() {
        Complex sA = new Complex(0., 0.);
        Complex sB = new Complex(0., 0.);
        if (loadType == AsymBusLoadType.CONSTANT_CURRENT) {
            sA = getSa();
            sB = getSb();
        }
        if (loadConnectionType == LegConnectionType.Y || loadConnectionType == LegConnectionType.Y_GROUNDED) {
            return (sB.multiply(getVb0().reciprocal())).conjugate();
        } else {
            return (sB.multiply(getVbc0().reciprocal()).add(sA.multiply(getVab0().reciprocal().multiply(-1.)))).conjugate();
        }
    }

    public Complex getIcTarget() {
        Complex sB = new Complex(0., 0.);
        Complex sC = new Complex(0., 0.);
        if (loadType == AsymBusLoadType.CONSTANT_CURRENT) {
            sB = getSb();
            sC = getSc();
        }
        if (loadConnectionType == LegConnectionType.Y || loadConnectionType == LegConnectionType.Y_GROUNDED) {
            return (sC.multiply(getVc0().reciprocal())).conjugate();
        } else {
            return (sC.multiply(getVca0().reciprocal()).add(sB.multiply(getVbc0().reciprocal().multiply(-1.)))).conjugate();
        }
    }

    public Complex getIzeroTarget() {
        if (isFortescueRepresentation) {
            return new Complex(0., 0.);
            //throw new IllegalStateException("constant current loads in Fortescue representation not yet handled at bus : " + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIaTarget();
        } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIbTarget();
        } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
            return getIaTarget();
        } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
            return getIaTarget();
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

    public Complex getIpositiveTarget() {
        if (isFortescueRepresentation) {
            return new Complex(0., 0.);
            //throw new IllegalStateException("constant current loads in Fortescue representation not yet handled at bus : " + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIbTarget();
        } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIcTarget();
        } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
            return getIcTarget();
        } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
            return getIbTarget();
        } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
            return getIcTarget();
        } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
            return getIbTarget();
        } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
            return getIaTarget();
        } else {
            throw new IllegalStateException("unknow abc target load config : " + bus.getId());
        }
    }

    public Complex getInegativeTarget() {
        if (isFortescueRepresentation) {
            return new Complex(0., 0.);
            //throw new IllegalStateException("constant current loads in Fortescue representation not yet handled at bus : " + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            return getIcTarget();
        } else {
            return new Complex(0., 0.);
        }
    }

    public boolean asymLoadExist() {
        double absAsymLoad = Math.abs(totalDeltaPa) + Math.abs(totalDeltaQa)
                + Math.abs(totalDeltaPb) + Math.abs(totalDeltaQb)
                + Math.abs(totalDeltaPc) + Math.abs(totalDeltaQc);
        if (absAsymLoad > 0.0000001) {
            return true;
        }
        return false;
    }
}
