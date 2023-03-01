/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class ConnectedComponentNetworkFactory extends AbstractLoadFlowNetworkFactory {

    /**
     * <pre>
     * b1 ----------+
     * |            |
     * b2 -------- b3
     *              |
     * b5 -------- b4
     * |            |
     * b6 ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoCcLinkedByASingleLine() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+
     * |                 |
     * b2 (g2) -------- b3 (d3)
     * |                 |
     * b4 (d4) -------- b5 (d5)
     * |                 |
     * b6 (g6) ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoCcLinkedByTwoLines() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b5, "l35", 0.1f);
        createLine(network, b2, b4, "l24", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);

        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+
     * |                 |
     * b2 (g2) -------- b3 (d3)
     * |                 |
     * b4 (d4) -------- b5 (d5)
     * |                 |
     * b6 (g6) ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoCcLinkedByTwoLinesWithAdditionnalGens() {
        Network network = createTwoCcLinkedByTwoLines();
        Bus b3 = network.getBusBreakerView().getBus("b3");
        createGenerator(b3, "g3", 2);
        createLoad(b3, "d3_bis", 2);
        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+
     * |                 |
     * b2 (g2) -------- b3 (d3)
     *                   |
     * b5 (d5) -------- b4 (d4)
     * |                 |
     * PTC               |
     * |                 |
     * b6 (g6) ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoCcWithATransformerLinkedByASingleLine() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "test_s", "b5");
        Bus b6 = createBus(network, "test_s", "b6");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        TwoWindingsTransformer twt = createTransformer(network, "test_s", b5, b6, "l56", 0.1f, 1d);
        twt.newPhaseTapChanger().setTapPosition(0)
           .beginStep()
           .setX(0.1f)
           .setAlpha(1)
           .endStep()
           .add();
        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+
     * |                 |
     * b2 (g2) -------- b3 (d3)
     *                   |
     *                   PTC
     *                   |
     * b5 (d5) -------- b4 (d4)
     * |                 |
     * b6 (g6) ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoCcLinkedByATransformer() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "test_s", "b3");
        Bus b4 = createBus(network, "test_s", "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        TwoWindingsTransformer twt = createTransformer(network, "test_s", b3, b4, "l34", 0.1f, 1d);
        twt.newPhaseTapChanger().setTapPosition(0)
            .beginStep()
            .setX(0.1f)
            .setAlpha(1)
            .endStep()
            .add();
        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+
     * |                 |
     * b2 (g2) -------- b3 (g3)
     *                   |
     * b5 (d5) -------- b4
     * |                 |
     * b6 (d6) ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoComponentWithGeneratorOnOneSide() {
        Network network = createTwoCcLinkedByASingleLine();
        Bus b2 = network.getBusBreakerView().getBus("b2");
        Bus b6 = network.getBusBreakerView().getBus("b6");
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b5 = network.getBusBreakerView().getBus("b5");
        Bus b3 = network.getBusBreakerView().getBus("b3");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        createGenerator(b2, "g2", 3);
        createGenerator(b3, "g3", 2);
        createLoad(b1, "d1", 2);
        createLoad(b5, "d5", 2);
        createLoad(b6, "d6", 1);
        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+
     * |                 |
     * b2 (g2) -------- b3 (d3)
     *                   |
     * b5 (d5) -------- b4 (d4)
     * |                 |
     * b6 (g6) ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoComponentWithGeneratorAndLoad() {
        Network network = createTwoCcLinkedByASingleLine();
        Bus b2 = network.getBusBreakerView().getBus("b2");
        Bus b6 = network.getBusBreakerView().getBus("b6");
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b5 = network.getBusBreakerView().getBus("b5");
        Bus b3 = network.getBusBreakerView().getBus("b3");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+                               b6 (g6) ----------+
     * |                 |                                |                |
     * b2 (g2) -------- b3 (d3) -------- b4 (d4) -------- b5 (d5) -------- b7 (d7)
     *                                    |
     *                                   b8 (d8) --------- b9 (d9)
     *                                    |                 |
     *                                   b10 (g10) ---------+
     * </pre>
     *
     * @return network
     */
    public static Network createThreeCcLinkedByASingleBus() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        Bus b7 = createBus(network, "b7");
        Bus b8 = createBus(network, "b8");
        Bus b9 = createBus(network, "b9");
        Bus b10 = createBus(network, "b10");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        createLine(network, b5, b7, "l57", 0.1f);
        createLine(network, b6, b7, "l67", 0.1f);
        createLine(network, b4, b8, "l48", 0.1f);
        createLine(network, b8, b9, "l89", 0.1f);
        createLine(network, b8, b10, "l810", 0.1f);
        createLine(network, b9, b10, "l910", 0.1f);

        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createGenerator(b10, "g10", 4);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        createLoad(b7, "d7", 2);
        createLoad(b8, "d8", 1);
        createLoad(b9, "d9", 1);

        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+                               b6 (g6) ---PTC----+
     * |                 |                                |                |
     * b2 (g2) -------- b3 (d3) -------- b4 (d4) -------- b5 (d5) -------- b7 (d7)
     *                                    |
     *                                   b8 (d8) --------- b9 (d9)
     *                                    |                 |
     *                                   b10 (g10) ---------+
     * </pre>
     *
     * @return network
     */
    public static Network createThreeCcLinkedByASingleBusWithTransformer() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "test_s", "b6");
        Bus b7 = createBus(network, "test_s", "b7");
        Bus b8 = createBus(network, "b8");
        Bus b9 = createBus(network, "b9");
        Bus b10 = createBus(network, "b10");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        createLine(network, b5, b7, "l57", 0.1f);
        TwoWindingsTransformer twt = createTransformer(network, "test_s", b6, b7, "l67", 0.1f, 1d);
        twt.newPhaseTapChanger().setTapPosition(0)
            .beginStep()
            .setX(0.1f)
            .setAlpha(1)
            .endStep()
            .add();
        createLine(network, b4, b8, "l48", 0.1f);
        createLine(network, b8, b9, "l89", 0.1f);
        createLine(network, b8, b10, "l810", 0.1f);
        createLine(network, b9, b10, "l910", 0.1f);

        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 4);
        createGenerator(b10, "g10", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        createLoad(b7, "d7", 2);
        createLoad(b8, "d8", 1);
        createLoad(b9, "d9", 1);

        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+
     * |                 |
     * b2 (g2) -------- b3 (d3)
     *                   |
     *                  b4 (d4)
     *                   |
     * b6 (g6) ------   b5 (d5)
     * |                 |
     * b7 (d7) ------ ---+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoConnectedComponentsLinkedByASerieOfTwoBranches() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        Bus b7 = createBus(network, "b7");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        createLine(network, b5, b7, "l57", 0.1f);
        createLine(network, b6, b7, "l67", 0.1f);

        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 3);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        createLoad(b7, "d7", 1);

        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+                +-------------- b6 (g6)
     * |                 |                |                |
     * b2 (g2) -------- b3 (d3) -------- b4 (d4) -------- b5 (d5)
     *                                    |
     *                                   b7 (d7) --------- b9 (g9)
     *                                    |                 |
     *                                   b8 (d8) -----------+
     * </pre>
     *
     * @return network
     */
    public static Network createThreeCc() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        Bus b7 = createBus(network, "b7");
        Bus b8 = createBus(network, "b8");
        Bus b9 = createBus(network, "b9");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b4, b7, "l47", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        createLine(network, b7, b8, "l78", 0.1f);
        createLine(network, b7, b9, "l79", 0.1f);
        createLine(network, b8, b9, "l89", 0.1f);

        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createGenerator(b9, "g9", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 2);
        createLoad(b5, "d5", 2);
        createLoad(b7, "d7", 1);
        createLoad(b8, "d8", 2);

        return network;
    }

    /**
     * <pre>
     * b1 (d1) ----------+                 +-------------- b6 (g6)
     * |                 |                 |                |
     * b2 (g2) -------- b3 (d3) --------  b4 (d4) -------- b5 (d5)
     * |                                                    |
     * +--------------------------------- b7 (d7) -------- b8 (d8)
     *                                     |                 |
     *                                    b9 (g9) -----------+
     * </pre>
     * @return network
     */
    public static Network createThreeCircularCc() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        Bus b7 = createBus(network, "b7");
        Bus b8 = createBus(network, "b8");
        Bus b9 = createBus(network, "b9");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b2, b7, "l27", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        createLine(network, b5, b8, "l58", 0.1f);
        createLine(network, b7, b8, "l78", 0.1f);
        createLine(network, b7, b9, "l79", 0.1f);
        createLine(network, b8, b9, "l89", 0.1f);

        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createGenerator(b9, "g9", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 2);
        createLoad(b5, "d5", 2);
        createLoad(b7, "d7", 1);
        createLoad(b8, "d8", 2);

        return network;
    }

    /**
     * <pre>
     * b6 (g6) ----------+                +-------------- b2 (g2) -------- b7 (d7) ----------+
     * |                 |                |                |                |                |
     * b5 (d5) -------- b4 (d4) -------- b1 (d1) -------- b3 (d3) -------- b9 (g9) -------- b8 (d8)
     *                                    |                                                  |
     *                                    +--------------------------------------------------+
     * </pre>
     * @return network
     */
    public static Network createAsymetricNetwork() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        Bus b7 = createBus(network, "b7");
        Bus b8 = createBus(network, "b8");
        Bus b9 = createBus(network, "b9");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b1, b4, "l14", 0.1f);
        createLine(network, b1, b8, "l18", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b2, b7, "l27", 0.1f);
        createLine(network, b3, b9, "l39", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        createLine(network, b7, b8, "l78", 0.1f);
        createLine(network, b7, b9, "l79", 0.1f);
        createLine(network, b8, b9, "l89", 0.1f);

        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createGenerator(b9, "g9", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 2);
        createLoad(b5, "d5", 2);
        createLoad(b7, "d7", 1);
        createLoad(b8, "d8", 2);

        return network;
    }

    /**
     * <pre>
     *                              b3 (g3) -------- b5 (d5)
     *                              /  | \             | \
     *                       -------   |   \           |  -------
     *                     /           |     \         |         \
     * b1 (g1+d1) ------ b2 (g2+d2)    |       \       |        b7 (g7) -------- b8 (d8)
     *                     \           |         \     |         /
     *                       -------   |           \   |  -------
     *                              \  |             \ | /
     *                              b4 (d4) -------- b6 (d6)
     * </pre>
     * @return network
     */
    public static Network createHighlyConnectedNetwork() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        Bus b7 = createBus(network, "b7");
        Bus b8 = createBus(network, "b8");

        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b2, b4, "l24", 0.1f);
        createLine(network, b3, b4, "l34", 0.1f);
        createLine(network, b3, b5, "l35", 0.1f);
        createLine(network, b3, b6, "l36", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        createLine(network, b5, b7, "l57", 0.1f);
        createLine(network, b6, b7, "l67", 0.1f);
        createLine(network, b7, b8, "l78", 0.1f);

        createGenerator(b1, "g1", 3);
        createGenerator(b2, "g2", 1);
        createGenerator(b3, "g3", 1);
        createGenerator(b7, "g7", 5);

        createLoad(b1, "d1", 1);
        createLoad(b2, "d2", 4);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);
        createLoad(b6, "d6", 1);
        createLoad(b8, "d8", 1);

        return network;
    }

    public static Network createHighlyConnectedSingleComponent() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");

        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b4, "l24", 0.1f);
        createLine(network, b3, b5, "l35", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);

        createGenerator(b1, "g1", 3);
        createGenerator(b2, "g2", 1);
        createGenerator(b3, "g3", 1);

        createLoad(b1, "d1", 1);
        createLoad(b2, "d2", 4);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);

        return network;
    }

    /**
     * <pre>
     * b1 ----------+
     * |            |
     * b2 -------- b3
     *
     * b5 -------- b4
     * |            |
     * b6 ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoUnconnectedCC() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");
        Bus b6 = createBus(network, "b6");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);

        createGenerator(b2, "g2", 3);
        createGenerator(b6, "g6", 2);
        createLoad(b1, "d1", 1);
        createLoad(b3, "d3", 1);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);

        return network;
    }

    public static Network createSubComp() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createBus(network, "b4");
        Bus b5 = createBus(network, "b5");

        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b2, b3, "l13", 0.1f);
        createLine(network, b2, b4, "l24", 0.1f);
        createLine(network, b3, b5, "l35", 0.1f);
        createLine(network, b4, b5, "l45", 0.1f);

        createGenerator(b1, "g1", 3);
        createGenerator(b2, "g2", 1);
        createGenerator(b3, "g3", 1);

        createLoad(b1, "d1", 1);
        createLoad(b2, "d2", 4);
        createLoad(b4, "d4", 1);
        createLoad(b5, "d5", 2);

        return network;
    }

    /**
     * <pre>
     * b1 ----------+
     * |            |
     * b2 -------- b3
     *              |
     * b5 -------- b4
     * |            |
     * b6 ----------+
     * </pre>
     *
     * @return network
     */
    public static Network createTwoCcLinkedBySwitches() {
        Network network = Network.create("test", "code");
        Bus b1 = createBus(network, "b1");
        Bus b2 = createBus(network, "b2");
        Bus b3 = createBus(network, "b3");
        Bus b4 = createOtherBus(network, "b4", "b3_vl");
        Bus b5 = createOtherBus(network, "b5", "b2_vl");
        Bus b6 = createBus(network, "b6");
        createLine(network, b1, b2, "l12", 0.1f);
        createLine(network, b1, b3, "l13", 0.1f);
        createLine(network, b2, b3, "l23", 0.1f);
        createSwitch(network, b3, b4, "s34");
        createLine(network, b4, b5, "l45", 0.1f);
        createLine(network, b4, b6, "l46", 0.1f);
        createLine(network, b5, b6, "l56", 0.1f);
        createSwitch(network, b2, b5, "s25");
        createGenerator(b1, "g1", 3);
        createGenerator(b5, "g3", 1);
        createLoad(b4, "d4", 3);
        createLoad(b2, "d2", 1);
        return network;
    }
}
