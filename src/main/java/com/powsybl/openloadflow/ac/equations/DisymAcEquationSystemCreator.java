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
        AsymBus asymBus = new AsymBus();
        bus.setProperty(AsymBus.PROPERTY_ASYMMETRICAL, asymBus);

        var ph = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR);
        asymBus.setPHomopolar(ph);
        var qh = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR);
        asymBus.setQHomopolar(qh);

        var pi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE);
        asymBus.setPHomopolar(pi);
        var qi = equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE);
        asymBus.setQHomopolar(qi);

        pi.setActive(true);
        qi.setActive(true);
        ph.setActive(true);
        qh.setActive(true);

        // Handle generators at bus for homopolar and inverse
        for (LfGenerator gen : bus.getGenerators()) {
            // if there is at least one generating unit that is voltage controlling we model the equivalent in inverse and homopolar
            // with a large admittance yg = g +jb to model a close connection of the bus to the ground (E_homopolar = 0 E_Inverse = 0)
            if (gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.REMOTE_REACTIVE_POWER
                    || gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE) {
                // homopolar sequence
                GeneratorShuntReactiveEquationTerm qShuntGenHomo = new GeneratorShuntReactiveEquationTerm(gen, bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR).addTerm(qShuntGenHomo);
                GeneratorShuntActiveEquationTerm pShuntGenHomo = new GeneratorShuntActiveEquationTerm(gen, bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR).addTerm(pShuntGenHomo);

                // inverse sequence
                GeneratorShuntReactiveEquationTerm qShuntGenInv = new GeneratorShuntReactiveEquationTerm(gen, bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE).addTerm(qShuntGenInv);
                GeneratorShuntActiveEquationTerm pShuntGenInv = new GeneratorShuntActiveEquationTerm(gen, bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE).addTerm(pShuntGenInv);

                break;
            }
        }
        double epsilon = 0.00001;

        boolean isAbcConstantLoad = true; // set false here and in LfBusImpl to have a load such that Sd = P+jQ  So = 0 and Si = 0
        boolean isShuntLoad = false;

        if (Math.abs(bus.getLoadTargetP()) > epsilon || Math.abs(bus.getLoadTargetQ()) > epsilon) {
            if (isAbcConstantLoad) {
                // load modelled as a constant power load in abc phase representation leading to a model depending on vd, vi, vo in fortescue representation
                FortescueLoadEquationTerm pLoadHomo = new FortescueLoadEquationTerm(bus, equationSystem.getVariableSet(), true, 0);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR).addTerm(pLoadHomo);
                FortescueLoadEquationTerm pLoadDirect = new FortescueLoadEquationTerm(bus, equationSystem.getVariableSet(), true, 1);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P).addTerm(pLoadDirect);
                FortescueLoadEquationTerm pLoadInv = new FortescueLoadEquationTerm(bus, equationSystem.getVariableSet(), true, 2);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE).addTerm(pLoadInv);
                FortescueLoadEquationTerm qLoadHomo = new FortescueLoadEquationTerm(bus, equationSystem.getVariableSet(), false, 0);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR).addTerm(qLoadHomo);
                FortescueLoadEquationTerm qLoadDirect = new FortescueLoadEquationTerm(bus, equationSystem.getVariableSet(), false, 1);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q).addTerm(qLoadDirect);
                FortescueLoadEquationTerm qLoadInv = new FortescueLoadEquationTerm(bus, equationSystem.getVariableSet(), false, 2);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE).addTerm(qLoadInv);
            } else if (isShuntLoad) {
                // load modelled as a constant shunt in fortescue representation
                LoadShuntActiveEquationTerm pLoadShuntHomo = new LoadShuntActiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_HOMOPOLAR).addTerm(pLoadShuntHomo);
                LoadShuntActiveEquationTerm pLoadShuntInv = new LoadShuntActiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_P_INVERSE).addTerm(pLoadShuntInv);
                LoadShuntReactiveEquationTerm qLoadShuntHomo = new LoadShuntReactiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.HOMOPOLAR);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_HOMOPOLAR).addTerm(qLoadShuntHomo);
                LoadShuntReactiveEquationTerm qLoadShuntInv = new LoadShuntReactiveEquationTerm(bus, equationSystem.getVariableSet(), DisymAcSequenceType.INVERSE);
                equationSystem.createEquation(bus, AcEquationType.BUS_TARGET_Q_INVERSE).addTerm(qLoadShuntInv);
            }
        }
    }

    @Override
    protected void createImpedantBranch(LfBranch branch, LfBus bus1, LfBus bus2, EquationSystem<AcVariableType, AcEquationType> equationSystem) {
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
                p1 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 1);
                q1 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 1);
                p2 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 1);
                q2 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 1);

                // homopolar
                ph1 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 0);
                qh1 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 0);
                ph2 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 0);
                qh2 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 0);

                // inverse
                pi1 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, true, 2);
                qi1 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, true, 2);
                pi2 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, true, false, 2);
                qi2 = new ClosedBranchDisymCoupledEquationTerm(branch, bus1, bus2, equationSystem.getVariableSet(), deriveA1, deriveR1, false, false, 2);
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
}
