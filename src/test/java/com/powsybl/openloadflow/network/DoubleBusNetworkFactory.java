package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.*;

import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBusNetworkFactory {

    /**
     * vl1 : 400 kv Double bus Voltage Level
     * vlgens : 20 kv Two separated bus with a generator each and a tranformer connected to VL1
     *          One of the generators is connected also to a dangling transformer and
     *          monnitors voltage on the dangling terminal
     * vlload : 63 kV. A load connected by a transforer to vl1
     * @return
     */
    public static Network create() {
        Network network = NetworkFactory.findDefault().createNetwork("double-bus", "source");
        AtomicInteger node =  new AtomicInteger();
        Substation substation = network.newSubstation()
                .setId("station")
                .add();
        VoltageLevel vl = substation.newVoltageLevel()
                .setNominalV(400)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .setId("vl1")
                .add();

        BusbarSection bbs1 = vl.getNodeBreakerView().newBusbarSection()
                .setNode(node.incrementAndGet())
                .setId("bbs1")
                .add();

        int bbs1Node = node.get();

        BusbarSection bbs2 = vl.getNodeBreakerView().newBusbarSection()
                .setNode(node.incrementAndGet())
                .setId("bbs2")
                .add();

        int bbs2Node = node.get();

        vl.getNodeBreakerView().newSwitch()
                .setRetained(true)
                .setId("discbbs1")
                .setKind(SwitchKind.DISCONNECTOR)
                .setNode1(bbs1Node)
                .setNode2(node.incrementAndGet())
                .add();

        vl.getNodeBreakerView().newSwitch()
                .setRetained(true)
                .setId("bbs1-2")
                .setKind(SwitchKind.BREAKER)
                .setNode1(node.get())
                .setNode2(node.incrementAndGet())
                .add();

        vl.getNodeBreakerView().newSwitch()
                .setRetained(true)
                .setId("discbbs2")
                .setKind(SwitchKind.DISCONNECTOR)
                .setNode1(node.get())
                .setNode2(bbs2Node)
                .add();

        VoltageLevel vlGens = substation.newVoltageLevel()
                .setNominalV(20)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .setId("vlgen")
                .add();

        createGroup(node, "1", substation, vlGens, vl, bbs1, bbs2, false);
        // Invert bbs to exchange remoe regulated terminal
        createGroup(node, "2", substation, vlGens, vl, bbs2, bbs1, true);

        VoltageLevel vlLoads = substation.newVoltageLevel()
                .setNominalV(63)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .setId("vlload")
                .add();
        createLoad(node, "1", substation, vlLoads, vl, bbs2, bbs1);



        return network;
    }

    static private void createGroup(AtomicInteger node, String suffix, Substation substation, VoltageLevel vlGens,
                                    VoltageLevel vl, BusbarSection bbs1, BusbarSection bbs2, boolean withDandlingControlTerminal) {

        BusbarSection bbsgen = vlGens.getNodeBreakerView()
                .newBusbarSection()
                .setId("bbsGen" + suffix)
                .setNode(node.incrementAndGet())
                .add();

        Generator g = vlGens.newGenerator()
                .setId("g" + suffix)
                .setTargetP(50)
                .setMaxP(100)
                .setMinP(10)
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(bbs1.getTerminal()) // Will be changed after tranformer is created
                .setTargetV(400)
                .setNode(node.incrementAndGet())
                .add();

        vlGens.getNodeBreakerView().newSwitch()
                .setId("sg" + suffix)
                .setNode1(g.getTerminal().getNodeBreakerView().getNode())
                .setNode2(bbsgen.getTerminal().getNodeBreakerView().getNode())
                .setKind(SwitchKind.BREAKER)
                .setRetained(true)
                .add();


        TwoWindingsTransformer tw = substation.newTwoWindingsTransformer()
                .setVoltageLevel1(vl.getId())
                .setNode1(node.incrementAndGet())
                .setVoltageLevel2(vlGens.getId())
                .setNode2(node.incrementAndGet())
                .setId("twg"+suffix)
                .setR(0)
                .setX(0.1)
                .add();


        g.setRegulatingTerminal(tw.getTerminal1());

        vlGens.getNodeBreakerView().newInternalConnection()
                .setNode1(tw.getTerminal(vlGens.getId()).getNodeBreakerView().getNode())
                .setNode2(bbsgen.getTerminal().getNodeBreakerView().getNode())
                .add();

        if (withDandlingControlTerminal) {
            TwoWindingsTransformer twDangling = substation.newTwoWindingsTransformer()
                    .setVoltageLevel1(vl.getId())
                    .setNode1(node.incrementAndGet())
                    .setVoltageLevel2(vlGens.getId())
                    .setNode2(node.incrementAndGet())
                    .setId("twg" + suffix + "_dangling")
                    .setR(0)
                    .setX(0.1)
                    .add();

            vlGens.getNodeBreakerView().newInternalConnection()
                    .setNode1(twDangling.getTerminal(vlGens.getId()).getNodeBreakerView().getNode())
                    .setNode2(bbsgen.getTerminal().getNodeBreakerView().getNode())
                    .add();

            g.setRegulatingTerminal(twDangling.getTerminal1());
        }

        vl.getNodeBreakerView().newSwitch()
                .setId(tw.getId() + bbs1.getId())
                .setNode1(tw.getTerminal(vl.getId()).getNodeBreakerView().getNode())
                .setNode2(node.incrementAndGet())
                .setKind(SwitchKind.BREAKER)
                .setRetained(true)
                .add();

        vl.getNodeBreakerView().newSwitch()
                .setId("disc" + tw.getId() + bbs1.getId())
                .setNode1(node.get())
                .setNode2(bbs1.getTerminal().getNodeBreakerView().getNode())
                .setKind(SwitchKind.DISCONNECTOR)
                .setRetained(true)
                .add();

        vl.getNodeBreakerView().newSwitch()
                .setId(tw.getId() + bbs2.getId())
                .setNode1(tw.getTerminal(vl.getId()).getNodeBreakerView().getNode())
                .setNode2(node.incrementAndGet())
                .setKind(SwitchKind.BREAKER)
                .setRetained(true)
                .add();

        vl.getNodeBreakerView().newSwitch()
                .setId("disc" + tw.getId() + bbs2.getId())
                .setNode1(node.get())
                .setNode2(bbs2.getTerminal().getNodeBreakerView().getNode())
                .setKind(SwitchKind.DISCONNECTOR)
                .setRetained(true)
                .add();

    }

    static private void createLoad(AtomicInteger node, String suffix, Substation substation, VoltageLevel vlLoads,
                                   VoltageLevel vl, BusbarSection bbs1, BusbarSection bbs2) {

        BusbarSection bbsLoad = vlLoads.getNodeBreakerView()
                .newBusbarSection()
                .setId("bbsLoad" + suffix)
                .setNode(node.incrementAndGet())
                .add();

        Load l = vlLoads.newLoad()
                .setId("l" + suffix)
                .setP0(50)
                .setQ0(10)
                .setNode(node.incrementAndGet())
                .add();

        vlLoads.getNodeBreakerView().newSwitch()
                .setId("sl" + suffix)
                .setNode1(l.getTerminal().getNodeBreakerView().getNode())
                .setNode2(node.incrementAndGet())
                .setKind(SwitchKind.BREAKER)
                .setRetained(true)
                .add();

        vlLoads.getNodeBreakerView().newSwitch()
                .setId("disc" + "sl" + suffix)
                .setNode1(node.get())
                .setNode2(bbsLoad.getTerminal().getNodeBreakerView().getNode())
                .setKind(SwitchKind.DISCONNECTOR)
                .setRetained(true)
                .add();


        TwoWindingsTransformer tw = substation.newTwoWindingsTransformer()
                .setVoltageLevel1(vl.getId())
                .setNode1(node.incrementAndGet())
                .setVoltageLevel2(vlLoads.getId())
                .setNode2(node.incrementAndGet())
                .setId("twl"+suffix)
                .setR(0)
                .setX(0.1)
                .add();

        vlLoads.getNodeBreakerView().newInternalConnection()
                .setNode1(tw.getTerminal(vlLoads.getId()).getNodeBreakerView().getNode())
                .setNode2(bbsLoad.getTerminal().getNodeBreakerView().getNode())
                .add();


        vl.getNodeBreakerView().newSwitch()
                .setId(tw.getId() + bbs1.getId())
                .setNode1(tw.getTerminal(vl.getId()).getNodeBreakerView().getNode())
                .setNode2(node.incrementAndGet())
                .setKind(SwitchKind.BREAKER)
                .setRetained(true)
                .add();

        vl.getNodeBreakerView().newSwitch()
                .setId("disc" + tw.getId() + bbs1.getId())
                .setNode1(node.get())
                .setNode2(bbs1.getTerminal().getNodeBreakerView().getNode())
                .setKind(SwitchKind.DISCONNECTOR)
                .setRetained(true)
                .add();

        vl.getNodeBreakerView().newSwitch()
                .setId(tw.getId() + bbs2.getId())
                .setNode1(tw.getTerminal(vl.getId()).getNodeBreakerView().getNode())
                .setNode2(node.incrementAndGet())
                .setKind(SwitchKind.BREAKER)
                .setRetained(true)
                .add();

        vl.getNodeBreakerView().newSwitch()
                .setId("disc" + tw.getId() + bbs2.getId())
                .setNode1(node.get())
                .setNode2(bbs2.getTerminal().getNodeBreakerView().getNode())
                .setKind(SwitchKind.DISCONNECTOR)
                .setRetained(true)
                .add();

    }
}
