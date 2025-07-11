package com.powsybl.openloadflow.ac.networktest;

//import com.powsybl.iidm.network.Bus;
//import com.powsybl.iidm.network.Country;
//import com.powsybl.iidm.network.extensions.ReferenceTerminals;
//import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.openloadflow.network.*;
//import com.powsybl.openloadflow.network.impl.Ref;
//import com.powsybl.security.results.BusResult;

//import java.util.List;
//import java.util.Optional;
//import java.util.stream.Collectors;

public class LfDcNodeImpl extends AbstractLfDcNode {

    String id;

    public LfDcNodeImpl(LfNetwork network, LfNetworkParameters parameters, String id) {
        super(network);
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
