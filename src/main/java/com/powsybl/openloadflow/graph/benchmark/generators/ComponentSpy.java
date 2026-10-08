/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark.generators;

import com.powsybl.openloadflow.graph.Component;
import com.powsybl.openloadflow.graph.dtree.AbstractSetView;
import com.powsybl.openloadflow.network.LfBus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.powsybl.openloadflow.graph.benchmark.BenchmarkMethod.*;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class ComponentSpy extends AbstractSetView<LfBus> implements Component<LfBus> {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static String newUniqueName() {
        return "comp_" + COUNTER.incrementAndGet();
    }

    private final BufferedWriter bw;
    private final String name;
    private final Component<LfBus> delegate;

    public ComponentSpy(BufferedWriter bw, String name, Component<LfBus> delegate) {
        this.bw = bw;
        this.name = name;
        this.delegate = delegate;
    }

    @Override
    public int getNumber() {
        try {
            WorkloadUtils.write(bw, COMP_GET_NUMBER, name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return delegate.getNumber();
    }

    @Override
    public Set<LfBus> toOwnedSet() {
        try {
            WorkloadUtils.write(bw, COMP_TO_OWNED_SET, name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return delegate.toOwnedSet();
    }

    @Override
    public int size() {
        try {
            WorkloadUtils.write(bw, COMP_SIZE, name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return delegate.size();
    }

    @Override
    public boolean contains(Object o) {
        try {
            WorkloadUtils.write(bw, COMP_CONTAINS, name, ((LfBus) o).getNum());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return delegate.contains(o);
    }

    @Override
    public Iterator<LfBus> iterator() {
        return delegate.iterator();
    }
}
