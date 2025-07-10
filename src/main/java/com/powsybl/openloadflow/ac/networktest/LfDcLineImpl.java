package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Objects;
import java.util.Optional;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

public class LfDcLineImpl extends AbstractLfDcLine {

    public String id;

    public LfDcLineImpl(LfDcNode dcNode1, LfDcNode dcNode2, LfNetwork network, LfNetworkParameters parameters, HvdcLine hvdcLine) {
        super(network, dcNode1, dcNode2, parameters, hvdcLine);
        this.id = hvdcLine.getId();
    }

    @Override
    public String getId() {
        return this.id;
    }
}
