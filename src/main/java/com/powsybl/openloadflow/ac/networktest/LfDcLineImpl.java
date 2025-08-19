package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.DcLine;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.LfBranchImpl;
import com.powsybl.openloadflow.network.impl.Ref;

import java.util.Objects;

public class LfDcLineImpl extends AbstractLfDcLine {

    public String id;

    private final Ref<DcLine> dcLineRef;

    public LfDcLineImpl(LfDcNode dcNode1, LfDcNode dcNode2, LfNetwork network, DcLine dcLine, LfNetworkParameters parameters) {
        super(network, dcNode1, dcNode2, dcLine.getR());
        this.dcLineRef = Ref.create(dcLine, parameters.isCacheEnabled());
    }

    public static LfDcLineImpl create(DcLine dcLine, LfNetwork network, LfDcNode dcNode1, LfDcNode dcNode2, LfTopoConfig topoConfig,
                                      LfNetworkParameters parameters) {
        Objects.requireNonNull(dcLine);
        Objects.requireNonNull(network);
        Objects.requireNonNull(topoConfig);
        Objects.requireNonNull(parameters);
        LfDcLineImpl lfDcLine = new LfDcLineImpl(dcNode1, dcNode2, network, dcLine, parameters );
        return lfDcLine;
    }

    private DcLine getDcLine() {
        return dcLineRef.get();
    }

    @Override
    public String getId() {
        return getDcLine().getId();
    }
}
