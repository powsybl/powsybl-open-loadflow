/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.extensions.*;
import com.powsybl.openloadflow.util.ComplexPart;
import com.powsybl.openloadflow.util.Fortescue;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at gmail.com>}
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
public class AsymmetricalAcEquationSystemCreator extends AcEquationSystemCreator {

    private static final double EPSILON = 0.00001;

    public AsymmetricalAcEquationSystemCreator(LfNetwork network, AcEquationSystemCreationParameters creationParameters) {
        super(network, creationParameters);
    }

    @Override
    protected void createBusEquation(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        super.createBusEquation(bus, equationSystem);

        // addition of asymmetric equations, supposing that existing v, theta, p and q are linked to the positive sequence
        LfAsymBus asymBus = bus.getAsym();

        if (asymBus.getAsymBusVariableType() == AsymBusVariableType.WYE) {
            if (asymBus.getNbMissingPhases() == 0) {
                asymBus.setIxN(equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE));
                asymBus.setIyN(equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE));
            }

            if (asymBus.getNbMissingPhases() <= 1) {
                asymBus.setIxZ(equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO));
                asymBus.setIyZ(equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO));
            }

        } else {
            if (asymBus.getNbMissingPhases() > 0) {
                throw new IllegalStateException(" Delta config with missing phases not yet handled at bus : " + bus.getId());
            }
            // delta connection
            asymBus.setIxN(equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE));
            asymBus.setIyN(equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE));
        }

        // Handle generators at bus for homopolar and inverse
        for (LfGenerator gen : bus.getGenerators()) {
            // if there is at least one generating unit that is voltage controlling we model the equivalent in inverse and homopolar
            // with a large admittance yg = g +jb to model a close connection of the bus to the ground (E_homopolar = 0 E_Inverse = 0)
            if (gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.REMOTE_REACTIVE_POWER
                    || gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.MONITORING_VOLTAGE) {
                throw new IllegalStateException("Generator with control type " + gen.getGeneratorControlType()
                        + " not yet supported in asymmetric load flow: " + gen.getId());
            }

            if (gen.getAsym() != null && (gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE
                    || gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.MONITORING_VOLTAGE)) {
                asymBus.setBzEquiv(asymBus.getBzEquiv() + gen.getAsym().getBz());
                asymBus.setGzEquiv(asymBus.getGzEquiv() + gen.getAsym().getGz());
                asymBus.setBnEquiv(asymBus.getBnEquiv() + gen.getAsym().getBn());
                asymBus.setGnEquiv(asymBus.getGnEquiv() + gen.getAsym().getGn());
            }
        }

        if (asymBus.getAsymBusVariableType() == AsymBusVariableType.WYE
                && (Math.abs(asymBus.getBzEquiv()) > EPSILON || Math.abs(asymBus.getGzEquiv()) > EPSILON)) {
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO)
                    .addTerm(new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.ZERO));
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO)
                    .addTerm(new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.ZERO));
        }

        if (Math.abs(asymBus.getGnEquiv()) > EPSILON || Math.abs(asymBus.getBnEquiv()) > EPSILON) {
            ShuntFortescueIxEquationTerm ixShuntNegative = new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.NEGATIVE);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE).addTerm(ixShuntNegative);
            ShuntFortescueIyEquationTerm iyShuntNegative = new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), SequenceType.NEGATIVE);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE).addTerm(iyShuntNegative);
        }

        addTermsForLoad(bus, asymBus, equationSystem, asymBus.getLoadWye0());
        addTermsForLoad(bus, asymBus, equationSystem, asymBus.getLoadDelta0());
        addTermsForLoad(bus, asymBus, equationSystem, asymBus.getLoadWye2());
        addTermsForLoad(bus, asymBus, equationSystem, asymBus.getLoadDelta2());
    }

    @Override
    protected void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // positive sequence
        EquationTerm<AcVariableType, AcEquationType> p1;
        EquationTerm<AcVariableType, AcEquationType> q1;
        EquationTerm<AcVariableType, AcEquationType> p2;
        EquationTerm<AcVariableType, AcEquationType> q2;
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

        if (bus1 == null) {
            throw new IllegalStateException("Line open at one side not yet supported in asymmetric load flow at bus 1 of branch: " + branch.getId());
        }

        if (bus2 == null) {
            throw new IllegalStateException("Line open at one side not yet supported in asymmetric load flow at bus 2 of branch: " + branch.getId());
        }

        LfAsymBus asymBus1 = bus1.getAsym();
        LfAsymBus asymBus2 = bus2.getAsym();

        boolean isBus1Wye = true;
        if (asymBus1.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            isBus1Wye = false;
        }
        int nbPhases1 = asymBus1.getNbExistingPhases();

        boolean isBus2Wye = true;
        if (asymBus2.getAsymBusVariableType() == AsymBusVariableType.DELTA) {
            isBus2Wye = false;
        }
        int nbPhases2 = asymBus2.getNbExistingPhases();

        if (!hasBranchAsymmetry(branch)) {

            if (nbPhases1 < 3 || nbPhases2 < 3) {
                throw new IllegalStateException("Branch with a missing phase cannot be handled as a fortescue balanced model at branch : " + branch.getId());
            }

            // no assymmetry is detected with this line, we handle the equations as Fortescue and decoupled
            if (asymBus1.isPositiveSequenceAsCurrent()) {
                p1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
                q1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
            } else {
                p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
                q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
            }

            if (asymBus2.isPositiveSequenceAsCurrent()) {
                p2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
                q2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
            } else {
                p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
                q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.POSITIVE);
            }

            i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);

            if (branch.getBranchType() == LfBranch.BranchType.LINE) {
                // zero
                if (isBus2Wye) {
                    ixz2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.ZERO);
                    iyz2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.ZERO);
                }

                if (isBus1Wye) {
                    ixz1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.ZERO);
                    iyz1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.ZERO);
                }

                // negative
                ixn1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.NEGATIVE);
                iyn1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.NEGATIVE);
                ixn2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.NEGATIVE);
                iyn2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, SequenceType.NEGATIVE);
            } else {
                // this must be a fortescue transformer
                // zero
                if (isBus2Wye) {
                    ixz2 = new ClosedBranchTfoZeroIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I2X);
                    iyz2 = new ClosedBranchTfoZeroIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I2Y);
                }

                if (isBus1Wye) {
                    ixz1 = new ClosedBranchTfoZeroIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I1X);
                    iyz1 = new ClosedBranchTfoZeroIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I1Y);
                }

                // negative
                ixn1 = new ClosedBranchTfoNegativeIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I1X);
                iyn1 = new ClosedBranchTfoNegativeIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I1Y);
                ixn2 = new ClosedBranchTfoNegativeIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I2X);
                iyn2 = new ClosedBranchTfoNegativeIflowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, AbstractClosedBranchAcFlowEquationTerm.FlowType.I2Y);
            }

        } else {
            // assymmetry is detected with this line, we handle the equations as coupled between the different sequences
            // positive
            if (asymBus1.isPositiveSequenceAsCurrent()) {
                // in this case we try to model the positive sequence as current balances
                p1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.POSITIVE);
                q1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.POSITIVE);
            } else {
                p1 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.POSITIVE);
                q1 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.POSITIVE);
            }

            if (isBus1Wye) {
                if (nbPhases1 == 3) {
                    ixn1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.NEGATIVE);
                    iyn1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.NEGATIVE);
                }
                if (nbPhases1 > 1) {
                    ixz1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.ZERO);
                    iyz1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.ZERO);
                }

            } else {
                ixn1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.ONE, SequenceType.NEGATIVE);
                iyn1 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.ONE, SequenceType.NEGATIVE);
            }

            if (asymBus2.isPositiveSequenceAsCurrent()) {
                p2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.POSITIVE);
                q2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.POSITIVE);
            } else {
                p2 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.POSITIVE);
                q2 = new AsymmetricalClosedBranchCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.POSITIVE);
            }

            if (isBus2Wye) {

                if (nbPhases2 == 3) {
                    ixn2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.NEGATIVE);
                    iyn2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.NEGATIVE);
                }
                if (nbPhases2 > 1) {
                    ixz2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.ZERO);
                    iyz2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.ZERO);
                }
            } else {
                ixn2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.REAL, Side.TWO, SequenceType.NEGATIVE);
                iyn2 = new AsymmetricalClosedBranchCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, Side.TWO, SequenceType.NEGATIVE);
            }
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

        createGeneratorReactivePowerControlBranchEquation(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);

        createTransformerPhaseControlEquations(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);

    }

    public boolean hasBranchAsymmetry(LfBranch branch) {
        boolean asymmetry = false;
        // check the existence of an extension
        if (branch.getBranchType() == LfBranch.BranchType.LINE || branch.getBranchType() == LfBranch.BranchType.TRANSFO_2) {
            LfAsymLine asymBranch = branch.getAsymLine();
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

    public void addTermForLoadEquation(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem, AcEquationType acEquationType, AsymmetricalLoadTerm asymmetricalLoadTerm) {
        if (asymmetricalLoadTerm != null) {
            equationSystem.createEquation(bus, acEquationType).addTerm(asymmetricalLoadTerm);
        }

    }

    public AsymmetricalLoadTerm createAbcLoadEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, ComplexPart complexPart, Fortescue.SequenceType sequenceType, LegConnectionType loadConnectionType, AsymBusLoadType asymBusLoadType) {
        if (asymBusLoadType == AsymBusLoadType.CONSTANT_POWER) {
            return new LoadAbcPowerEquationTerm(bus, variableSet, complexPart, sequenceType, loadConnectionType);
        } else if (asymBusLoadType == AsymBusLoadType.CONSTANT_IMPEDANCE) {
            return new LoadAbcImpedantEquationTerm(bus, variableSet, complexPart, sequenceType, loadConnectionType);
        } else {
            throw new IllegalStateException("Load abc has no supported type at bus : " + bus.getId());
        }
    }

    public void addTermsForLoad(LfBus bus, LfAsymBus asymBus, EquationSystem<AcVariableType, AcEquationType> equationSystem, LfAsymLoad lfAsymLoad) {

        if (lfAsymLoad == null) {
            return;
        }

        AsymBusLoadType lfAsymLoadType = lfAsymLoad.getLoadType();
        AsymmetricalLoadTerm pLoadPositive;
        AsymmetricalLoadTerm qLoadPositive;
        AsymmetricalLoadTerm ixLoadNegative = null;
        AsymmetricalLoadTerm iyLoadNegative = null;
        AsymmetricalLoadTerm ixLoadZero = null;
        AsymmetricalLoadTerm iyLoadZero = null;

        if (asymBus.isFortescueRepresentation()) {

            if (lfAsymLoadType == AsymBusLoadType.CONSTANT_CURRENT || lfAsymLoadType == AsymBusLoadType.CONSTANT_IMPEDANCE) {
                throw new IllegalStateException("Load with fortescue representation other than constant Power not yet handled at bus : " + bus.getId());
            }

            // load modelled as a constant power load in abc phase representation leading to a model depending on vd, vi, vo in fortescue representation
            pLoadPositive = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.POSITIVE, lfAsymLoad.getLoadConnectionType());
            qLoadPositive = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.POSITIVE, lfAsymLoad.getLoadConnectionType());

            ixLoadNegative = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.NEGATIVE, lfAsymLoad.getLoadConnectionType());
            iyLoadNegative = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.NEGATIVE, lfAsymLoad.getLoadConnectionType());

            if (asymBus.getAsymBusVariableType() == AsymBusVariableType.WYE) {
                ixLoadZero = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.ZERO, lfAsymLoad.getLoadConnectionType());
                iyLoadZero = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.ZERO, lfAsymLoad.getLoadConnectionType());
            }
        } else {
            // use of load in ABC sequences
            // for now only S with current moddeling provided
            int nbPhases = asymBus.getNbExistingPhases();
            pLoadPositive = createAbcLoadEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.POSITIVE, lfAsymLoad.getLoadConnectionType(), lfAsymLoadType);
            qLoadPositive = createAbcLoadEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.POSITIVE, lfAsymLoad.getLoadConnectionType(), lfAsymLoadType);

            if (nbPhases == 3) {
                ixLoadNegative = createAbcLoadEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.NEGATIVE, lfAsymLoad.getLoadConnectionType(), lfAsymLoadType);
                iyLoadNegative = createAbcLoadEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.NEGATIVE, lfAsymLoad.getLoadConnectionType(), lfAsymLoadType);
            }

            if (nbPhases > 1) {
                ixLoadZero = createAbcLoadEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.REAL, SequenceType.ZERO, lfAsymLoad.getLoadConnectionType(), lfAsymLoadType);
                iyLoadZero = createAbcLoadEquationTerm(bus, equationSystem.getVariableSet(), ComplexPart.IMAGINARY, SequenceType.ZERO, lfAsymLoad.getLoadConnectionType(), lfAsymLoadType);
            }
        }
        // only terms that are not null are added :
        addTermForLoadEquation(bus, equationSystem, AcEquationType.BUS_TARGET_P, pLoadPositive);
        addTermForLoadEquation(bus, equationSystem, AcEquationType.BUS_TARGET_Q, qLoadPositive);
        addTermForLoadEquation(bus, equationSystem, AcEquationType.BUS_TARGET_IX_NEGATIVE, ixLoadNegative);
        addTermForLoadEquation(bus, equationSystem, AcEquationType.BUS_TARGET_IY_NEGATIVE, iyLoadNegative);
        addTermForLoadEquation(bus, equationSystem, AcEquationType.BUS_TARGET_IX_ZERO, ixLoadZero);
        addTermForLoadEquation(bus, equationSystem, AcEquationType.BUS_TARGET_IY_ZERO, iyLoadZero);

    }
}
