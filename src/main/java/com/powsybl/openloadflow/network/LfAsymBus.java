/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.openloadflow.network.extensions.AbcPhaseType;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.EvaluableConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
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

    public double getAngleN() {
        return angleN;
    }

    public double getAngleZ() {
        return angleZ;
    }

    public double getVn() {
        return vn;
    }

    public double getVz() {
        return vz;
    }

    public void setAngleZ(double angleZ) {
        this.angleZ = angleZ;
    }

    public void setAngleN(double angleN) {
        this.angleN = angleN;
    }

    public void setVz(double vz) {
        this.vz = vz;
    }

    public void setVn(double vn) {
        this.vn = vn;
    }

    public void setIxZ(Evaluable ixZ) {
        this.ixZ = ixZ;
    }

    public void setIxN(Evaluable ixN) {
        this.ixN = ixN;
    }

    public void setIyZ(Evaluable iyZ) {
        this.iyZ = iyZ;
    }

    public void setIyN(Evaluable iyN) {
        this.iyN = iyN;
    }

    public Evaluable getIxN() {
        return ixN;
    }

    public Evaluable getIxZ() {
        return ixZ;
    }

    public Evaluable getIyN() {
        return iyN;
    }

    public Evaluable getIyZ() {
        return iyZ;
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
        return Complex.ONE;
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

    public Complex getItarget(WindingConnectionType loadConnectionType, AbcPhaseType abcPhaseType) {
        Complex s1 = Complex.ZERO;
        Complex s2 = Complex.ZERO;

        AbcPhaseType abcPhaseType2;
        Complex v1;
        Complex v31;
        Complex v12;
        if (abcPhaseType == AbcPhaseType.A) {
            abcPhaseType2 = AbcPhaseType.C;
            v12 = getVab0();
            v31 = getVca0();
            v1 = getVa0();
        } else if (abcPhaseType == AbcPhaseType.B) {
            abcPhaseType2 = AbcPhaseType.A;
            v12 = getVbc0();
            v31 = getVab0();
            v1 = getVb0();
        } else {
            abcPhaseType2 = AbcPhaseType.B;
            v12 = getVca0();
            v31 = getVbc0();
            v1 = getVc0();
        }

        if (loadConnectionType == WindingConnectionType.Y || loadConnectionType == WindingConnectionType.Y_GROUNDED) {
            if (loadWye1 != null) {
                s1 = loadWye1.getS(abcPhaseType);
            }
            return (s1.multiply(v1.reciprocal())).conjugate();
        } else {
            // We suppose sA = sAB and sC = sCA
            if (loadDelta1 != null) {
                s1 = loadDelta1.getS(abcPhaseType);
                s2 = loadDelta1.getS(abcPhaseType2);
            }
            return (s1.multiply(v12.reciprocal()).add(s2.multiply(v31.reciprocal().multiply(-1.)))).conjugate();
        }
    }

    public Complex getIzeroTarget(WindingConnectionType loadConnectionType) {
        if (isFortescueRepresentation) {
            throw new IllegalStateException(LOAD_CONFIG_NOT_SUPPORTED + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            return getItarget(loadConnectionType, AbcPhaseType.A);
        } else if (hasPhaseB && hasPhaseC) {
            return getItarget(loadConnectionType, AbcPhaseType.B);
        } else if (hasPhaseA && hasPhaseC) {
            return getItarget(loadConnectionType, AbcPhaseType.A);
        } else if (hasPhaseA && hasPhaseB) {
            return getItarget(loadConnectionType, AbcPhaseType.A);
        } else if (hasPhaseC || hasPhaseB || hasPhaseA) {
            return Complex.ZERO;
        } else {
            throw new IllegalStateException("unknow abc target load config : " + bus.getId());
        }
    }

    public Complex getIpositiveTarget(WindingConnectionType loadConnectionType) {
        if (isFortescueRepresentation) {
            throw new IllegalStateException(LOAD_CONFIG_NOT_SUPPORTED + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            return getItarget(loadConnectionType, AbcPhaseType.B);
        } else if (hasPhaseB && hasPhaseC) {
            return getItarget(loadConnectionType, AbcPhaseType.C);
        } else if (hasPhaseA && hasPhaseC) {
            return getItarget(loadConnectionType, AbcPhaseType.C);
        } else if (hasPhaseA && hasPhaseB) {
            return getItarget(loadConnectionType, AbcPhaseType.B);
        } else if (hasPhaseC) {
            return getItarget(loadConnectionType, AbcPhaseType.C);
        } else if (hasPhaseB) {
            return getItarget(loadConnectionType, AbcPhaseType.B);
        } else if (hasPhaseA) {
            return getItarget(loadConnectionType, AbcPhaseType.A);
        } else {
            throw new IllegalStateException("unknow abc target load config : " + bus.getId());
        }
    }

    public Complex getInegativeTarget(WindingConnectionType loadConnectionType) {
        if (isFortescueRepresentation) {
            throw new IllegalStateException(LOAD_CONFIG_NOT_SUPPORTED + bus.getId());
        }
        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            return getItarget(loadConnectionType, AbcPhaseType.C);
        } else {
            return Complex.ZERO;
        }
    }
}
