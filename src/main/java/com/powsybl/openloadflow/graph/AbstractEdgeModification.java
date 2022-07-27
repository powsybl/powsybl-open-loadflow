package com.powsybl.openloadflow.graph;

public abstract class AbstractEdgeModification<V, E>  implements GraphModification<V, E>  {
    protected final E e;
    protected final V v1;
    protected final V v2;

    public AbstractEdgeModification(V vertex1, V vertex2, E e) {
        this.v1 = vertex1;
        this.v2 = vertex2;
        this.e = e;
    }
}

