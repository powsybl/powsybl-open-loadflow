package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractNamedEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.Extensions.AsymBus;
import com.powsybl.openloadflow.network.LfBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class FortescueLoadEquationTerm extends AbstractNamedEquationTerm<AcVariableType, AcEquationType> {

    protected final LfBus bus;

    // direct
    protected final Variable<AcVariableType> vVar;

    protected final Variable<AcVariableType> phVar;

    // inverse
    protected final Variable<AcVariableType> vVarInv;

    protected final Variable<AcVariableType> phVarInv;

    // homopolar
    protected final Variable<AcVariableType> vVarHom;

    protected final Variable<AcVariableType> phVarHom;

    protected final List<Variable<AcVariableType>> variables = new ArrayList<>();

    private final boolean isActive; // true if active power asked, false if reactive power asked
    private final int sequenceNum; // 0 = hompolar, 1 = direct, 2 = inverse

    public FortescueLoadEquationTerm(LfBus bus, VariableSet<AcVariableType> variableSet, boolean isActive, int sequenceNum) {
        super(true);
        Objects.requireNonNull(bus);
        Objects.requireNonNull(variableSet);

        this.bus = bus;
        this.isActive = isActive;
        this.sequenceNum = sequenceNum;

        vVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        phVar = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI);

        vVarInv = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_INVERSE);
        phVarInv = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_INVERSE);

        vVarHom = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V_HOMOPOLAR);
        phVarHom = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_PHI_HOMOPOLAR);

        variables.add(vVar);
        variables.add(phVar);
        variables.add(vVarInv);
        variables.add(phVarInv);
        variables.add(vVarHom);
        variables.add(phVarHom);
    }

    public double ph(int g) {
        switch (g) {
            case 0: // homopolar
                return sv.get(phVarHom.getRow());

            case 1: // direct
                return sv.get(phVar.getRow());

            case 2: // inverse
                return sv.get(phVarInv.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    public double v(int g) {
        switch (g) {
            case 0: // homopolar
                return sv.get(vVarHom.getRow());

            case 1: // direct
                return sv.get(vVar.getRow());

            case 2: // inverse
                return sv.get(vVarInv.getRow());

            default:
                throw new IllegalStateException("Unknown variable: ");
        }
    }

    public static double numerator(boolean isActive, int sequenceNum, FortescueLoadEquationTerm eqTerm) {
        // For a balanced constant power load: Sa = Sb = Sc = Sabc
        // By noting : denom = vd^3 + vi^3 + vo^3 - 3*vd*vo*vi
        // So = (vo^3-vd*vo*vi) * Sabc / denom
        // Sd = (vd^3-vd*vo*vi) * Sabc / denom
        // Si = (vi^3-vd*vo*vi) * Sabc / denom
        // Given the expressions of the numerator, we directly use the GenericLoadTerm T1Load to build the numerator and its derivative

        // For an unbalanced constant power load: Sa != Sb != Sc
        // The previous expressions get the following general form:
        // So =   1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vo^3 - 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vd*vi*vo
        //      + 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vi²*vo
        //      + 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vd²*vo
        //      - 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vo²*vi
        //      - 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vo²*vd

        // Sd =   1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vd^3 - 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vd*vi*vo
        //      + 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vo²*vd
        //      + 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vi²*vd
        //      - 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vd²*vo
        //      - 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vd²*vi

        // Si =   1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vi^3 - 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vd*vi*vo
        //      + 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vd²*vi
        //      + 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vo²*vi
        //      - 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vi²*vd
        //      - 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vi²*vo

        double busP = eqTerm.bus.getLoadTargetP();
        double busQ = eqTerm.bus.getLoadTargetQ();

        AsymBus asymBus = (AsymBus) eqTerm.bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + eqTerm.bus.getId());
        }
        boolean isLoadBalanced = asymBus.isBalancedLoad();

        if (isLoadBalanced) {
            if (isActive) {
                return GenericLoadTerm.t1Load(sequenceNum, sequenceNum, sequenceNum, busP, busQ, eqTerm).getFirst() - GenericLoadTerm.t1Load(0, 1, 2, busP, busQ, eqTerm).getFirst();
            } else {
                return GenericLoadTerm.t1Load(sequenceNum, sequenceNum, sequenceNum, busP, busQ, eqTerm).getSecond() - GenericLoadTerm.t1Load(0, 1, 2, busP, busQ, eqTerm).getSecond();
            }
        } else {

            double paPbPc = (asymBus.getPc() + asymBus.getPa() + asymBus.getPb()) / 3;
            double qaQbQc = (asymBus.getQa() + asymBus.getQb() + asymBus.getQc()) / 3;
            double paPamPbmPc = (2 * asymBus.getPa() - asymBus.getPb() - asymBus.getPc()) / 6;
            double qaQamQbmQc = (2 * asymBus.getQa() - asymBus.getQc() - asymBus.getQb()) / 6;
            double sqPbmPc = Math.sqrt(3) / 6 * (asymBus.getPb() - asymBus.getPc());
            double sqQbmQc = Math.sqrt(3) / 6 * (asymBus.getQb() - asymBus.getQc());
            if (isActive) {
                //numSo = 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vo^3 - 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vd*vi*vo
                //      + 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vi²*vo
                //      + 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vd²*vo
                //      - 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vo²*vi
                //      - 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vo²*vd

                //numSd = 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vd^3 - 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vd*vi*vo
                //      + 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vo²*vd
                //      + 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vi²*vd
                //      - 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vd²*vo
                //      - 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vd²*vi

                //numSi = 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vi^3 - 1/3*(Pa+Pb+Pc + j(Qa+Qb+Qc))*vd*vi*vo
                //      + 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vd²*vi
                //      + 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vo²*vi
                //      - 1/6*(2Pa-Pb-Pc-sqrt3*Qb+sqrt3*Qc + j(2Qa-Qb-Qc+sqrt3*Pb-sqrt3*Pc))*vi²*vd
                //      - 1/6*(2Pa-Pb-Pc+sqrt3*Qb-sqrt3*Qc + j(2Qa-Qb-Qc-sqrt3*Pb+sqrt3*Pc))*vi²*vo
                return GenericLoadTerm.t1Load(sequenceNum, sequenceNum, sequenceNum, paPbPc, qaQbQc, eqTerm).getFirst()
                        - GenericLoadTerm.t1Load(0, 1, 2, paPbPc, qaQbQc, eqTerm).getFirst()
                        + GenericLoadTerm.t1Load(sequenceNum, getSequenceShift(sequenceNum, 2), getSequenceShift(sequenceNum, 2), paPamPbmPc + sqQbmQc, qaQamQbmQc - sqPbmPc, eqTerm).getFirst()
                        + GenericLoadTerm.t1Load(sequenceNum, getSequenceShift(sequenceNum, 1), getSequenceShift(sequenceNum, 1), paPamPbmPc - sqQbmQc, qaQamQbmQc + sqPbmPc, eqTerm).getFirst()
                        - GenericLoadTerm.t1Load(sequenceNum, sequenceNum, getSequenceShift(sequenceNum, 2), paPamPbmPc - sqQbmQc, qaQamQbmQc + sqPbmPc, eqTerm).getFirst()
                        - GenericLoadTerm.t1Load(sequenceNum, sequenceNum, getSequenceShift(sequenceNum, 1), paPamPbmPc + sqQbmQc, qaQamQbmQc - sqPbmPc, eqTerm).getFirst();
            } else {
                return GenericLoadTerm.t1Load(sequenceNum, sequenceNum, sequenceNum, paPbPc, qaQbQc, eqTerm).getSecond()
                        - GenericLoadTerm.t1Load(0, 1, 2, paPbPc, qaQbQc, eqTerm).getSecond()
                        + GenericLoadTerm.t1Load(sequenceNum, getSequenceShift(sequenceNum, 2), getSequenceShift(sequenceNum, 2), paPamPbmPc + sqQbmQc, qaQamQbmQc - sqPbmPc, eqTerm).getSecond()
                        + GenericLoadTerm.t1Load(sequenceNum, getSequenceShift(sequenceNum, 1), getSequenceShift(sequenceNum, 1), paPamPbmPc - sqQbmQc, qaQamQbmQc + sqPbmPc, eqTerm).getSecond()
                        - GenericLoadTerm.t1Load(sequenceNum, sequenceNum, getSequenceShift(sequenceNum, 2), paPamPbmPc - sqQbmQc, qaQamQbmQc + sqPbmPc, eqTerm).getSecond()
                        - GenericLoadTerm.t1Load(sequenceNum, sequenceNum, getSequenceShift(sequenceNum, 1), paPamPbmPc + sqQbmQc, qaQamQbmQc - sqPbmPc, eqTerm).getSecond();
            }
        }
    }

    public static double denominator(FortescueLoadEquationTerm eqTerm) {
        return GenericLoadTerm.denom(eqTerm);
    }

    public static int getSequenceShift(int sequence, int shift) {
        // return a number of sequence equal to {0,1,2} with an input = sequence + shift
        // example : if sequence = 1 and shift = 2 it will return 0 = 3 [3]
        // input can only be sequence = {0, 1, 2} and shift = {1,2}
        //System.out.println("shift sequence =  " + sequence + " shift = " + shift);
        int sum = sequence + shift;
        if (sum < 3) {
            //System.out.println("return =  " + sum);
            return sum;
        } else {
            int sum1 = sum - 3;
            //System.out.println("return =  " + sum1);
            return sum - 3;
        }
    }

    public static double pq(boolean isActive, int sequenceNum, FortescueLoadEquationTerm eqTerm) {
        return numerator(isActive, sequenceNum, eqTerm) / denominator(eqTerm);
    }

    public static double dpq(boolean isActive, int sequenceNum, FortescueLoadEquationTerm eqTerm, Variable<AcVariableType> derVariable) {

        double busP = eqTerm.bus.getLoadTargetP();
        double busQ = eqTerm.bus.getLoadTargetQ();

        // we use the formula: (f/g)' = (f'*g-f*g')/g²
        double f = numerator(isActive, sequenceNum, eqTerm);
        double df;

        AsymBus asymBus = (AsymBus) eqTerm.bus.getProperty(AsymBus.PROPERTY_ASYMMETRICAL);
        if (asymBus == null) {
            throw new IllegalStateException("unexpected null pointer for an asymmetric bus " + eqTerm.bus.getId());
        }
        boolean isLoadBalanced = asymBus.isBalancedLoad();

        if (isLoadBalanced) {
            if (isActive) {
                df = GenericLoadTerm.dt1Load(sequenceNum, sequenceNum, sequenceNum, busP, busQ, eqTerm, derVariable).getFirst() - GenericLoadTerm.dt1Load(0, 1, 2, busP, busQ, eqTerm, derVariable).getFirst();
            } else {
                df = GenericLoadTerm.dt1Load(sequenceNum, sequenceNum, sequenceNum, busP, busQ, eqTerm, derVariable).getSecond() - GenericLoadTerm.dt1Load(0, 1, 2, busP, busQ, eqTerm, derVariable).getSecond();
            }
        } else {
            double paPbPc = (asymBus.getPc() + asymBus.getPa() + asymBus.getPb()) / 3.;
            double qaQbQc = (asymBus.getQa() + asymBus.getQb() + asymBus.getQc()) / 3.;
            double paPamPbmPc = (2. * asymBus.getPa() - asymBus.getPb() - asymBus.getPc()) / 6.;
            double qaQamQbmQc = (2. * asymBus.getQa() - asymBus.getQc() - asymBus.getQb()) / 6.;
            double sqPbmPc = Math.sqrt(3.) / 6. * (asymBus.getPb() - asymBus.getPc());
            double sqQbmQc = Math.sqrt(3.) / 6. * (asymBus.getQb() - asymBus.getQc());
            if (isActive) {
                df = GenericLoadTerm.dt1Load(sequenceNum, sequenceNum, sequenceNum, paPbPc, qaQbQc, eqTerm, derVariable).getFirst()
                        - GenericLoadTerm.dt1Load(0, 1, 2, paPbPc, qaQbQc, eqTerm, derVariable).getFirst()
                        + GenericLoadTerm.dt1Load(sequenceNum, getSequenceShift(sequenceNum, 2), getSequenceShift(sequenceNum, 2), paPamPbmPc + sqQbmQc, qaQamQbmQc - sqPbmPc, eqTerm, derVariable).getFirst()
                        + GenericLoadTerm.dt1Load(sequenceNum, getSequenceShift(sequenceNum, 1), getSequenceShift(sequenceNum, 1), paPamPbmPc - sqQbmQc, qaQamQbmQc + sqPbmPc, eqTerm, derVariable).getFirst()
                        - GenericLoadTerm.dt1Load(sequenceNum, sequenceNum, getSequenceShift(sequenceNum, 2), paPamPbmPc - sqQbmQc, qaQamQbmQc + sqPbmPc, eqTerm, derVariable).getFirst()
                        - GenericLoadTerm.dt1Load(sequenceNum, sequenceNum, getSequenceShift(sequenceNum, 1), paPamPbmPc + sqQbmQc, qaQamQbmQc - sqPbmPc, eqTerm, derVariable).getFirst();
            } else {
                df = GenericLoadTerm.dt1Load(sequenceNum, sequenceNum, sequenceNum, paPbPc, qaQbQc, eqTerm, derVariable).getSecond()
                        - GenericLoadTerm.dt1Load(0, 1, 2, paPbPc, qaQbQc, eqTerm, derVariable).getSecond()
                        + GenericLoadTerm.dt1Load(sequenceNum, getSequenceShift(sequenceNum, 2), getSequenceShift(sequenceNum, 2), paPamPbmPc + sqQbmQc, qaQamQbmQc - sqPbmPc, eqTerm, derVariable).getSecond()
                        + GenericLoadTerm.dt1Load(sequenceNum, getSequenceShift(sequenceNum, 1), getSequenceShift(sequenceNum, 1), paPamPbmPc - sqQbmQc, qaQamQbmQc + sqPbmPc, eqTerm, derVariable).getSecond()
                        - GenericLoadTerm.dt1Load(sequenceNum, sequenceNum, getSequenceShift(sequenceNum, 2), paPamPbmPc - sqQbmQc, qaQamQbmQc + sqPbmPc, eqTerm, derVariable).getSecond()
                        - GenericLoadTerm.dt1Load(sequenceNum, sequenceNum, getSequenceShift(sequenceNum, 1), paPamPbmPc + sqQbmQc, qaQamQbmQc - sqPbmPc, eqTerm, derVariable).getSecond();
            }
        }

        double g = denominator(eqTerm);
        double dg = GenericLoadTerm.dDenom(eqTerm, derVariable);
        if (Math.abs(g) < 0.000001) {
            throw new IllegalStateException("Unexpected singularity of load derivative: ");
        }
        return (df * g - dg * f) / (g * g);
    }

    @Override
    public double eval() {
        return pq(isActive, sequenceNum, this);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        return dpq(isActive, sequenceNum, this, variable);
    }

    @Override
    protected String getName() {
        return "ac_pq_load";
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BUS;
    } // TODO : check if acceptable

    @Override
    public List<Variable<AcVariableType>> getVariables() {
        return variables;
    }

    @Override
    public int getElementNum() {
        return bus.getNum(); // TODO : check if acceptable
    }
}
