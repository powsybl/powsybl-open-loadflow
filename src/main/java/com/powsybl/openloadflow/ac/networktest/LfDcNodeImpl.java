package com.powsybl.openloadflow.ac.networktest;
import com.powsybl.openloadflow.network.LfNetwork;

public class LfDcNodeImpl extends AbstractLfDcNode {

    String id;

    public LfDcNodeImpl(LfNetwork network, double nominalV, String id) {
        super(network, nominalV);
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isParticipating() {
        return true;
    }

}
