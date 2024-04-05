package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.AcOuterLoopGroupContext;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;

public interface ACOuterLoopGroup {

    OuterLoopStatus runOuterLoops(AcOuterLoopGroupContext groupContext, ReportNode nrReportNode);

}
