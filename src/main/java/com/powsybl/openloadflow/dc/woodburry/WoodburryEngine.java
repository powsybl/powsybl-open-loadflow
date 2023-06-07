package com.powsybl.openloadflow.dc.woodburry;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.sensi.DcSensitivityAnalysis;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class WoodburryEngine {
    public List<Pair<LfBranch, Double>> compute(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem, LUDecomposition lu, List<NetworkShift> networkShifts, WoodburryElement woodburryElement) {
        List<Pair<LfBranch, Double>> result = null;
        int nbShifts = networkShifts.size();

        // Theta 2: states in case of +1/-1 MW at the terminals of the shifted elements
        DenseMatrix stateAdmVar = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), nbShifts);

        // Initializing stateAdmVar (rhs)
        int j = 0;
        for (NetworkShift shift : networkShifts) {
            if (shift.getType() == NetworkShift.ShiftType.BRANCH_ADMITTANCE_SHIFT){
                LfBranch lfBranch = lfNetwork.getBranchById(shift.getElementId());
                if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                    continue;
                }
                LfBus bus1 = lfBranch.getBus1();
                LfBus bus2 = lfBranch.getBus2();
                if (bus1.isSlack()) {
                    Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                    stateAdmVar.set(p.getColumn(), j, -1);
                } else if (bus2.isSlack()) {
                    Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                    stateAdmVar.set(p.getColumn(), j, 1);
                } else {
                    Equation<DcVariableType, DcEquationType> p1 = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                    Equation<DcVariableType, DcEquationType> p2 = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                    stateAdmVar.set(p1.getColumn(), j, 1);
                    stateAdmVar.set(p2.getColumn(), j, -1);
                }
            } else {
                throw new IllegalStateException("Unknown shift type " + shift.getType());
            }
            j++;
        }

        // Solving
        lu.solveTransposed(stateAdmVar);

        if (nbShifts == 1) {
            LfBranch lfBranch = lfNetwork.getBranchById(networkShifts.get(0).getElementId());
            // TODO HG from setAlphas
        } else {
           
        }

        return result;
    }
}
