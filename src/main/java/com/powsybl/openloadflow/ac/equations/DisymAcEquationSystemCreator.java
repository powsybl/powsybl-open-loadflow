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

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class DisymAcEquationSystemCreator extends AcEquationSystemCreator {

    boolean isUseOfCurrents = true;

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
        // TODO : In a first version, extensions of busses to carry additional info related to inverse and homopolar sequences are created here
        //  this will reduce the number of modified files between AC and a first Disym implementation
        AsymBus asymBus = new AsymBus(bus); // bus is used to reach potential bus extensions in iidm that will be used to setup asymbus
        bus.setProperty(AsymBus.PROPERTY_ASYMMETRICAL, asymBus);

        if (isUseOfCurrents) {
            var ixh = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_HOMOPOLAR);
            asymBus.setIxHomopolar(ixh);
            var iyh = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_HOMOPOLAR);
            asymBus.setIyHomopolar(iyh);

            var ixi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_INVERSE);
            asymBus.setIxInverse(ixi);
            var iyi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_INVERSE);
            asymBus.setIyInverse(iyi);

            ixi.setActive(true);
            iyi.setActive(true);
            ixh.setActive(true);
            iyh.setActive(true);
        } else {
            var ph = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR);
            asymBus.setPHomopolar(ph);
            var qh = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR);
            asymBus.setQHomopolar(qh);

            var pi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE);
            asymBus.setPInverse(pi);
            var qi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE);
            asymBus.setQInverse(qi);

            pi.setActive(true);
            qi.setActive(true);
            ph.setActive(true);
            qh.setActive(true);
        }

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
            if (isUseOfCurrents) {
                ShuntFortescueIxEquationTerm ixShuntHomo = new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_HOMOPOLAR).addTerm(ixShuntHomo);
                ShuntFortescueIyEquationTerm iyShuntHomo = new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_HOMOPOLAR).addTerm(iyShuntHomo);
            } else {
                ShuntFortescuePowerReactiveEquationTerm qShuntHomo = new ShuntFortescuePowerReactiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR).addTerm(qShuntHomo);
                ShuntFortescuePowerActiveEquationTerm pShuntHomo = new ShuntFortescuePowerActiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR).addTerm(pShuntHomo);
            }
        }

        if (Math.abs(asymBus.getgNegativeEquivalent()) > epsilon || Math.abs(asymBus.getbNegativeEquivalent()) > epsilon) {
            if (isUseOfCurrents) {
                ShuntFortescueIxEquationTerm ixShuntInv = new ShuntFortescueIxEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_INVERSE).addTerm(ixShuntInv);
                ShuntFortescueIyEquationTerm iyShuntInv = new ShuntFortescueIyEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_INVERSE).addTerm(iyShuntInv);
            } else {
                ShuntFortescuePowerReactiveEquationTerm qShuntInv = new ShuntFortescuePowerReactiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE).addTerm(qShuntInv);
                ShuntFortescuePowerActiveEquationTerm pShuntInv = new ShuntFortescuePowerActiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE).addTerm(pShuntInv);
            }
        }

        boolean isAbcConstantLoad = true; // set false here and in LfBusImpl to have a load such that Sd = P+jQ  So = 0 and Si = 0
        boolean isShuntLoad = false;

        if (Math.abs(bus.getLoadTargetP()) > epsilon || Math.abs(bus.getLoadTargetQ()) > epsilon) {
            // load modelled as a constant power load in abc phase representation leading to a model depending on vd, vi, vo in fortescue representation
            if (isUseOfCurrents) {
                if (isAbcConstantLoad) {
                    LoadFortescuePowerEquationTerm ixLoadHomo = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 0, LoadFortescuePowerEquationTerm.LoadEquationTermType.CURRENT);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_HOMOPOLAR).addTerm(ixLoadHomo);
                    LoadFortescuePowerEquationTerm pLoadDirect = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 1, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P).addTerm(pLoadDirect);
                    LoadFortescuePowerEquationTerm ixLoadInv = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 2, LoadFortescuePowerEquationTerm.LoadEquationTermType.CURRENT);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IX_INVERSE).addTerm(ixLoadInv);
                    LoadFortescuePowerEquationTerm iyLoadHomo = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 0, LoadFortescuePowerEquationTerm.LoadEquationTermType.CURRENT);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_HOMOPOLAR).addTerm(iyLoadHomo);
                    LoadFortescuePowerEquationTerm qLoadDirect = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 1, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(qLoadDirect);
                    LoadFortescuePowerEquationTerm iyLoadInv = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 2, LoadFortescuePowerEquationTerm.LoadEquationTermType.CURRENT);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_IY_INVERSE).addTerm(iyLoadInv);
                } else if (isShuntLoad) {
                    // load modelled as a constant shunt in fortescue representation
                    /*LoadShuntActiveEquationTerm pLoadShuntHomo = new LoadShuntActiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR).addTerm(pLoadShuntHomo);
                    LoadShuntActiveEquationTerm pLoadShuntInv = new LoadShuntActiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE).addTerm(pLoadShuntInv);
                    LoadShuntReactiveEquationTerm qLoadShuntHomo = new LoadShuntReactiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR).addTerm(qLoadShuntHomo);
                    LoadShuntReactiveEquationTerm qLoadShuntInv = new LoadShuntReactiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE).addTerm(qLoadShuntInv);*/
                }
            } else {
                if (isAbcConstantLoad) {
                    LoadFortescuePowerEquationTerm pLoadHomo = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 0, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR).addTerm(pLoadHomo);
                    LoadFortescuePowerEquationTerm pLoadDirect = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 1, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P).addTerm(pLoadDirect);
                    LoadFortescuePowerEquationTerm pLoadInv = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), true, 2, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE).addTerm(pLoadInv);
                    LoadFortescuePowerEquationTerm qLoadHomo = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 0, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR).addTerm(qLoadHomo);
                    LoadFortescuePowerEquationTerm qLoadDirect = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 1, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(qLoadDirect);
                    LoadFortescuePowerEquationTerm qLoadInv = new LoadFortescuePowerEquationTerm(bus, equationSystem.getVariableSet(), false, 2, LoadFortescuePowerEquationTerm.LoadEquationTermType.POWER);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE).addTerm(qLoadInv);

                } else if (isShuntLoad) {
                    // load modelled as a constant shunt in fortescue representation
                    /*LoadShuntActiveEquationTerm pLoadShuntHomo = new LoadShuntActiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR).addTerm(pLoadShuntHomo);
                    LoadShuntActiveEquationTerm pLoadShuntInv = new LoadShuntActiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE).addTerm(pLoadShuntInv);
                    LoadShuntReactiveEquationTerm qLoadShuntHomo = new LoadShuntReactiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR).addTerm(qLoadShuntHomo);
                    LoadShuntReactiveEquationTerm qLoadShuntInv = new LoadShuntReactiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                    equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE).addTerm(qLoadShuntInv);*/
                }
            }
        }
    }

    protected void createImpedantBranchCurrent(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // TODO :
        // direct sequence
        EquationTerm<AcVariableType, AcEquationType> p1 = null;
        EquationTerm<AcVariableType, AcEquationType> q1 = null;
        EquationTerm<AcVariableType, AcEquationType> p2 = null;
        EquationTerm<AcVariableType, AcEquationType> q2 = null;
        EquationTerm<AcVariableType, AcEquationType> i1 = null;
        EquationTerm<AcVariableType, AcEquationType> i2 = null;

        // homopolar sequence
        EquationTerm<AcVariableType, AcEquationType> ixh1 = null;
        EquationTerm<AcVariableType, AcEquationType> iyh1 = null;
        EquationTerm<AcVariableType, AcEquationType> ixh2 = null;
        EquationTerm<AcVariableType, AcEquationType> iyh2 = null;

        // inverse sequence
        EquationTerm<AcVariableType, AcEquationType> ixi1 = null;
        EquationTerm<AcVariableType, AcEquationType> iyi1 = null;
        EquationTerm<AcVariableType, AcEquationType> ixi2 = null;
        EquationTerm<AcVariableType, AcEquationType> iyi2 = null;

        boolean deriveA1 = isDeriveA1(branch);
        boolean deriveR1 = isDeriveR1(branch);

        // check the existence of an extension
        AsymLine asymLine = (AsymLine) branch.getProperty(AsymLine.PROPERTY_ASYMMETRICAL);
        boolean disconnectionAsymmetry = false;
        if (asymLine != null) {
            disconnectionAsymmetry = asymLine.isDisconnectionAsymmetryDetected();
            System.out.println("Disymmetry detected  for branch : " + branch.getId() + " = " + disconnectionAsymmetry);
        } else {
            System.out.println("No disymmetry detected  for branch : " + branch.getId() + " with no asym extension");
        }

        if (bus1 != null && bus2 != null) {

            if (!disconnectionAsymmetry) {
                // no assymmetry is detected with this line, we handle the equations as decoupled
                // direct
                p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
                q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
                p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
                q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
                i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
                i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);

                // homopolar
                ixh1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.HOMOPOLAR);
                iyh1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.HOMOPOLAR);
                ixh2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.HOMOPOLAR);
                iyh2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.HOMOPOLAR);

                // inverse
                ixi1 = new ClosedBranchI1xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.INVERSE);
                iyi1 = new ClosedBranchI1yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.INVERSE);
                ixi2 = new ClosedBranchI2xFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.INVERSE);
                iyi2 = new ClosedBranchI2yFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.INVERSE);
            } else {
                // assymmetry is detected with this line, we handle the equations as coupled between the different sequences
                // direct
                p1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 1);
                q1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 1);
                p2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 1);
                q2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 1);

                // homopolar
                ixh1 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 0);
                iyh1 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 0);
                ixh2 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 0);
                iyh2 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 0);

                // inverse
                ixi1 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 2);
                iyi1 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 2);
                ixi2 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 2);
                iyi2 = new ClosedBranchDisymCoupledCurrentEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 2);
            }

        } else if (bus1 != null) {
            throw new IllegalStateException("Line open at one side not yet supported in disym load flow: ");
            //p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            //q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            //i1 = new OpenBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
        } else if (bus2 != null) {
            throw new IllegalStateException("Line open at one side not yet supported in disym load flow: ");
            //p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            //q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            //i2 = new OpenBranchSide1CurrentMagnitudeEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
        }

        // direct
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

        // homopolar
        if (ixh1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_IX_HOMOPOLAR)
                    .orElseThrow()
                    .addTerm(ixh1);
            //branch.setP1(p1); // TODO : check not necessary
        }
        if (iyh1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_IY_HOMOPOLAR)
                    .orElseThrow()
                    .addTerm(iyh1);
            //branch.setQ1(q1); // TODO : check not necessary
        }
        if (ixh2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_IX_HOMOPOLAR)
                    .orElseThrow()
                    .addTerm(ixh2);
            //branch.setP2(ph2);
        }
        if (iyh2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_IY_HOMOPOLAR)
                    .orElseThrow()
                    .addTerm(iyh2);
            //branch.setQ2(qh2);
        }

        // inverse
        if (ixi1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_IX_INVERSE)
                    .orElseThrow()
                    .addTerm(ixi1);
            //branch.setP1(pi1);
        }
        if (iyi1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_IY_INVERSE)
                    .orElseThrow()
                    .addTerm(iyi1);
            //branch.setQ1(qi1);
        }
        if (ixi2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_IX_INVERSE)
                    .orElseThrow()
                    .addTerm(ixi2);
            //branch.setP2(pi2);
        }
        if (iyi2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_IY_INVERSE)
                    .orElseThrow()
                    .addTerm(iyi2);
            //branch.setQ2(qi2);
        }

        createReactivePowerControlBranchEquation(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);

        createTransformerPhaseControlEquations(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);

    }

    protected void createImpedantBranchPower(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
        // direct sequence
        EquationTerm<AcVariableType, AcEquationType> p1 = null;
        EquationTerm<AcVariableType, AcEquationType> q1 = null;
        EquationTerm<AcVariableType, AcEquationType> p2 = null;
        EquationTerm<AcVariableType, AcEquationType> q2 = null;
        EquationTerm<AcVariableType, AcEquationType> i1 = null;
        EquationTerm<AcVariableType, AcEquationType> i2 = null;

        // homopolar sequence
        EquationTerm<AcVariableType, AcEquationType> ph1 = null;
        EquationTerm<AcVariableType, AcEquationType> qh1 = null;
        EquationTerm<AcVariableType, AcEquationType> ph2 = null;
        EquationTerm<AcVariableType, AcEquationType> qh2 = null;

        // inverse sequence
        EquationTerm<AcVariableType, AcEquationType> pi1 = null;
        EquationTerm<AcVariableType, AcEquationType> qi1 = null;
        EquationTerm<AcVariableType, AcEquationType> pi2 = null;
        EquationTerm<AcVariableType, AcEquationType> qi2 = null;

        boolean deriveA1 = isDeriveA1(branch);
        boolean deriveR1 = isDeriveR1(branch);

        // check the existence of an extension
        AsymLine asymLine = (AsymLine) branch.getProperty(AsymLine.PROPERTY_ASYMMETRICAL);
        boolean disconnectionAsymmetry = false;
        if (asymLine != null) {
            disconnectionAsymmetry = asymLine.isDisconnectionAsymmetryDetected();
            System.out.println("Disymmetry detected  for branch : " + branch.getId() + " = " + disconnectionAsymmetry);
        } else {
            System.out.println("No disymmetry detected  for branch : " + branch.getId() + " with no asym extension");
        }

        if (bus1 != null && bus2 != null) {

            if (!disconnectionAsymmetry) {
                // no assymmetry is detected with this line, we handle the equations as decoupled
                // direct
                p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
                q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
                p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
                q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.DIRECT);
                i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
                i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);

                // homopolar
                ph1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.HOMOPOLAR);
                qh1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.HOMOPOLAR);
                ph2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.HOMOPOLAR);
                qh2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.HOMOPOLAR);

                // inverse
                pi1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.INVERSE);
                qi1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.INVERSE);
                pi2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.INVERSE);
                qi2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, DisymAcSequenceType.INVERSE);
            } else {
                // assymmetry is detected with this line, we handle the equations as coupled between the different sequences
                // direct
                p1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 1);
                q1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 1);
                p2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 1);
                q2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 1);

                // homopolar
                ph1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 0);
                qh1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 0);
                ph2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 0);
                qh2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 0);

                // inverse
                pi1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 2);
                qi1 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 2);
                pi2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 2);
                qi2 = new ClosedBranchDisymCoupledPowerEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 2);
            }

        } else if (bus1 != null) {
            throw new IllegalStateException("Line open at one side not yet supported in disym load flow: ");
            //p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            //q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
            //i1 = new OpenBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, equationSystem.getVariableSet(), deriveA1, deriveR1);
        } else if (bus2 != null) {
            throw new IllegalStateException("Line open at one side not yet supported in disym load flow: ");
            //p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            //q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
            //i2 = new OpenBranchSide1CurrentMagnitudeEquationTerm(branch, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1);
        }

        // direct
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

        // homopolar
        if (ph1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P_HOMOPOLAR)
                    .orElseThrow()
                    .addTerm(ph1);
            //branch.setP1(p1); // TODO : check not necessary
        }
        if (qh1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_Q_HOMOPOLAR)
                    .orElseThrow()
                    .addTerm(qh1);
            //branch.setQ1(q1); // TODO : check not necessary
        }
        if (ph2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P_HOMOPOLAR)
                    .orElseThrow()
                    .addTerm(ph2);
            //branch.setP2(ph2);
        }
        if (qh2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_Q_HOMOPOLAR)
                    .orElseThrow()
                    .addTerm(qh2);
            //branch.setQ2(qh2);
        }

        // inverse
        if (pi1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_P_INVERSE)
                    .orElseThrow()
                    .addTerm(pi1);
            //branch.setP1(pi1);
        }
        if (qi1 != null) {
            equationSystem.getEquation(bus1.getNum(), AcEquationType.BUS_TARGET_Q_INVERSE)
                    .orElseThrow()
                    .addTerm(qi1);
            //branch.setQ1(qi1);
        }
        if (pi2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_P_INVERSE)
                    .orElseThrow()
                    .addTerm(pi2);
            //branch.setP2(pi2);
        }
        if (qi2 != null) {
            equationSystem.getEquation(bus2.getNum(), AcEquationType.BUS_TARGET_Q_INVERSE)
                    .orElseThrow()
                    .addTerm(qi2);
            //branch.setQ2(qi2);
        }

        createReactivePowerControlBranchEquation(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);

        createTransformerPhaseControlEquations(branch, bus1, bus2, equationSystem, deriveA1, deriveR1);
    }

    @Override
    protected void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {

        if (isUseOfCurrents) {
            createImpedantBranchCurrent(branch, bus1, bus2, equationSystem);
        } else {
            createImpedantBranchPower(branch, bus1, bus2, equationSystem);
        }
    }
}
