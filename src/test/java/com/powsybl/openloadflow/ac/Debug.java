package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;

import java.io.File;
import java.nio.file.Path;

public final class Debug {

    private Debug() {

    }

    public static void main(String[] args) {
        String path = "/home/vidaldid/support/AC_HVDC_2/urnuuid8bdd39d7-7bc9-fd35-6556-f5ccbf63418d.xiidm";
        Network toDebug = Network.read(Path.of(path));
        LoadFlow.Runner olfRunner = LoadFlow.find("OpenLoadFlow");

        LoadFlowParameters params = JsonLoadFlowParameters.read(new File(new File(path).getParent(), "OLFParams.json").toPath());

        params.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);

        LoadFlowResult result = olfRunner.run(toDebug, params);
        System.out.println(result);

//        System.out.println(toDebug.getBusBreakerView().getBus("MEES P11").getQ());
//        System.out.println(olfRunner.getVersion());
//        toDebug.write("XIIDM", null, new File(new File(path).getParent(), "debug.xiidm").toPath());
    }
}
