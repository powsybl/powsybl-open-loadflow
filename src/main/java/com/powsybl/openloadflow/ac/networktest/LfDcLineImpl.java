package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfNetwork;

public class LfDcLineImpl extends AbstractLfDcLine {

    public String id;

    public LfDcLineImpl(LfDcNode dcNode1, LfDcNode dcNode2, LfNetwork network, double r, String id) {
        super(network, dcNode1, dcNode2, r);
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }
}
