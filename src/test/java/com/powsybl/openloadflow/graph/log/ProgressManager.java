/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.log;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
public class ProgressManager<P extends TProgress<P>> {

    private final List<P> progress = new ArrayList<>();
    private ProgressFormatter<P> formatter;

    private long last = 0;
    private long period;

    public ProgressManager() {
        this(1000);
    }

    public ProgressManager(long period) {
        this.period = period;
    }

    public P newProgress(P progress) {
        this.progress.add(progress);
        progress.setManager(this);
        return progress;
    }

    public boolean remove(P progress) {
        if (this.progress.remove(progress)) {
            progress.setManager(null);
            return true;
        }
        return false;
    }

    public void removeAll() {
        for (P progress : this.progress) {
            progress.setManager(null);
        }
        progress.clear();
    }

    public void printProgress() {
        if (System.currentTimeMillis() - last > period) {
            // multiple threads can reach this point
            synchronized (this) {
                // but only one can be here
                // checking twice is useful when printProgress is called often in multiple threads
                long elapsed = System.currentTimeMillis() - last;
                if (elapsed > period) {
                    String line = formatter.format(progress, elapsed);

                    Log.get().logProgress(line);
                    last = System.currentTimeMillis();
                }
            }
        }
    }

    public void printProgress(boolean force) {
        synchronized (this) {
            long elapsed = System.currentTimeMillis() - last;
            String line = formatter.format(progress, elapsed);

            Log.get().logProgress(line);
            last = System.currentTimeMillis();
        }
    }

    public synchronized void setPeriod(long period) {
        this.period = period;
    }

    public List<P> getProgress() {
        return progress;
    }

    public synchronized void setFormatter(ProgressFormatter<P> formatter) {
        this.formatter = formatter;
    }

    public ProgressFormatter<P> getFormatter() {
        return formatter;
    }
}
