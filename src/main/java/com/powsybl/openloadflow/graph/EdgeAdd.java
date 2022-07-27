package com.powsybl.openloadflow.graph;

import org.jgrapht.Graph;

public class EdgeAdd<V, E>  extends AbstractEdgeModification<V, E>  {
    public EdgeAdd(V vertex1, V vertex2, E e) {
        super(vertex1, vertex2, e);
    }

    @Override
    public void apply(Graph<V, E> graph) {
        graph.addEdge(v1, v2, e);
    }

    @Override
    public void undo(Graph<V, E> graph) {
        graph.removeEdge(e);
    }
}

