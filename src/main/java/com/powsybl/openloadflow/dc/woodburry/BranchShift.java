package com.powsybl.openloadflow.dc.woodburry;

import com.powsybl.openloadflow.network.LfBranch;

public class BranchShift extends NetworkShift {

    private enum BranchShiftType {
        OPENNING,
        CLOSING
    }

    private final BranchShiftType shiftType;

    BranchShift(LfBranch branch, BranchShiftType shiftType) {
        super(branch.getId());
        this.shiftType = shiftType;
    }

    public final ShiftType getType()
    {
        return ShiftType.BRANCH_ADMITTANCE_SHIFT;
    }

    public final BranchShiftType getShiftType() {
        return shiftType;
    }
}
