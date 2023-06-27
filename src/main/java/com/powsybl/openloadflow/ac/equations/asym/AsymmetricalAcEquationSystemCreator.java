/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.extensions.*;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymmetricalAcEquationSystemCreator extends AcEquationSystemCreator {

    public AsymmetricalAcEquationSystemCreator(LfNetwork network, AcEquationSystemCreationParameters creationParameters) {
        super(network, creationParameters);
    }

    @Override
    protected void createBusEquation(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        super.createBusEquation(bus, equationSystem);

        // addition of asymmetric equations, supposing that existing v, theta, p and q are linked to the direct sequence
        AsymBus asymBus = (AsymBus) bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);

        if (asymBus.getAsymBusVariableType() == AsymBusVariableType.WYE) {
            if (asymBus.getNbExistingPhases() == 0) {
                var ixi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE);
                asymBus.setIxNegative(ixi);
                var iyi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE);
                asymBus.setIyNegative(iyi);

                ixi.setActive(true);
                iyi.setActive(true);
            }

            if (asymBus.getNbExistingPhases() <= 1) {
                var ixh = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO);
                asymBus.setIxZero(ixh);
                var iyh = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO);
                asymBus.setIyZero(iyh);
                ixh.setActive(true);
                iyh.setActive(true);
            }

        } else {
            if (asymBus.getNbExistingPhases() > 0) {
                throw new IllegalStateException(" Delta config with missing phases not yet handled at bus : " + bus.getId());
            }

            // delta connection
            var ixi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE);
            asymBus.setIxNegative(ixi);
            var iyi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE);
            asymBus.setIyNegative(iyi);

            ixi.setActive(true);
            iyi.setActive(true);

        }

        double epsilon = 0.00001;

        // Handle generators at bus for homopolar and inverse
        for (LfGenerator gen : bus.getGenerators()) {
            // if there is at least one generating unit that is voltage controlling we model the equivalent in inverse and homopolar
            // with a large admittance yg = g +jb to model a close connection of the bus to the ground (E_homopolar = 0 E_Inverse = 0)
            if (gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.REMOTE_REACTIVE_POWER) {
                throw new IllegalStateException("Generating unit with remote reactive power not yet supported in asymmetric load flow for generator: " + gen.getId());
            }

            if (gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE) {

                AsymGenerator asymGenerator = (AsymGenerator) gen.getProperty(AsymGenerator.PROPERTY_ASYMMETRICAL);
                if (asymGenerator != null) {
                    asymBus.setbZeroEquivalent(asymBus.getbZeroEquivalent() + asymGenerator.getBz());
                    asymBus.setgZeroEquivalent(asymBus.getgZeroEquivalent() + asymGenerator.getGz());
                    asymBus.setbNegativeEquivalent(asymBus.getbNegativeEquivalent() + asymGenerator.getBn());
                    asymBus.setgNegativeEquivalent(asymBus.getgNegativeEquivalent() + asymGenerator.getGn());
                    // TODO : try to add perfect asym generator
                }
            }
        }

        if (asymBus.getAsymBusVariableType() == AsymBusVariableType.WYE) {
            if (Math.abs(asymBus.getbZeroEquivalent()) > epsilon || Math.abs(asymBus.getgZeroEquivalent()) > epsilon) {
                ShuntFortescueIxEquationTerm ixShuntZero = new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.ZERO);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO).addTerm(ixShuntZero);
                ShuntFortescueIyEquationTerm iyShuntZero = new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.ZERO);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO).addTerm(iyShuntZero);
            }
        }
        /*if (Math.abs(asymBus.getbZeroEquivalent()) > epsilon || Math.abs(asymBus.getgZeroEquivalent()) > epsilon) {
            ShuntFortescueIxEquationTerm ixShuntZero = new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.ZERO);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO).addTerm(ixShuntZero);
            ShuntFortescueIyEquationTerm iyShuntZero = new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.ZERO);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO).addTerm(iyShuntZero);
        }*/

        if (Math.abs(asymBus.getgNegativeEquivalent()) > epsilon || Math.abs(asymBus.getbNegativeEquivalent()) > epsilon) {
            ShuntFortescueIxEquationTerm ixShuntNegative = new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.NEGATIVE);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE).addTerm(ixShuntNegative);
            ShuntFortescueIyEquationTerm iyShuntNegative = new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.NEGATIVE);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE).addTerm(iyShuntNegative);
        }

        // P,Q loads
        if (Math.abs(bus.getLoadTargetP()) > epsilon || Math.abs(bus.getLoadTargetQ()) > epsilon || asymBus.asymLoadExist()) {
            if (asymBus.isFortescueRepresentation()) {
                // load modelled as a constant power load in abc phase representation leading to a model depending on vd, vi, vo in fortescue representation
                LoadFortescuePowerEquationTerm pLoadPositive = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.POSITIVE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P).addTerm(pLoadPositive);
                LoadFortescuePowerEquationTerm qLoadPositive = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.POSITIVE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(qLoadPositive);

                LoadFortescuePowerEquationTerm ixLoadNegative = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.NEGATIVE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE).addTerm(ixLoadNegative);
                LoadFortescuePowerEquationTerm iyLoadNegative = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.NEGATIVE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE).addTerm(iyLoadNegative);

                if (asymBus.getAsymBusVariableType() == AsymBusVariableType.WYE) {
                    LoadFortescuePowerEquationTerm ixLoadZero = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.ZERO);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO).addTerm(ixLoadZero);
                    LoadFortescuePowerEquationTerm iyLoadZero = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.ZERO);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO).addTerm(iyLoadZero);
                }
            } else {
                // use of load in ABC sequences
                // for now only S with current moddeling provided

                if (asymBus.getLoadType() == AsymBusLoadType.CONSTANT_POWER) {
                    LoadAbcPowerEquationTerm pLoadPositive = new LoadAbcPowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.POSITIVE);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P).addTerm(pLoadPositive);
                    LoadAbcPowerEquationTerm qLoadPositive = new LoadAbcPowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.POSITIVE);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(qLoadPositive);

                    int nbPhases = 3 - asymBus.getNbExistingPhases();
                    if (nbPhases == 3) {
                        LoadAbcPowerEquationTerm ixLoadNegative = new LoadAbcPowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.NEGATIVE);
                        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE).addTerm(ixLoadNegative);
                        LoadAbcPowerEquationTerm iyLoadNegative = new LoadAbcPowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.NEGATIVE);
                        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE).addTerm(iyLoadNegative);
                    }

                    if (nbPhases > 1) {
                        LoadAbcPowerEquationTerm ixLoadZero = new LoadAbcPowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.ZERO);
                        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO).addTerm(ixLoadZero);
                        LoadAbcPowerEquationTerm iyLoadZero = new LoadAbcPowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.ZERO);
                        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO).addTerm(iyLoadZero);
                    }
                } else if (asymBus.getLoadType() == AsymBusLoadType.CONSTANT_IMPEDANCE) {
                    //throw new IllegalStateException("Non-Constant Power ABC loads not yet handled at bus : " + bus.getId());
                    LoadAbcImpedantEquationTerm pLoadPositive = new LoadAbcImpedantEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.POSITIVE);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P).addTerm(pLoadPositive);
                    LoadAbcImpedantEquationTerm qLoadPositive = new LoadAbcImpedantEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.POSITIVE);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(qLoadPositive);

                    int nbPhases = 3 - asymBus.getNbExistingPhases();
                    if (nbPhases == 3) {
                        LoadAbcImpedantEquationTerm ixLoadNegative = new LoadAbcImpedantEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.NEGATIVE);
                        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE).addTerm(ixLoadNegative);
                        LoadAbcImpedantEquationTerm iyLoadNegative = new LoadAbcImpedantEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.NEGATIVE);
                        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE).addTerm(iyLoadNegative);
                    }

                    if (nbPhases > 1) {
                        LoadAbcImpedantEquationTerm ixLoadZero = new LoadAbcImpedantEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.ZERO);
                        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO).addTerm(ixLoadZero);
                        LoadAbcImpedantEquationTerm iyLoadZero = new LoadAbcImpedantEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.ZERO);
                        equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO).addTerm(iyLoadZero);
                    }

                }
            }
        }
    }

    @Override
    protected void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // positive sequence
        EquationTerm<AcVariableType, AcEquationType> p1 = null;
        EquationTerm<AcVariableType, AcEquationType> q1 = null;
        EquationTerm<AcVariableType, AcEquationType> p2 = null;
        EquationTerm<AcVariableType, AcEquationType> q2 = null;
        EquationTerm<AcVariableType, AcEquationType> i1 = null;
        EquationTerm<AcVariableType, AcEquationType> i2 = null;

        // zero sequence
        EquationTerm<AcVariableType, AcEquationType> ixz1 = null;
        EquationTerm<AcVariableType, AcEquationType> iyz1 = null;
        EquationTerm<AcVariableType, AcEquationType> ixz2 = null;
        EquationTerm<AcVariableType, AcEquationType> iyz2 = null;

        // negative sequence
        EquationTerm<AcVariableType, AcEquationType> ixn1 = null;
        EquationTerm<AcVariableType, AcEquationType> iyn1 = null;
        EquationTerm<AcVariableType, AcEquationType> ixn2 = null;
        EquationTerm<AcVariableType, AcEquationType> iyn2 = null;

        boolean deriveA1 = isDeriveA1(branch, creationParameters);
        boolean deriveR1 = isDeriveR1(branch);

        boolean isBus1Wye = true;
        boolean isBus2Wye = true;
        AsymBus asymBus1 = (AsymBus) bus1.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus1.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            isBus1Wye = false;
        }
        AsymBus asymBus2 = (AsymBus) bus2.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus2.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            isBus2Wye = false;
        }

        if (bus1 != null && bus2 != null) {

            if (!hasBranchAsymmetry(branch)) {
                // no assymmetry is detected with this line, we handle the equations as Fortescue and decoupled
                p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
                q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
                p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
                q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
                i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
                i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);

                if (branch.getBranchType() == LfBranch.BranchType.LINE) {
                    // zero
                    ixz1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.ZERO);
                    iyz1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.ZERO);
                    ixz2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.ZERO);
                    iyz2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.ZERO);

                    // negative
                    ixn1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.NEGATIVE);
                    iyn1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.NEGATIVE);
                    ixn2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.NEGATIVE);
                    iyn2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.NEGATIVE);
                } else {
                    // this must be a fortescue transformer
                    // zero
                    ixz1 = new ClosedBranchTfoZeroIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I1x);
                    iyz1 = new ClosedBranchTfoZeroIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I1y);
                    ixz2 = new ClosedBranchTfoZeroIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I2x);
                    iyz2 = new ClosedBranchTfoZeroIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I2y);

                    // negative
                    ixn1 = new ClosedBranchTfoNegativeIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I1x);
                    iyn1 = new ClosedBranchTfoNegativeIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I1y);
                    ixn2 = new ClosedBranchTfoNegativeIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I2x);
                    iyn2 = new ClosedBranchTfoNegativeIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I2y);
                }

            } else {
                // assymmetry is detected with this line, we handle the equations as coupled between the different sequences
                // positive
                int nbPhases1 = 3 - asymBus1.getNbExistingPhases();
                int nbPhases2 = 3 - asymBus2.getNbExistingPhases();

                // test: if we are WYE variables and there is a phase missing, the positive sequence is modeled with currents
                if (isBus1Wye) {

                    if (asymBus1.isPositiveSequenceAsCurrent()) {
                        // in this case we try to model the positive sequence as current balances
                        p1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.POSITIVE);
                        q1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.POSITIVE);
                    } else {
                        p1 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.POSITIVE);
                        q1 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.POSITIVE);
                    }

                    // test
                    if (nbPhases1 == 3) {
                        ixn1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.NEGATIVE);
                        iyn1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.NEGATIVE);
                    }
                    if (nbPhases1 > 1) {
                        ixz1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.ZERO);
                        iyz1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.ZERO);
                    }

                } else {
                    p1 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.POSITIVE);
                    q1 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.POSITIVE);

                    ixn1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.NEGATIVE);
                    iyn1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.NEGATIVE);
                }

                if (isBus2Wye) {

                    if (asymBus2.isPositiveSequenceAsCurrent()) {
                        p2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.POSITIVE);
                        q2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.POSITIVE);
                    } else {
                        p2 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.POSITIVE);
                        q2 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.POSITIVE);
                    }

                    if (nbPhases2 == 3) {
                        ixn2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.NEGATIVE);
                        iyn2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.NEGATIVE);
                    }
                    if (nbPhases2 > 1) {
                        ixz2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.ZERO);
                        iyz2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.ZERO);
                    }
                } else {
                    p2 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.POSITIVE);
                    q2 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.POSITIVE);

                    ixn2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.NEGATIVE);
                    iyn2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.NEGATIVE);
                }

            }
        } else if (bus1 != null) {
            throw new IllegalStateException("Line open at one side not yet supported in asymmetric load flow at bus: " + bus1.getId());
        } else if (bus2 != null) {
            throw new IllegalStateException("Line open at one side not yet supported in asymmetric load flow  at bus: " + bus2.getId());
        }

        // positive
        if (p1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P)
                    .orElseThrow()
                    .addTerm(p1);
            branch.setP1(p1);
        }
        if (q1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_Q)
                    .orElseThrow()
                    .addTerm(q1);
            branch.setQ1(q1);
        }
        if (p2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P)
                    .orElseThrow()
                    .addTerm(p2);
            branch.setP2(p2);
        }
        if (q2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_Q)
                    .orElseThrow()
                    .addTerm(q2);
            branch.setQ2(q2);
        }

        if (i1 != null) {
            equationSystem.attach(i1);
            branch.setI1(i1);
        }

        if (i2 != null) {
            equationSystem.attach(i2);
            branch.setI2(i2);
        }

        // zero
        if (ixz1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_IX_ZERO)
                    .orElseThrow()
                    .addTerm(ixz1);
        }
        if (iyz1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_IY_ZERO)
                    .orElseThrow()
                    .addTerm(iyz1);
        }
        if (ixz2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_IX_ZERO)
                    .orElseThrow()
                    .addTerm(ixz2);
        }
        if (iyz2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_IY_ZERO)
                    .orElseThrow()
                    .addTerm(iyz2);
        }

        // negative
        if (ixn1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE)
                    .orElseThrow()
                    .addTerm(ixn1);
        }
        if (iyn1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_IY_NEGATIVE)
                    .orElseThrow()
                    .addTerm(iyn1);
        }
        if (ixn2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_IX_NEGATIVE)
                    .orElseThrow()
                    .addTerm(ixn2);
        }
        if (iyn2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_IY_NEGATIVE)
                    .orElseThrow()
                    .addTerm(iyn2);
        }

        createReactivePowerControlBranchEquation(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);

        createTransformerPhaseControlEquations(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);

    }

    public boolean hasBranchAsymmetry(LfBranch branch) {
        boolean asymmetry = false;
        // check the existence of an extension
        if (branch.getBranchType() == LfBranch.BranchType.LINE || branch.getBranchType() == LfBranch.BranchType.TRANSFO_2) {
            AsymBranch asymBranch = (AsymBranch) branch.getProperty(AsymBranch.PROPERTY_ASYMMETRICAL);
            if (asymBranch != null) {
                boolean asymmetryCoupling = asymBranch.getAdmittanceMatrix().isCoupled();
                boolean asymmetryMissingPhase = !(asymBranch.isHasPhaseA1() && asymBranch.isHasPhaseA2()
                        && asymBranch.isHasPhaseB1() && asymBranch.isHasPhaseB2()
                        && asymBranch.isHasPhaseC1() && asymBranch.isHasPhaseC2());
                return asymmetryCoupling || asymmetryMissingPhase;
            }
            return asymmetry;
        }
        return false;

    }
}
