package com.powsybl.openloadflow.network;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
public class LfAcDcNetwork extends LfNetwork{
    private List<LfNetwork> acSubNetworks;
    private List<LfNetwork> dcSubNetworks;
    protected List<LfBus> slackBuses = new ArrayList<>();
    private final List<LfBus> acDcReferenceBuses = new ArrayList<>();

    public LfAcDcNetwork(List<LfNetwork> acNetworks, List<LfNetwork> dcNetworks) {
        //TODO : find a better way to implement super class
        super(acNetworks.getFirst());
        for(LfNetwork network : acNetworks) {
            network.getBuses().forEach(this::addBus);
            network.getBranches().forEach(this::addBranch);
            network.getAreas().forEach(this::addArea);
            network.getHvdcs().forEach(this::addHvdc);
        }
        for(LfNetwork network : dcNetworks) {
            network.getDcNodes().forEach(this::addDcNode);
            network.getDcLines().forEach(this::addDcLine);
        }
        acSubNetworks = acNetworks;
        dcSubNetworks = dcNetworks;

        System.out.println(this.getBuses());
        System.out.println(this.getBranches());
        System.out.println(this.getDcNodes());
        System.out.println(this.getDcLines());
        System.out.println(this.getVoltageSourceConverters());
    }

    @Override
    protected void invalidateSlackAndReference() {
        acSubNetworks.forEach(LfNetwork::invalidateSlackAndReference);
        dcSubNetworks.forEach(LfNetwork::invalidateSlackAndReference);
    }

    @Override
    public void updateSlackBusesAndReferenceBus() {
        if (!acSubNetworks.isEmpty()) {
            for (LfNetwork acSubNetwork : acSubNetworks) {
                acSubNetwork.updateSlackBusesAndReferenceBus();
                for (LfBus bus : acSubNetwork.slackBuses) {
                    LfBus slackBus = this.getBusById(bus.getId());
                    if (!this.slackBuses.contains(slackBus)) {
                        slackBus.setSlack(true);
                        this.slackBuses.add(slackBus);
                    }
                }
                LfBus referenceBus = this.getBusById(acSubNetwork.referenceBus.getId());
                if (!acDcReferenceBuses.contains(referenceBus)) {
                    referenceBus.setReference(true);
                    this.acDcReferenceBuses.add(referenceBus);
                }
            }
        }
    }

    @Override
    public List<LfBus> getSlackBuses() {
        updateSlackBusesAndReferenceBus();
        return slackBuses;
    }

    @Override
    public LfBus getReferenceBus() {
        updateSlackBusesAndReferenceBus();
        //FIXME: which bus do we return ?
        return acDcReferenceBuses.getFirst();
    }

    public List<LfNetwork> getAcSubNetworks() {
        return acSubNetworks;
    }

    public List<LfNetwork> getDcSubNetworks() {
        return dcSubNetworks;
    }
}


