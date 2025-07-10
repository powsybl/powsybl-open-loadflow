package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.LfHvdcImpl;

import java.util.ArrayList;
import java.util.List;

public class LfHvdcV2Impl extends LfHvdcImpl {

    public List<LfDcNode> dcNodes = new ArrayList<>();
    public List<LfDcLine> dcLines = new ArrayList<>();


    public LfHvdcV2Impl(String id, LfBus bus1, LfBus bus2, LfNetwork network, HvdcLine hvdcLine, List<LfDcNode> dcNodes, List<LfDcLine> dcLines) {
        super(id, bus1, bus2, network, hvdcLine, false);
        this.dcNodes = dcNodes;
        this.dcLines = dcLines;
        for(LfDcNode dcNode : dcNodes){
            network.addDcNode(dcNode);
        }
        for(LfDcLine dcLine : dcLines){
            network.addDcLine(dcLine);
        }
    }

    public List<LfDcNode> getDcNodes(){return this.dcNodes;}
    public List<LfDcLine> getDcLines(){return this.dcLines;}

}
