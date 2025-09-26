package com.powsybl.openloadflow.network.impl;
import com.powsybl.iidm.network.DcNode;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfNetworkStateUpdateParameters;

import java.util.Objects;

public class LfDcNodeImpl extends AbstractLfDcNode {

    private final Ref<DcNode> dcNodeRef;

    private boolean isGrounded = false;

    public LfDcNodeImpl(DcNode dcNode, LfNetwork network, double nominalV, LfNetworkParameters parameters) {
        super(network, nominalV, dcNode.getV());
        this.dcNodeRef = Ref.create(dcNode, parameters.isCacheEnabled());
    }

    public static LfDcNodeImpl create(DcNode dcNode, LfNetwork network, LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(dcNode);
        Objects.requireNonNull(parameters);
        return new LfDcNodeImpl(dcNode, network, dcNode.getNominalV(), parameters);
    }

    private DcNode getDcNode() {
        return dcNodeRef.get();
    }

    @Override
    public String getId() {
        return getDcNode().getId();
    }

    @Override
    public boolean isGrounded() {
        return isGrounded;
    }

    @Override
    public void setGround(boolean isGrounded) {
        this.isGrounded = isGrounded;
    }

    @Override
    public void updateState(LfNetworkStateUpdateParameters parameters) {
        var dcNode = getDcNode();
        dcNode.setV(v);
    }
}
