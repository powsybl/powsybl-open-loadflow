/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph;

import org.jgrapht.alg.connectivity.TreeDynamicConnectivity;
import org.jgrapht.util.AVLTree;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class SpanningForest<V> {

    public AVLTree<V> find(V vertex) {
        return null;
    }

    public boolean connected(V u, V w) {
        return find(u) == find(w);
    }

    public int componentSize(V vertex) {
        return 0;
    }

    public boolean addEdge(V u, V v) {
        return false;
    }

    public void addAll(AVLTree<V> tree) {

    }

    public boolean removeEdge(V u, V v) {
        return false;
    }

    public Iterator<V> verticesInComponent(V vertex) {
        return null;
    }

    private int sizeOfComponent(TreeDynamicConnectivity<V> forest, V vertex) {
        return avlTree(forest, vertex).getSize() / 2;
    }

    // uuuh
    private AVLTree<V> avlTree(TreeDynamicConnectivity<V> forest, V vertex) {
        try {
            Method getNode = TreeDynamicConnectivity.class.getDeclaredMethod("getNode", Object.class);
            getNode.setAccessible(true);
            Method getTree = TreeDynamicConnectivity.class.getDeclaredMethod("getTree",
                    Class.forName("org.jgrapht.alg.connectivity.TreeDynamicConnectivity$Node"));
            getTree.setAccessible(true);

            Object node = getNode.invoke(forest, vertex);
            return (AVLTree<V>) getTree.invoke(forest, node);
        } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Edge {}
}
