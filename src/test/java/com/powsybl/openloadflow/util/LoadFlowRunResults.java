package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.powsybl.openloadflow.util.LoadFlowAssert.DELTA_POWER;
import static org.hamcrest.MatcherAssert.assertThat;

public class LoadFlowRunResults<N extends Enum<N>, P extends Enum<P>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowRunResults.class);

    // Map<NetworkDescription, Map <RunningParameters, NetworkResult>>
    private Map<N, Map<P, Network>> networkResultByRunningParametersAndNetworkDescription = new LinkedHashMap<>();

    public enum NetworkItemType { BUS, BRANCH, GENERATOR, LOAD, SVC, SC }

    public Network addLoadFlowReport(N networkDescription, P runningParameters, Network network) {
        networkResultByRunningParametersAndNetworkDescription
                .computeIfAbsent(networkDescription, key -> new LinkedHashMap<>())
                .put(runningParameters, network);
        return network;
    }

    public Network getLoadFlowReport(N networkDescription, P runningParameters) {
        if (networkResultByRunningParametersAndNetworkDescription.containsKey(networkDescription)) {
            return networkResultByRunningParametersAndNetworkDescription.get(networkDescription).get(runningParameters);
        } else {
            return null;
        }
    }

    public Map<N, Map<P, Network>> getNetworkResultByRunningParametersAndNetworkDescription() {
        return networkResultByRunningParametersAndNetworkDescription;
    }

    public void displayAll() {
        for (N n : networkResultByRunningParametersAndNetworkDescription.keySet()) {
            Map<P, Network> networkResultByRunningParameters = networkResultByRunningParametersAndNetworkDescription.get(n);
            for (P p : networkResultByRunningParameters.keySet()) {
                LOGGER.trace(">>> Load flow result on network {} with parameters {}", n, p);
                Network network = getLoadFlowReport(n, p);
                for (Branch branch : network.getBranches()) {
                    logBranch(branch);
                }
                for (Bus bus : network.getBusView().getBuses()) {
                    logBus(bus);
                    for (Generator generator : bus.getGenerators()) {
                        logGenerator(generator);
                    }
                    for (Load load : bus.getLoads()) {
                        logLoad(load);
                    }
                    for (StaticVarCompensator staticVarCompensator : bus.getStaticVarCompensators()) {
                        logStaticVarCompensator(staticVarCompensator);
                    }
                    for (ShuntCompensator shuntCompensator : bus.getShuntCompensators()) {
                        logShuntCompensator(shuntCompensator);
                    }
                }
            }
        }
    }

    private void logBus(Bus bus) {
        if (bus != null) {
            LOGGER.trace("  bus {} : V = {}, Angle = {}", bus.getId(), bus.getV(), bus.getAngle());
        }
    }

    private void logBranch(Branch branch) {
        if (branch != null) {
            LOGGER.trace("  branch {} : terminal1 = (P = {}, Q = {}), terminal2 = (P = {}, Q = {})",
                    branch.getId(), branch.getTerminal1().getP(), branch.getTerminal1().getQ(),
                    branch.getTerminal2().getP(), branch.getTerminal2().getQ());
        }
    }

    private void logGenerator(Generator generator) {
        if (generator != null) {
            LOGGER.trace("    generator {} : P = {}, Q = {}", generator.getId(), -generator.getTerminal().getP(), -generator.getTerminal().getQ());
        }
    }

    private void logLoad(Load load) {
        if (load != null) {
            LOGGER.trace("    load {} : P = {}, Q = {}", load.getId(), load.getTerminal().getP(), load.getTerminal().getQ());
        }
    }

    private void logStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
        if (staticVarCompensator != null) {
            LOGGER.trace("    staticVarCompensator {} : Q = {}", staticVarCompensator.getId(), -staticVarCompensator.getTerminal().getQ());
        }
    }

    private void logShuntCompensator(ShuntCompensator shuntCompensator) {
        if (shuntCompensator != null) {
            LOGGER.trace("    shuntCompensator {} : Q = {}", shuntCompensator.getId(), -shuntCompensator.getTerminal().getQ());
        }
    }

    public void display(N networkDescription, P runningParameters, NetworkItemType networkItemType, String itemId) {
        if (networkResultByRunningParametersAndNetworkDescription.containsKey(networkDescription)) {
            Map<P, Network> networkResultByRunningParameters = networkResultByRunningParametersAndNetworkDescription.get(networkDescription);
            if (networkResultByRunningParameters.containsKey(runningParameters)) {
                Network network = networkResultByRunningParametersAndNetworkDescription.get(networkDescription).get(runningParameters);
                switch (networkItemType) {
                    case BUS:
                        logBus(network.getBusView().getBus(itemId));
                        break;
                    case BRANCH:
                        logBranch(network.getBranch(itemId));
                        break;
                    case GENERATOR:
                        logGenerator(network.getGenerator(itemId));
                        break;
                    case LOAD:
                        logLoad(network.getLoad(itemId));
                        break;
                    case SVC:
                        logStaticVarCompensator(network.getStaticVarCompensator(itemId));
                        break;
                    case SC:
                        logShuntCompensator(network.getShuntCompensator(itemId));
                        break;
                }
            }
        }
    }

    private boolean isClosedLine(Line line) {
        Terminal terminalONE = line.getTerminal(Branch.Side.ONE);
        if (Double.isNaN(terminalONE.getQ())) {
            return false;
        }
        Terminal terminalTWO = line.getTerminal(Branch.Side.TWO);
        if (Double.isNaN(terminalTWO.getQ())) {
            return false;
        }
        return true;
    }

    public void shouldHaveValidSumOfQinLines() {
        for (N n : networkResultByRunningParametersAndNetworkDescription.keySet()) {
            Map<P, Network> networkResultByRunningParameters = networkResultByRunningParametersAndNetworkDescription.get(n);
            for (P p : networkResultByRunningParameters.keySet()) {
                Network network = getLoadFlowReport(n, p);
                for (Bus bus : network.getBusView().getBuses()) {
                    double linesTerminalQ = 0;
                    double sumItemBusQ = 0;
                    for (Generator generator : bus.getGenerators()) {
                        sumItemBusQ -= generator.getTerminal().getQ();
                    }
                    for (Load load : bus.getLoads()) {
                        sumItemBusQ -= load.getTerminal().getQ();
                    }
                    for (StaticVarCompensator staticVarCompensator : bus.getStaticVarCompensators()) {
                        sumItemBusQ -= staticVarCompensator.getTerminal().getQ();
                    }
                    for (ShuntCompensator shuntCompensator : bus.getShuntCompensators()) {
                        sumItemBusQ -= shuntCompensator.getTerminal().getQ();
                    }
                    for (Line line : bus.getLines()) {
                        Terminal terminal = line.getTerminal(bus.getId().substring(0, bus.getId().indexOf("_")));
                        linesTerminalQ += terminal.getQ();
                    }
                    assertThat("sum Q of bus items should be equals to Q line", sumItemBusQ,
                            new LoadFlowAssert.EqualsTo(linesTerminalQ, DELTA_POWER));
                }
            }
        }
    }
}
