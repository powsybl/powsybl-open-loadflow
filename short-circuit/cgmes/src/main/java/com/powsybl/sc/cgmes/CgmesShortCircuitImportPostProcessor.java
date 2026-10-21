/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.cgmes;

import com.google.auto.service.AutoService;
import com.powsybl.cgmes.conversion.CgmesImportPostProcessor;
import com.powsybl.cgmes.model.CgmesModel;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.sc.extensions.*;
import com.powsybl.triplestore.api.PropertyBag;
import com.powsybl.triplestore.api.QueryCatalog;
import com.powsybl.triplestore.api.TripleStore;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(CgmesImportPostProcessor.class)
public class CgmesShortCircuitImportPostProcessor implements CgmesImportPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CgmesShortCircuitImportPostProcessor.class);

    public static final String NAME = "ShortCircuit";

    @Override
    public String getName() {
        return NAME;
    }

    private final QueryCatalog queryCatalog = new QueryCatalog("short-circuit.sparql");

    public static final double EPSILON = 0.000001;

    private void processExternalNetworkInjection(Network network, TripleStore tripleStore) {
        for (PropertyBag propertyBag : tripleStore.query(queryCatalog.get("externalNetworkInjectionShortCircuit"))) {
            String id = propertyBag.getId("ID");
            double ikQmax = propertyBag.asDouble("maxInitialSymShCCurrent");
            double roOverXo = propertyBag.asDouble("maxR0ToX0Ratio");
            double rOverX = propertyBag.asDouble("maxR1ToX1Ratio");
            double zoOverZ = propertyBag.asDouble("maxZ0ToZ1Ratio");
            double voltageFactor = propertyBag.asDouble("voltageFactor");

            /*System.out.println(" ================>  ExternalNetworkInjectionId = " + id + " ikQmax = " + ikQmax
                    + " maxR0ToX0Ratio = " + maxR0ToX0Ratio + " maxR1ToX1Ratio = " + maxR1ToX1Ratio + " maxZ0ToZ1Ratio = " + maxZ0ToZ1Ratio
                    + " voltageFactor = " + voltageFactor);*/

            Generator generator = network.getGenerator(id);
            if (generator == null) {
                throw new PowsyblException("Generator '" + id + "' not found");
            }

            double xoOverX = zoOverZ * Math.sqrt((rOverX * rOverX + 1.) / (roOverXo * roOverXo + 1.));
            double roOverR = 0.;
            if (Math.abs(rOverX) > EPSILON) {
                roOverR = roOverXo * xoOverX / rOverX;
            }

            boolean grounded = false;
            if (Math.abs(xoOverX) > EPSILON || Math.abs(roOverR) > EPSILON) {
                grounded = true;
            }

            //double ikQmax = extensions2.getIkQmax();
            //double maxR1ToX1Ratio = extensions2.getMaxR1ToX1Ratio();
            //double cq = extensions2.getCq();

            // Zq = cq * Unomq / (sqrt3 * Ikq)
            // Zq = sqrt(rÂ² + xÂ²) which gives x = Zq / sqrt((R/X)Â² + 1)
            double zq = voltageFactor * generator.getTerminal().getVoltageLevel().getNominalV() / (Math.sqrt(3.) * ikQmax / 1000.); // ikQmax is changed from A to kA
            double xq = zq / Math.sqrt(rOverX * rOverX + 1.);
            double rq = xq * rOverX;

            generator.newExtension(GeneratorShortCircuitAdder.class)
                    .withStepUpTransformerX(0.)
                    .withDirectTransX(xq)
                    .withDirectSubtransX(xq)
                    .add();
            generator.newExtension(GeneratorShortCircuitAdder2.class)
                    .withSubTransRd(rq)
                    .withTransRd(rq)
                    .withRatedU(0.)
                    .withCosPhi(0.)
                    .withMaxR1ToX1Ratio(rOverX) // extensions for feeder type
                    .withCq(voltageFactor)
                    .withIkQmax(ikQmax)
                    .add();

            generator.newExtension(GeneratorFortescueAdder.class)
                    .withRz(roOverR * rq)
                    .withXz(xoOverX * xq)
                    .withRn(0.)
                    .withXn(0.)
                    .withGrounded(grounded)
                    .add();

            generator.newExtension(GeneratorFortescueTypeAdder.class)
                    .withGeneratorType(GeneratorFortescueType.GeneratorType.FEEDER)
                    .add();
        }
    }

    private void processSynchronousMachines(Network network, TripleStore tripleStore) {
        for (PropertyBag propertyBag : tripleStore.query(queryCatalog.get("synchronousMachineShortCircuit"))) {
            String id = propertyBag.getId("ID");
            double r0 = propertyBag.asDouble("r0");
            double r2 = propertyBag.asDouble("r2");
            double x0 = propertyBag.asDouble("x0");
            double x2 = propertyBag.asDouble("x2");
            double r = propertyBag.asDouble("r"); // given in ohms
            double subTransXdPu = propertyBag.asDouble("satDirectSubtransX"); // value given in pu
            double transXdPu = propertyBag.asDouble("satDirectTransX"); // value given in pu
            double ratedU = propertyBag.asDouble("ratedU");
            double ratedPowerFactor = propertyBag.asDouble("ratedPowerFactor");
            double voltageRegulationRange = propertyBag.asDouble("voltageRegulationRange", 0.);
            boolean earthing = propertyBag.asBoolean("earthing", false);

            Generator generator = network.getGenerator(id);
            if (generator == null) {
                throw new PowsyblException("Generator '" + id + "' not found");
            }
            double genRatedS = generator.getRatedS();
            if (genRatedS == 0) {
                throw new PowsyblException("Generator '" + generator.getId() + "' has a rated S equal to zero");
            }
            if (ratedU == 0) {
                throw new PowsyblException("Generator '" + generator.getId() + "' has a rated U equal to zero");
            }

            double zBase = ratedU * ratedU / genRatedS;
            double subTransXd = subTransXdPu * zBase;
            double transXd = transXdPu * zBase;

            generator.newExtension(GeneratorShortCircuitAdder.class)
                    .withStepUpTransformerX(0.)
                    .withDirectTransX(transXd)
                    .withDirectSubtransX(subTransXd)
                    .add();
            generator.newExtension(GeneratorShortCircuitAdder2.class)
                    .withSubTransRd(r)
                    .withTransRd(r)
                    .withRatedU(ratedU)
                    .withCosPhi(ratedPowerFactor)
                    .withVoltageRegulationRange(voltageRegulationRange)
                    .add();

            generator.newExtension(GeneratorFortescueAdder.class)
                    .withRz(r0)
                    .withXz(x0)
                    .withRn(0.)
                    .withXn(0.)
                    .withGrounded(earthing)
                    .withGroundingR(0.)  // TODO : check if info available
                    .add();

            generator.newExtension(GeneratorFortescueTypeAdder.class)
                    .withGeneratorType(GeneratorFortescueType.GeneratorType.ROTATING_MACHINE)
                    .add();
        }
    }

    private void processAsynchronousMachines(Network network, TripleStore tripleStore) {
        for (PropertyBag propertyBag : tripleStore.query(queryCatalog.get("asynchronousMachineShortCircuit"))) {
            String id = propertyBag.getId("ID");
            double ratedMechanicalPower = propertyBag.asDouble("ratedMechanicalPower");
            double ratedPowerFactor = propertyBag.asDouble("ratedPowerFactor");
            double ratedS = propertyBag.asDouble("ratedS");
            double ratedU = propertyBag.asDouble("ratedU");
            double efficiency = propertyBag.asDouble("efficiency");
            double iaIrRatio = propertyBag.asDouble("iaIrRatio");
            double rxLockedRotorRatio = propertyBag.asDouble("rxLockedRotorRatio");
            int polePairNumber = propertyBag.asInt("polePairNumber");

            Load load = network.getLoad(id);
            if (load == null) {
                throw new PowsyblException("Load '" + id + "' not found");
            }

            load.newExtension(LoadShortCircuitAdder.class)
                    .withLoadShortCircuitType(LoadShortCircuit.LoadShortCircuitType.ASYNCHRONOUS_MACHINE)
                    .withAsynchronousMachineLoadData(new LoadShortCircuit.AsynchronousMachineLoadData(ratedMechanicalPower, ratedPowerFactor, ratedS, ratedU,
                            efficiency, iaIrRatio, polePairNumber, rxLockedRotorRatio))
                    .add();
        }
    }

    private void processAcLineSegments(Network network, TripleStore tripleStore) {
        for (PropertyBag propertyBag : tripleStore.query(queryCatalog.get("acLineSegmentShortCircuit"))) {
            String id = propertyBag.getId("ID");
            double r0 = propertyBag.asDouble("r0");
            double x0 = propertyBag.asDouble("x0");
            double g0ch = propertyBag.asDouble("g0ch");
            double b0ch = propertyBag.asDouble("b0ch");
            Line line = network.getLine(id);
            if (line != null) {
                line.newExtension(LineFortescueAdder.class)
                        .withRz(r0)
                        .withXz(x0)
                        .add();
            } else {
                BoundaryLine danglingLine = network.getBoundaryLine(id);
                if (danglingLine != null) {
                    // FIXME LineShortCircuitAdder should be compatible with dangling lines
                    // TODO
                } else {
                    throw new PowsyblException("Line or dangling line not found: '" + id + "'");
                }
            }
        }
    }

    private void processPowerTransformers(Network network, TripleStore tripleStore, Map<TwoWindingsTransformerFortescue, Pair<Double, Double>> tmpExtensionToRoXo) {
        for (PropertyBag propertyBag : tripleStore.query(queryCatalog.get("powerTransformerShortCircuit"))) {
            String id = propertyBag.getId("ID");
            boolean isPartOfGeneratingUnit = propertyBag.asBoolean("isPartOfGeneratorUnit", false);

            TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer(id);
            if (t2wt != null) {
                TwoWindingsTransformerFortescue extension = t2wt.getExtension(TwoWindingsTransformerFortescue.class);
                t2wt.setProperty("isPartOfGeneratorUnit", String.valueOf(isPartOfGeneratingUnit));
                if (extension == null) {
                    t2wt.newExtension(TwoWindingsTransformerFortescueAdder.class)
                            .withRz(t2wt.getR())
                            .withXz(t2wt.getX())
                            .add();
                } else {
                    Pair<Double, Double> roxo = tmpExtensionToRoXo.get(extension);
                    double ro = roxo.getFirst();
                    double xo = roxo.getSecond();
                    extension.setRz(ro);
                    extension.setXz(xo);

                }
            }
        }
    }

    private void processPowerTransformerEnds(Network network, TripleStore tripleStore, Map<TwoWindingsTransformerFortescue, Pair<Double, Double>> tmpExtensionToRoXo) {
        for (PropertyBag propertyBag : tripleStore.query(queryCatalog.get("powerTransformerEndShortCircuit"))) {
            String id = propertyBag.getId("ID");
            int endNumber = propertyBag.asInt("endNumber");
            double r0 = propertyBag.asDouble("r0");
            double x0 = propertyBag.asDouble("x0");
            double g0 = propertyBag.asDouble("g0");
            double b0 = propertyBag.asDouble("b0");
            double ratedS = propertyBag.asDouble("ratedS");
            double rground = propertyBag.asDouble("rground");
            double xground = propertyBag.asDouble("xground");
            boolean grounded = propertyBag.asBoolean("grounded", false);
            String connectionKind = propertyBag.getId("connectionKind");

            String windingConnection = "WindingConnection.";
            String connectionY = windingConnection + "Y";
            String connectionYn = windingConnection + "Yn";
            String connectionD = windingConnection + "D";

            WindingConnectionType legConnectionType;
            if (connectionKind.equals(connectionY)) {
                legConnectionType = WindingConnectionType.Y;
            } else if (connectionKind.equals(connectionD)) {
                legConnectionType = WindingConnectionType.DELTA;
            } else if (connectionKind.equals(connectionYn)) {
                legConnectionType = WindingConnectionType.Y_GROUNDED;
            } else {
                throw new PowsyblException("Leg connection type '" + connectionKind + "' not handled in transformer : " + id);
            }

            TwoWindingsTransformer t2wt = network.getTwoWindingsTransformer(id);
            if (t2wt != null) {
                t2wt.setRatedS(ratedS); // rated S not not filled in CGMES import example for transformers
                TwoWindingsTransformerFortescue extension = t2wt.getExtension(TwoWindingsTransformerFortescue.class);
                if (extension == null) {
                    t2wt.newExtension(TwoWindingsTransformerFortescueAdder.class)
                            .add();
                    extension = t2wt.getExtension(TwoWindingsTransformerFortescue.class);
                }

                // use of a local variable to get the sum of ro and xo from both ends to get the coefs afterwards
                double tmpR0 = 0.;
                double tmpX0 = 0.;
                if (tmpExtensionToRoXo.containsKey(extension)) {
                    Pair<Double, Double> tmpRoXo = tmpExtensionToRoXo.get(extension);
                    tmpR0 = tmpRoXo.getFirst();
                    tmpX0 = tmpRoXo.getSecond();
                }

                if (endNumber == 1) {
                    double rho2 = t2wt.getRatedU2() * t2wt.getRatedU2() / t2wt.getRatedU1() / t2wt.getRatedU1();
                    double roRatedU2 = r0 * rho2;
                    double xoRatedU2 = x0 * rho2;

                    // update of the sum zo = zo1 + zo2
                    tmpR0 = tmpR0 + roRatedU2;
                    tmpX0 = tmpX0 + xoRatedU2;

                    extension.setConnectionType1(legConnectionType);

                    if (grounded) {
                        extension.setGroundingR1(3. * rground * rho2); //ZoT_ground = 3 * Zo_Ground , we put in input the rated value at side 2 ready to be added to R and X of transformer
                        extension.setGroundingX1(3. * xground * rho2);
                    }

                } else if (endNumber == 2) {

                    // update of the sum zo = zo1 + zo2
                    tmpR0 = tmpR0 + r0;
                    tmpX0 = tmpX0 + x0;

                    extension.setConnectionType2(legConnectionType);

                    if (grounded) {
                        extension.setGroundingR2(3. * rground); //ZoT_ground = 3 * Zo_Ground , we put in input the rated value at side 2 ready to be added to R and X of transformer
                        extension.setGroundingX2(3. * xground);
                    }

                } else {
                    throw new PowsyblException("incorrect end number for 2 windings transformer end '" + id + "'");
                }

                // update of the local variable for the sum zo = zo1 + zo2
                Pair<Double, Double> newPair = new Pair<>(tmpR0, tmpX0);
                tmpExtensionToRoXo.put(extension, newPair);

            } else {
                ThreeWindingsTransformer t3wt = network.getThreeWindingsTransformer(id);
                if (t3wt != null) {
                    ThreeWindingsTransformerFortescue extension = t3wt.getExtension(ThreeWindingsTransformerFortescue.class);
                    if (extension == null) {
                        t3wt.newExtension(ThreeWindingsTransformerFortescueAdder.class)
                                .add();
                        extension = t3wt.getExtension(ThreeWindingsTransformerFortescue.class);
                    }

                    double ratedU02 = t3wt.getRatedU0() * t3wt.getRatedU0();

                    if (endNumber == 1) {
                        t3wt.getLeg1().setRatedS(ratedS);
                        setLegRoXoCoefs(t3wt.getLeg1(), extension.getLeg1(), r0, x0, ratedU02);
                        extension.getLeg1().setConnectionType(legConnectionType);
                    } else if (endNumber == 2) {
                        t3wt.getLeg2().setRatedS(ratedS);
                        setLegRoXoCoefs(t3wt.getLeg2(), extension.getLeg2(), r0, x0, ratedU02);
                        extension.getLeg2().setConnectionType(legConnectionType);
                    } else if (endNumber == 3) {
                        t3wt.getLeg3().setRatedS(ratedS);
                        setLegRoXoCoefs(t3wt.getLeg3(), extension.getLeg3(), r0, x0, ratedU02);
                        extension.getLeg3().setConnectionType(legConnectionType);
                    } else {
                        throw new PowsyblException("incorrect end number for 3 windings transformer end '" + id + "'");
                    }

                } else {
                    throw new PowsyblException("2 or 3 windings transformer not found: '" + id + "'");
                }
            }
        }
    }

    public void setLegRoXoCoefs(ThreeWindingsTransformer.Leg leg, LegFortescue extLeg, double r0, double x0, double ratedU02) {

        double ratedRo = r0 * ratedU02 / leg.getRatedU() / leg.getRatedU();
        double ratedXo = x0 * ratedU02 / leg.getRatedU() / leg.getRatedU();
        extLeg.setRz(ratedRo);
        extLeg.setXz(ratedXo);
    }

    @Override
    public void process(Network network, CgmesModel cgmesModel) {
        LOGGER.info("Loading CGMES short circuit data...");
        processSynchronousMachines(network, cgmesModel.tripleStore());
        processAsynchronousMachines(network, cgmesModel.tripleStore());
        processAcLineSegments(network, cgmesModel.tripleStore());
        Map<TwoWindingsTransformerFortescue, Pair<Double, Double>> tmpExtensionToRoXo = new HashMap<>();
        processPowerTransformerEnds(network, cgmesModel.tripleStore(), tmpExtensionToRoXo);
        processPowerTransformers(network, cgmesModel.tripleStore(), tmpExtensionToRoXo);
        processExternalNetworkInjection(network, cgmesModel.tripleStore());
    }
}
