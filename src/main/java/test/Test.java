package test;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisRunParameters;

import java.util.List;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
public final class Test {

    private Test() {
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        Network network = Network.read("/home/dupuyflo/Data/recollement-auto-20220302-1910-enrichi.xiidm");
        List<Contingency> contingencies = network.getLineStream()
                .filter(l -> l.getTerminal1().getVoltageLevel().getNominalV() > 300
                        && l.getTerminal2().getVoltageLevel().getNominalV() > 300
                        && l.getTerminal1().getVoltageLevel().getSubstation().orElseThrow().getCountry().orElseThrow() == Country.FR
                        && l.getTerminal2().getVoltageLevel().getSubstation().orElseThrow().getCountry().orElseThrow() == Country.FR)
                .limit(150)
                .map(l -> Contingency.line(l.getId()))
                .toList();
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters()
                .setDcFastMode(true)
                .setThreadCount(1);
        parameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);
        LoadFlowParameters lfParameters = parameters.getLoadFlowParameters();
        OpenLoadFlowParameters.create(lfParameters);
        lfParameters.setDc(false);
        SecurityAnalysisRunParameters runParameters = new SecurityAnalysisRunParameters().setSecurityAnalysisParameters(parameters);
        SecurityAnalysis.find("OpenLoadFlow").run(network, contingencies, runParameters);
        System.out.println("Done in " + (System.currentTimeMillis() - start) + " ms");
    }
}
