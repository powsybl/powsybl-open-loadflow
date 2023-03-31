/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.Extensions.AsymBus;
import com.powsybl.openloadflow.network.Extensions.AsymGenerator;
import com.powsybl.openloadflow.network.Extensions.AsymLine;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Fortescue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class DisymAcEquationSystemCreator extends AcEquationSystemCreator {

    public DisymAcEquationSystemCreator(LfNetwork network) {
        super(network);
    }

    public DisymAcEquationSystemCreator(LfNetwork network, AcEquationSystemCreationParameters creationParameters) {
        super(network, creationParameters);
    }

    @Override
    protected void createBusEquation(LfBus bus, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        super.createBusEquation(bus, equationSystem);

        // addition of asymmetric equations, supposing that existing v, theta, p and q are linked to the direct sequence
        AsymBus asymBus = new AsymBus(bus); // bus is used to reach potential bus extensions in iidm that will be used to setup asymbus
        bus.setProperty(AsymBus.PROPERTY_ASYMMETRICAL, asymBus);

        var ixh = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO);
        asymBus.setIxZero(ixh);
        var iyh = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO);
        asymBus.setIyZero(iyh);

        var ixi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE);
        asymBus.setIxNegative(ixi);
        var iyi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE);
        asymBus.setIyNegative(iyi);

        ixi.setActive(true);
        iyi.setActive(true);
        ixh.setActive(true);
        iyh.setActive(true);

        double epsilon = 0.00001;

        // Handle generators at bus for homopolar and inverse
        for (LfGenerator gen : bus.getGenerators()) {
            // if there is at least one generating unit that is voltage controlling we model the equivalent in inverse and homopolar
            // with a large admittance yg = g +jb to model a close connection of the bus to the ground (E_homopolar = 0 E_Inverse = 0)
            if (gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.REMOTE_REACTIVE_POWER
                    || gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE) {

                AsymGenerator asymGenerator = (AsymGenerator) gen.getProperty(AsymGenerator.PROPERTY_ASYMMETRICAL);
                if (asymGenerator != null) {
                    asymBus.setbZeroEquivalent(asymBus.getbZeroEquivalent() + asymGenerator.getb0());
                    asymBus.setgZeroEquivalent(asymBus.getgZeroEquivalent() + asymGenerator.getg0());
                    asymBus.setbNegativeEquivalent(asymBus.getbNegativeEquivalent() + asymGenerator.getb2());
                    asymBus.setgNegativeEquivalent(asymBus.getgNegativeEquivalent() + asymGenerator.getg2());
                }
            }
        }

        if (Math.abs(asymBus.getbZeroEquivalent()) > epsilon || Math.abs(asymBus.getgZeroEquivalent()) > epsilon) {
            ShuntFortescueIxEquationTerm ixShuntHomo = new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), Fortescue.SequenceType.ZERO);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO).addTerm(ixShuntHomo);
            ShuntFortescueIyEquationTerm iyShuntHomo = new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), Fortescue.SequenceType.ZERO);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO).addTerm(iyShuntHomo);
        }

        if (Math.abs(asymBus.getgNegativeEquivalent()) > epsilon || Math.abs(asymBus.getbNegativeEquivalent()) > epsilon) {
            ShuntFortescueIxEquationTerm ixShuntInv = new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), Fortescue.SequenceType.NEGATIVE);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE).addTerm(ixShuntInv);
            ShuntFortescueIyEquationTerm iyShuntInv = new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), Fortescue.SequenceType.NEGATIVE);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE).addTerm(iyShuntInv);
        }

        if (Math.abs(bus.getLoadTargetP()) > epsilon || Math.abs(bus.getLoadTargetQ()) > epsilon) {
            // load modelled as a constant power load in abc phase representation leading to a model depending on vd, vi, vo in fortescue representation
            LoadFortescuePowerEquationTerm ixLoadHomo = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 0, LoadFortescuePowerEquationTerm.LoadEquationTermType.CURRENT);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_ZERO).addTerm(ixLoadHomo);
            LoadFortescuePowerEquationTerm pLoadDirect = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 1, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P).addTerm(pLoadDirect);
            LoadFortescuePowerEquationTerm ixLoadInv = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 2, LoadFortescuePowerEquationTerm.LoadEquationTermType.CURRENT);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_NEGATIVE).addTerm(ixLoadInv);
            LoadFortescuePowerEquationTerm iyLoadHomo = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 0, LoadFortescuePowerEquationTerm.LoadEquationTermType.CURRENT);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_ZERO).addTerm(iyLoadHomo);
            LoadFortescuePowerEquationTerm qLoadDirect = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 1, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(qLoadDirect);
            LoadFortescuePowerEquationTerm iyLoadInv = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 2, LoadFortescuePowerEquationTerm.LoadEquationTermType.CURRENT);
            equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_NEGATIVE).addTerm(iyLoadInv);
        }
    }

    @Override
    protected void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // TODO :
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

        boolean deriveA1 = isDeriveA1(branch);
        boolean deriveR1 = isDeriveR1(branch);

        boolean asymmetry = hasBranchAsymmetry(branch);

        if (bus1 != null && bus2 != null) {

            if (!asymmetry) {
                // no assymmetry is detected with this line, we handle the equations as decoupled
                // positive
                p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
                q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
                p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
                q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
                i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
                i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);

                // zero
                ixz1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.ZERO);
                iyz1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.ZERO);
                ixz2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.ZERO);
                iyz2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.ZERO);

                // negative
                ixn1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.NEGATIVE);
                iyn1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.NEGATIVE);
                ixn2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.NEGATIVE);
                iyn2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, Fortescue.SequenceType.NEGATIVE);
            } else {
                // assymmetry is detected with this line, we handle the equations as coupled between the different sequences
                // positive
                p1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 1);
                q1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 1);
                p2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 1);
                q2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 1);

                // zero
                ixz1 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 0);
                iyz1 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 0);
                ixz2 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 0);
                iyz2 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 0);

                // negative
                ixn1 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 2);
                iyn1 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 2);
                ixn2 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 2);
                iyn2 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 2);
            }

        } else if (bus1 != null) {
            throw new IllegalStateException("Line open at one side not yet supported in asymmetric load flow: ");
        } else if (bus2 != null) {
            throw new IllegalStateException("Line open at one side not yet supported in asymmetric load flow: ");
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
        // check the existence of an extension
        AsymLine asymLine = (AsymLine) branch.getProperty(AsymLine.PROPERTY_ASYMMETRICAL);
        boolean asymmetry = false;
        if (asymLine != null) {
            asymmetry = asymLine.isAdmittanceAsymmetryDetected() || asymLine.isDisconnectionAsymmetryDetected();
        }
        return asymmetry;
    }
}
