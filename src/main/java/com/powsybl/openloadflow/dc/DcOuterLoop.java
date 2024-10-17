package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.lf.outerloop.OuterLoop;

public interface DcOuterLoop extends OuterLoop<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcOuterLoopContext> {
}
