package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.DcLine;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.impl.Ref;

import java.util.Objects;

public class LfDcLineImpl extends AbstractLfDcLine {

    private final Ref<DcLine> dcLineRef;

    public LfDcLineImpl(LfDcNode dcNode1, LfDcNode dcNode2, LfNetwork network, DcLine dcLine, LfNetworkParameters parameters) {
        super(network, dcNode1, dcNode2, dcLine.getR());
        this.dcLineRef = Ref.create(dcLine, parameters.isCacheEnabled());
    }

    public static LfDcLineImpl create(DcLine dcLine, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2,
                                      LfNetworkParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(dcLine);
        Objects.requireNonNull(parameters);
        return new LfDcLineImpl(dcNode1, dcNode2, network, dcLine, parameters);
    }

    private DcLine getDcLine() {
        return dcLineRef.get();
    }

    @Override
    public String getId() {
        return getDcLine().getId();
    }
}
