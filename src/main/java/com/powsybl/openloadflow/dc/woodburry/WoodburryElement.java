package com.powsybl.openloadflow.dc.woodburry;

import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfBranch;

import java.util.List;

public class WoodburryElement {

    private final TargetVector<DcVariableType, DcEquationType> targetVector;
    private final List<LfBranch> flows;

    WoodburryElement(TargetVector<DcVariableType, DcEquationType> targetVector, List<LfBranch> flows) {
        this.targetVector = targetVector;
        this.flows = flows;
    }

    public final TargetVector<DcVariableType, DcEquationType> getTargetVector() {
        return targetVector;
    }

    public final List<LfBranch> getFlows() {
        return flows;
    }
}
