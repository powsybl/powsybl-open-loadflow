package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.openloadflow.network.LfNetwork;

public class LfDcLineImpl extends AbstractLfDcLine {

    public String id;

    public LfDcLineImpl(LfDcNode dcNode1, LfDcNode dcNode2, LfNetwork network, HvdcLine hvdcLine) {
        super(network, dcNode1, dcNode2, hvdcLine);
        this.id = hvdcLine.getId();
    }

    @Override
    public String getId() {
        return this.id;
    }
}
