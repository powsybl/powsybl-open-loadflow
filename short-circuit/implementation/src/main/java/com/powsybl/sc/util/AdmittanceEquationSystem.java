/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.*;
import com.powsybl.sc.util.extensions.ScGenerator;
import com.powsybl.sc.util.extensions.ScLoad;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import net.jafama.FastMath;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class AdmittanceEquationSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdmittanceEquationSystem.class);

    private static final double SB = 100.;

    private static final double EPSILON = 0.00000001;

    private AdmittanceEquationSystem() {
    }

    //Equations are created based on the branches connections
    private static void createImpedantBranch(VariableSet<VariableType> variableSet, EquationSystem<VariableType, EquationType> equationSystem,
                                             LfBranch branch, LfBus bus1, LfBus bus2, AdmittanceType admittanceType, FrequencyType frequencyType) {
        if (bus1 != null && bus2 != null) {
            // Equation system Y*V = I (expressed in cartesian coordinates x,y)
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_YR)
                    .addTerm(new AdmittanceEquationTermX1(branch, bus1, bus2, variableSet, admittanceType, frequencyType));

            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_YI)
                    .addTerm(new AdmittanceEquationTermY1(branch, bus1, bus2, variableSet, admittanceType, frequencyType));

            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_YR)
                    .addTerm(new AdmittanceEquationTermX2(branch, bus1, bus2, variableSet, admittanceType, frequencyType));

            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_YI)
                    .addTerm(new AdmittanceEquationTermY2(branch, bus1, bus2, variableSet, admittanceType, frequencyType));
        }
    }

    public enum AdmittanceVoltageProfileType {
        CALCULATED, // use the computed values at nodes to compute Y elements
        NOMINAL // use the nominal voltage values at nodes to get Y elements
    }

    public enum AdmittanceType {
        ADM_INJ, // all external nodal injections that does not come from branches are considered as current injectors (including shunts elements)
        ADM_SHUNT, // all external  nodal injections that does not come from branches are considered as current injectors (but not shunt elements)
        ADM_ADMIT, // all external  nodal injections are transformed into passive shunt elements included in the Y matrix (then [Ie] should be [0])
        // used to compute the Zth Thevenin Equivalent: shunts remain shunts, synchronous machines are transformed into X" equivalent shunts,
        // remaining injections are transformed into passive shunt elements included in the Y matrix
        ADM_THEVENIN,
        ADM_THEVENIN_HOMOPOLAR // used to compute the homopolar admittance matrix for unbalanced short circuits
    }

    public enum AdmittancePeriodType {
        ADM_SUB_TRANSIENT,
        ADM_TRANSIENT,
        ADM_STEADY_STATE,
    }

    public enum FrequencyType {
        FREQ_20_HZ,
        FREQ_50_HZ
    }

    private static void createBranches(LfNetwork network, VariableSet<VariableType> variableSet, EquationSystem<VariableType, EquationType> equationSystem,
            AdmittanceType admittanceType, FrequencyType frequencyType) {
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            PiModel piModel = branch.getPiModel();
            if (FastMath.abs(piModel.getX()) < LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE) {
                if (bus1 != null && bus2 != null) {
                    LOGGER.warn("Warning: Branch = {} : Non impedant lines not supported in the current version of the reduction method",
                            branch.getId());
                }
            } else {
                createImpedantBranch(variableSet, equationSystem, branch, bus1, bus2, admittanceType, frequencyType);
            }
        }
    }

    private static double getBfromShunt(LfBus bus) {
        List<Feeder> feederList = new ArrayList<>();
        return getBfromShuntAndUpdateFeederList(bus, feederList);
    }

    private static double getBfromShuntAndUpdateFeederList(LfBus bus, List<Feeder> feederList) {
        LfShunt shunt = bus.getShunt().orElse(null);
        double tmpB = 0.;
        if (shunt != null) {
            tmpB += shunt.getB();
            Feeder shuntFeeder = new Feeder(new Complex(0., shunt.getB()), shunt.getId(), Feeder.FeederType.SHUNT);
            feederList.add(shuntFeeder);
            //check if g will be implemented
        }
        LfShunt controllerShunt = bus.getControllerShunt().orElse(null);
        if (controllerShunt != null) {
            tmpB += controllerShunt.getB();
            Feeder shuntFeeder = new Feeder(new Complex(0., controllerShunt.getB()), controllerShunt.getId(), Feeder.FeederType.CONTROLLED_SHUNT);
            feederList.add(shuntFeeder);
            //check if g will be implemented
        }

        return tmpB;
    }

    private static Complex getYtransfromRdXdAndUpdateFeederList(LfBus bus, AdmittancePeriodType admittancePeriodType, List<Feeder> feederList, AdmittanceType admittanceType) {
        double vnomVl = bus.getNominalV();

        Complex tmpY = new Complex(0.);
        for (LfGenerator lfgen : bus.getGenerators()) { //compute R'd or R"d from generators at bus
            ScGenerator scGen = (ScGenerator) lfgen.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            double kG = (Double) lfgen.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
            Complex z = scGen.getTransZd().add(scGen.getStepUpTfoZ()).multiply(kG);
            if (admittancePeriodType == AdmittancePeriodType.ADM_SUB_TRANSIENT) {
                z = scGen.getSubTransZd().add(scGen.getStepUpTfoZ()).multiply(kG);
            }

            if (admittanceType == AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
                // For now, xo and ro are fixed independently of x'd and x"d:
                // further improvement might be needed if xo and ro are different for transient and subTransient short circuit analysis
                z = new Complex(0.);
                if (scGen.isGrounded()) {
                    z = scGen.getZo();
                }
            }

            double epsilon = 0.0000001;
            if (z.abs() > epsilon) {
                Complex yGen = z.reciprocal().multiply(vnomVl * vnomVl / SB);
                tmpY = tmpY.add(yGen);
                Feeder shuntFeeder = new Feeder(yGen, lfgen.getId(), Feeder.FeederType.GENERATOR);
                feederList.add(shuntFeeder);
            }
        }

        return tmpY;
    }

    private static void createShunts(LfNetwork network, VariableSet<VariableType> variableSet, EquationSystem<VariableType, EquationType> equationSystem, AdmittanceType admittanceType,
                                     AdmittanceVoltageProfileType admittanceVoltageProfileType, AdmittancePeriodType admittancePeriodType,
                                     boolean isShuntsIgnore, FeedersAtNetwork feeders, FrequencyType frequencyType) {
        for (LfBus bus : network.getBuses()) {

            Complex y = new Complex(0.); //total shunt at bus to be integrated in the admittance matrix
            Complex yLoadEq = new Complex(0.); //shunts created to represent the equivalence of loads and to be integrated in the total admittance matrix shunt at bus
            Complex yGenEq = new Complex(0.); //shunts created to represent the equivalence of generating units sand to be integrated in the total admittance matrix shunt at bus

            Complex v = ComplexUtils.polar2Complex(bus.getV(), Math.toRadians(bus.getAngle())); //choice of vbase to be used to transform power injections into equivalent shunts
            if (admittanceVoltageProfileType == AdmittanceVoltageProfileType.NOMINAL) {
                v = new Complex(1.);
            }
            boolean isBusPv = bus.isVoltageControlled();

            if (admittanceType == AdmittanceType.ADM_SHUNT) {
                if (!isShuntsIgnore) {
                    y = new Complex(0., getBfromShunt(bus)); // Handling shunts that physically exist
                }
            } else if (admittanceType == AdmittanceType.ADM_ADMIT) {
                if (!isShuntsIgnore) {
                    y = new Complex(0., getBfromShunt(bus)); // Handling shunts that physically exist
                }

                ScLoad scLoad = (ScLoad) bus.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
                yLoadEq = scLoad.getYdEquivalent().divide(v.abs() * v.abs());

                // Handling transformation of generators into equivalent shunts
                // Warning !!! : evaluation of power injections mandatory
                double gGenEq = -bus.getP().eval() / (v.abs() * v.abs()) - yLoadEq.getReal(); // full nodal P injection without the load

                if (isBusPv) {
                    // full nodal Q injection without the load
                    yGenEq = new Complex(gGenEq, bus.getQ().eval() / (v.abs() * v.abs()) + yLoadEq.getImaginary());
                } else {
                    yGenEq = new Complex(gGenEq, bus.getGenerationTargetQ() / (v.abs() * v.abs()));
                }
            } else if (admittanceType == AdmittanceType.ADM_THEVENIN) {

                List<Feeder> feederList = new ArrayList<>();

                if (!isShuntsIgnore) {
                    // Handling shunts that physically exist
                    y = new Complex(0., getBfromShuntAndUpdateFeederList(bus, feederList)); // ! updates feederList
                }

                ScLoad scLoad = (ScLoad) bus.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);

                yLoadEq = scLoad.getYdEquivalent().divide(v.abs() * v.abs());

                Feeder shuntFeeder = new Feeder(yLoadEq, bus.getId(), Feeder.FeederType.LOAD);
                feederList.add(shuntFeeder);

                yGenEq = getYtransfromRdXdAndUpdateFeederList(bus, admittancePeriodType, feederList, admittanceType); // ! updates feederList
                // TODO : check how to verify that the generators are operating

                FeedersAtBus shortCircuitEquationSystemBusFeeders = new FeedersAtBus(feederList, bus);
                feeders.busToFeeders.put(bus, shortCircuitEquationSystemBusFeeders);

            } else if (admittanceType == AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {

                List<Feeder> feederList = new ArrayList<>(); // not used yet in homopolar

                y = getYtransfromRdXdAndUpdateFeederList(bus, admittancePeriodType, feederList, admittanceType); // ! updates feederList
                //TODO : check how to verify that the generators are operating

                FeedersAtBus shortCircuitEquationSystemBusFeeders = new FeedersAtBus(feederList, bus);
                feeders.busToFeeders.put(bus, shortCircuitEquationSystemBusFeeders);
            }

            y = y.add(yLoadEq).add(yGenEq);

            // coef to adapt reactance if freq is not 50Hz
            double freqCoef = 1.0;
            if (frequencyType == FrequencyType.FREQ_20_HZ) {
                freqCoef = 20. / 50.;
            }

            Complex z = y.reciprocal();
            z = new Complex(z.getReal(), z.getImaginary() * freqCoef);
            y = z.reciprocal();

            if (y.abs() > EPSILON) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_YR)
                        .addTerm(new AdmittanceEquationTermShunt(y.getReal(), y.getImaginary(), bus, variableSet, true));
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_YI)
                        .addTerm(new AdmittanceEquationTermShunt(y.getReal(), y.getImaginary(), bus, variableSet, false));
            }
        }
    }

    public static EquationSystem<VariableType, EquationType> create(LfNetwork network, VariableSet<VariableType> variableSet,
                                                                    AdmittanceType admittanceType, AdmittanceVoltageProfileType admittanceVoltageProfileType,
                                                                    AdmittancePeriodType admittancePeriodType, boolean isShuntsIgnore, FeedersAtNetwork feeders,
                                                                    AcLoadFlowParameters acLoadFlowParameters, FrequencyType frequencyType) {

        EquationSystem<VariableType, EquationType> equationSystem = new EquationSystem<>(EquationType.class, network, variableSet);

        if (admittanceType == AdmittanceType.ADM_ADMIT) {
            try (AcLoadFlowContext context = new AcLoadFlowContext(network, acLoadFlowParameters)) {
                new AcloadFlowEngine(context)
                        .run();
            }
        }

        createBranches(network, variableSet, equationSystem, admittanceType, frequencyType);
        if (admittanceType != AdmittanceType.ADM_INJ) { //shunts created in the admittance matrix are only those that really exist in the network
            createShunts(network, variableSet, equationSystem, admittanceType, admittanceVoltageProfileType, admittancePeriodType, isShuntsIgnore, feeders, frequencyType);
        }

        return equationSystem;
    }

}
