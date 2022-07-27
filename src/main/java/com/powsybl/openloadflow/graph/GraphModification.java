package com.powsybl.openloadflow.graph;

import org.jgrapht.Graph;

public interface GraphModification<V, E>  {
    void apply(Graph<V, E> graph);

    void undo(Graph<V, E> graph);
}
