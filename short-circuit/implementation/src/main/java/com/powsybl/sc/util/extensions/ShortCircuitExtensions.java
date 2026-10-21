/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.iidm.network.extensions.FortescueConstants;
import com.powsybl.iidm.network.extensions.GeneratorFortescue;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuit;
import com.powsybl.iidm.network.extensions.LineFortescue;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.openloadflow.network.*;
import com.powsybl.sc.extensions.*;
import com.powsybl.sc.util.AdmittanceEquationSystem;
import org.apache.commons.math3.complex.Complex;

import java.util.List;
import java.util.Objects;

import static com.powsybl.sc.extensions.FortescueConstants.*;
import static com.powsybl.sc.extensions.ShortCircuitConstants.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class ShortCircuitExtensions {

    public static final String PROPERTY_SHORT_CIRCUIT = "ShortCircuit";
    public static final String PROPERTY_HOMOPOLAR_MODEL = "HomopolarModel";
    public static final String PROPERTY_HOMOPOLAR_MODEL_20HZ = "HomopolarModel20hz";
    public static final String PROPERTY_SHORT_CIRCUIT_NORM = "ShortCircuitNorm";

    private static final double SB = 100.;

    private static final double EPSILON = 0.00000001;

    private ShortCircuitExtensions() {
    }

    public static void add(Network network, List<LfNetwork> lfNetworks) {
        add(network, lfNetworks, new ShortCircuitNormExtensions());
    }

    public static void add(Network network, List<LfNetwork> lfNetworks, ShortCircuitNormExtensions shortCircuitNormExtensions) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(lfNetworks);
        for (LfNetwork lfNetwork : lfNetworks) {
            for (LfBus lfBus : lfNetwork.getBuses()) {
                for (LfGenerator lfGenerator : lfBus.getGenerators()) {
                    addGeneratorExtension(network, lfGenerator, shortCircuitNormExtensions);
                }
                addLoadExtension(network, lfBus);
            }

            for (LfBranch lfBranch : lfNetwork.getBranches()) {
                switch (lfBranch.getBranchType()) {
                    case LINE:
                        addLineExtension(network, lfBranch);
                        break;

                    case TRANSFO_2:
                        addTransfo2Extension(network, lfBranch, shortCircuitNormExtensions);
                        break;

                    case TRANSFO_3_LEG_1:
                    case TRANSFO_3_LEG_2:
                    case TRANSFO_3_LEG_3:
                        addTransfo3Extension(network, lfBranch, shortCircuitNormExtensions);
                        break;

                    case BOUNDARY_LINE:
                        // FIXME something to do?
                        break;

                    default:
                        break;
                }

                // build homopolar model
                HomopolarModel homopolarModel = HomopolarModel.build(lfBranch, AdmittanceEquationSystem.FrequencyType.FREQ_50_HZ);
                HomopolarModel homopolarModel20hz = HomopolarModel.build(lfBranch, AdmittanceEquationSystem.FrequencyType.FREQ_20_HZ);
                lfBranch.setProperty(PROPERTY_HOMOPOLAR_MODEL, homopolarModel);
                lfBranch.setProperty(PROPERTY_HOMOPOLAR_MODEL_20HZ, homopolarModel20hz);
            }
        }
    }

    private static void addTransfo3Extension(Network network, LfBranch lfBranch, ShortCircuitNormExtensions shortCircuitNormExtensions) {
        String t3wId = lfBranch.getOriginalIds().get(0);
        ThreeWindingsTransformer twt = network.getThreeWindingsTransformer(t3wId);

        double leg1Ro = twt.getLeg1().getR();
        double leg2Ro = twt.getLeg2().getR();
        double leg3Ro = twt.getLeg3().getR();
        double leg1Xo = twt.getLeg1().getX();
        double leg2Xo = twt.getLeg2().getX();
        double leg3Xo = twt.getLeg3().getX();
        boolean leg1FreeFluxes = FortescueConstants.DEFAULT_FREE_FLUXES;
        boolean leg2FreeFluxes = FortescueConstants.DEFAULT_FREE_FLUXES;
        boolean leg3FreeFluxes = FortescueConstants.DEFAULT_FREE_FLUXES;
        WindingConnectionType leg1ConnectionType = FortescueConstants.DEFAULT_LEG1_CONNECTION_TYPE;
        WindingConnectionType leg2ConnectionType = FortescueConstants.DEFAULT_LEG2_CONNECTION_TYPE;
        WindingConnectionType leg3ConnectionType = FortescueConstants.DEFAULT_LEG3_CONNECTION_TYPE;
        double kT1R = DEFAULT_COEFF_K;
        double kT1X = DEFAULT_COEFF_K;
        double kT2R = DEFAULT_COEFF_K;
        double kT2X = DEFAULT_COEFF_K;
        double kT3R = DEFAULT_COEFF_K;
        double kT3X = DEFAULT_COEFF_K;
        double kT1R0 = DEFAULT_COEFF_K;
        double kT1X0 = DEFAULT_COEFF_K;
        double kT2R0 = DEFAULT_COEFF_K;
        double kT2X0 = DEFAULT_COEFF_K;
        double kT3R0 = DEFAULT_COEFF_K;
        double kT3X0 = DEFAULT_COEFF_K;

        var extensions = twt.getExtension(ThreeWindingsTransformerFortescue.class);
        if (extensions != null) {

            leg1Ro = extensions.getLeg1().getRz();
            leg2Ro = extensions.getLeg2().getRz();
            leg3Ro = extensions.getLeg3().getRz();
            leg1Xo = extensions.getLeg1().getXz();
            leg2Xo = extensions.getLeg2().getXz();
            leg3Xo = extensions.getLeg3().getXz();

            leg1FreeFluxes = extensions.getLeg1().isFreeFluxes();
            leg2FreeFluxes = extensions.getLeg2().isFreeFluxes();
            leg3FreeFluxes = extensions.getLeg3().isFreeFluxes();
            leg1ConnectionType = extensions.getLeg1().getConnectionType();
            leg2ConnectionType = extensions.getLeg2().getConnectionType();
            leg3ConnectionType = extensions.getLeg3().getConnectionType();
        }

        ThreeWindingsTransformerNorm t3wExtensionNorm = shortCircuitNormExtensions.getNormExtension(twt);
        if (t3wExtensionNorm != null) {
            if (t3wExtensionNorm.isOverloadHomopolarCoefs()) {

                leg1Ro = twt.getLeg1().getR() * t3wExtensionNorm.getLeg1().getLegCoeffRoOverload();
                leg2Ro = twt.getLeg2().getR() * t3wExtensionNorm.getLeg2().getLegCoeffRoOverload();
                leg3Ro = twt.getLeg3().getR() * t3wExtensionNorm.getLeg3().getLegCoeffRoOverload();
                leg1Xo = twt.getLeg1().getX() * t3wExtensionNorm.getLeg1().getLegCoeffXoOverload();
                leg2Xo = twt.getLeg2().getX() * t3wExtensionNorm.getLeg2().getLegCoeffXoOverload();
                leg3Xo = twt.getLeg3().getX() * t3wExtensionNorm.getLeg3().getLegCoeffXoOverload();
            }

            kT1R = t3wExtensionNorm.getLeg1().getKtR();
            kT1X = t3wExtensionNorm.getLeg1().getKtX();
            kT2R = t3wExtensionNorm.getLeg2().getKtR();
            kT2X = t3wExtensionNorm.getLeg2().getKtX();
            kT3R = t3wExtensionNorm.getLeg3().getKtR();
            kT3X = t3wExtensionNorm.getLeg3().getKtX();
            kT1R0 = t3wExtensionNorm.getLeg1().getKtRo();
            kT1X0 = t3wExtensionNorm.getLeg1().getKtXo();
            kT2R0 = t3wExtensionNorm.getLeg2().getKtRo();
            kT2X0 = t3wExtensionNorm.getLeg2().getKtXo();
            kT3R0 = t3wExtensionNorm.getLeg3().getKtRo();
            kT3X0 = t3wExtensionNorm.getLeg3().getKtXo();
        }

        // building of pu base supposing that impedances are on star bus side (ratedU0)
        double zBase = twt.getRatedU0() * twt.getRatedU0() / SB;

        ScTransfo3W.Leg leg1 = new ScTransfo3W.Leg(leg1ConnectionType, new Complex(leg1Ro, leg1Xo).divide(zBase), leg1FreeFluxes);
        ScTransfo3W.Leg leg2 = new ScTransfo3W.Leg(leg2ConnectionType, new Complex(leg2Ro, leg2Xo).divide(zBase), leg2FreeFluxes);
        ScTransfo3W.Leg leg3 = new ScTransfo3W.Leg(leg3ConnectionType, new Complex(leg3Ro, leg3Xo).divide(zBase), leg3FreeFluxes);

        ScTransfo3wKt.Leg leg1kT = new ScTransfo3wKt.Leg(kT1R, kT1X, kT1R0, kT1X0);
        ScTransfo3wKt.Leg leg2kT = new ScTransfo3wKt.Leg(kT2R, kT2X, kT2R0, kT2X0);
        ScTransfo3wKt.Leg leg3kT = new ScTransfo3wKt.Leg(kT3R, kT3X, kT3R0, kT3X0);

        lfBranch.setProperty(PROPERTY_SHORT_CIRCUIT, new ScTransfo3W(leg1, leg2, leg3));
        // set in a separate extension because is does not depend only on iidm in input but also on the type of norm
        lfBranch.setProperty(PROPERTY_SHORT_CIRCUIT_NORM, new ScTransfo3wKt(leg1kT, leg2kT, leg3kT));
    }

    private static void addLineExtension(Network network, LfBranch lfBranch) {
        String lineId = lfBranch.getOriginalIds().getFirst();
        Line line = network.getLine(lineId);

        double vNom2 = line.getTerminal2().getVoltageLevel().getNominalV();
        double zBase = vNom2 * vNom2 / SB;
        Complex zo = new Complex(line.getR(), line.getX()).divide(zBase);
        LineFortescue extensions = line.getExtension(LineFortescue.class);
        if (extensions != null) {
            zo = new Complex(extensions.getRz(), extensions.getXz()).divide(zBase);
        }

        lfBranch.setProperty(PROPERTY_SHORT_CIRCUIT, new ScLine(zo));
    }

    private static void addTransfo2Extension(Network network, LfBranch lfBranch, ShortCircuitNormExtensions shortCircuitNormExtensions) {
        String t2wId = lfBranch.getOriginalIds().getFirst();
        TwoWindingsTransformer twt = network.getTwoWindingsTransformer(t2wId);

        // per unitizing of grounding Z for LfNetwork
        double u2Nom = twt.getTerminal2().getVoltageLevel().getNominalV();
        double zBase = u2Nom * u2Nom / SB;

        Complex zo = new Complex(DEFAULT_COEFF_RO * lfBranch.getPiModel().getR(), DEFAULT_COEFF_XO * lfBranch.getPiModel().getX());
        boolean freeFluxes = FortescueConstants.DEFAULT_FREE_FLUXES;
        WindingConnectionType leg1ConnectionType = FortescueConstants.DEFAULT_LEG1_CONNECTION_TYPE;
        WindingConnectionType leg2ConnectionType = FortescueConstants.DEFAULT_LEG2_CONNECTION_TYPE;
        double kT = DEFAULT_COEFF_K;
        Complex z1Ground = new Complex(0.);
        Complex z2Ground = new Complex(0.);
        var extensions = twt.getExtension(TwoWindingsTransformerFortescue.class);
        if (extensions != null) {
            zo = new Complex(extensions.getRz(), extensions.getXz()).divide(zBase);
            freeFluxes = extensions.isFreeFluxes();
            leg1ConnectionType = extensions.getConnectionType1();
            leg2ConnectionType = extensions.getConnectionType2();

            z1Ground = new Complex(extensions.getGroundingR1(), extensions.getGroundingX1()).divide(zBase);
            z2Ground = new Complex(extensions.getGroundingR2(), extensions.getGroundingX2()).divide(zBase);
        }

        TwoWindingsTransformerNorm t2wNormExtension = shortCircuitNormExtensions.getNormExtension(twt);
        if (t2wNormExtension != null) {
            kT = t2wNormExtension.getkNorm();
        }

        lfBranch.setProperty(PROPERTY_SHORT_CIRCUIT, new ScTransfo2W(leg1ConnectionType, leg2ConnectionType, zo, freeFluxes, z1Ground, z2Ground));
        lfBranch.setProperty(PROPERTY_SHORT_CIRCUIT_NORM, kT); // set in a separate extension because is does not depend only on iidm in input but also on the type of norm
    }

    private static void addGeneratorExtension(Network network, LfGenerator lfGenerator, ShortCircuitNormExtensions shortCircuitNormExtensions) {
        Generator generator = network.getGenerator(lfGenerator.getOriginalId());

        double transX = DEFAULT_TRANS_XD;
        double subtransX = DEFAULT_SUB_TRANS_XD;
        double stepUpTfoX = DEFAULT_STEP_UP_XD;

        GeneratorShortCircuit extension = generator.getExtension(GeneratorShortCircuit.class);
        if (extension != null) {
            transX = extension.getDirectTransX();
            subtransX = extension.getDirectSubtransX();
            stepUpTfoX = extension.getStepUpTransformerX();
        }

        double transRd = DEFAULT_TRANS_RD;
        double subTransRd = DEFAULT_SUB_TRANS_RD;
        boolean toGround = FortescueConstants.DEFAULT_GROUNDED;
        Complex zo = new Complex(subTransRd, subtransX);
        double kG = 1.0;

        GeneratorShortCircuit2 extensions2 = generator.getExtension(GeneratorShortCircuit2.class);
        if (extensions2 != null) {
            transRd = extensions2.getTransRd();
            subTransRd = extensions2.getSubTransRd();
        }

        GeneratorFortescue extensionFortescue = generator.getExtension(GeneratorFortescue.class);
        if (extensionFortescue != null) {
            toGround = extensionFortescue.isGrounded();
            zo = new Complex(extensionFortescue.getRz(), extensionFortescue.getXz());
        }

        GeneratorNorm extensionGenNorm = shortCircuitNormExtensions.getNormExtension(generator);
        if (extensionGenNorm != null) {
            kG = extensionGenNorm.getkG();
        }

        lfGenerator.setProperty(PROPERTY_SHORT_CIRCUIT, new ScGenerator(new Complex(transRd, transX),
                new Complex(0., stepUpTfoX), ScGenerator.MachineType.SYNCHRONOUS_GEN,
                new Complex(subTransRd, subtransX), toGround, new Complex(0.), zo));
        lfGenerator.setProperty(PROPERTY_SHORT_CIRCUIT_NORM, kG); // set in a separate extension because is does not depend only on iidm in input but also on the type of norm
    }

    private static void addLoadExtension(Network network, LfBus lfBus) {
        Complex yLoads = new Complex(0.);
        for (LfLoad lfLoad : lfBus.getLoads()) {
            for (String loadId : lfLoad.getOriginalIds()) {
                Load load = network.getLoad(loadId);
                double uNom = load.getTerminal().getVoltageLevel().getNominalV();
                LoadShortCircuit extension = load.getExtension(LoadShortCircuit.class);
                Complex zd = new Complex(0.);
                if (extension != null) {
                    zd = extension.getZeqLoad();
                } else {
                    // No info available in extension we use the default formula with P and Q
                    Complex s = new Complex(load.getP0(), load.getQ0());
                    double s2 = s.abs() * s.abs();

                    if (s2 > EPSILON) {
                        zd = s.multiply(uNom * uNom / s2);
                    }
                }
                if (zd.abs() > EPSILON) {
                    Complex tmpY = zd.reciprocal().multiply(uNom * uNom / SB);
                    yLoads = yLoads.add(tmpY);
                }
            }
        }
        // TODO : create load only if it is not zero
        lfBus.setProperty(PROPERTY_SHORT_CIRCUIT, new ScLoad(yLoads)); // for now load extension is attached to the bus
    }
}
