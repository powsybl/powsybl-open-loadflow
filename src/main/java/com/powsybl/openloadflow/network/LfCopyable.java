package com.powsybl.openloadflow.network;

public interface LfCopyable<E> {

    E copy(LfNetwork newNetwork);
}
