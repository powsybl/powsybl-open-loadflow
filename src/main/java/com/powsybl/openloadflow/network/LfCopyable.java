package com.powsybl.openloadflow.network;

public interface LfCopyable<E, P > {

    /**
     * Create a flat copy of the object in the given copyNetwork. The object should be added manually then
     *
     * @param parentElement   The parent to copy the object in (usually a LfNetwork, or a LfBus)
     * @return the copied object (after being created, it should then be added manually in the parent element)
     */
    E copy(P parentElement);
}
