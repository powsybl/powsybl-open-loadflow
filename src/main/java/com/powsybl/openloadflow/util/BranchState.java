package com.powsybl.openloadflow.util;

import com.powsybl.openloadflow.network.*;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BranchState {
    private final double a1;
    private final double r1;
    private final double x;
    private final double r;
    private final DiscretePhaseControl phaseControl;
    private final DiscreteVoltageControl voltageControl;

    public BranchState(LfBranch b) {
        PiModel piModel = b.getPiModel();
        a1 = piModel.getA1();
        r1 = piModel.getR1();
        x = piModel.getX();
        r = piModel.getR();
        phaseControl = b.getDiscretePhaseControl();
        voltageControl = b.getDiscreteVoltageControl();
    }

    public void restoreBranchState(LfBranch branch) {
        restoreBranchActiveState(branch);
        branch.setDiscretePhaseControl(phaseControl);
        branch.setDiscreteVoltageControl(voltageControl);
    }

    public void restoreBranchActiveState(LfBranch branch) {
        PiModel piModel = branch.getPiModel();
        piModel.setA1(a1);
        piModel.setR1(r1);
        piModel.setR(r);
        piModel.setX(x);
    }

    /**
     * Get the map of the states of given branches, indexed by the branch itself
     * @param branches the bus for which the state is returned
     * @return the map of the states of given branches, indexed by the branch itself
     */
    public static Map<LfBranch, BranchState> createBranchStates(Collection<LfBranch> branches) {
        return branches.stream().collect(Collectors.toMap(Function.identity(), BranchState::new));
    }

    /**
     * Set the branch states based on the given map of states
     * @param branchStates the map containing the branches states, indexed by branches
     */
    public static void restoreBranchStates(Map<LfBranch, BranchState> branchStates) {
        branchStates.forEach((b, state) -> state.restoreBranchState(b));
    }

    /**
     * Set the branch states based on the given map of states
     * @param branchStates the map containing the branches states, indexed by branches
     */
    public static void restoreBranchActiveStates(Map<LfBranch, BranchState> branchStates) {
        branchStates.forEach((b, state) -> state.restoreBranchActiveState(b));
    }
}
