/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class Log {

    private static Log instance;

    public static Log init() {
        if (instance == null) {
            instance = new Log();
        }

        return instance;
    }

    public static Log init(String output) {
        if (instance == null) {
            instance = new Log(output);
        }

        return instance;
    }

    public static Log get() {
        return Objects.requireNonNull(instance);
    }

    private boolean lastIsProgress = false;
    private final BufferedWriter output;
    private Thread shutdownHook;

    public Log() {
        output = null;
    }

    public Log(String logFile) {
        try {
            output = Files.newBufferedWriter(Path.of(logFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        shutdownHook = new Thread(this::close);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public void log(String format, Object... args) {
        if (lastIsProgress) {
            System.out.print("\r\033[2K"); // erase line
        }

        String line = format.formatted(args);
        System.out.println(line);
        lastIsProgress = false;

        if (output != null) {
            try {
                output.write(line);
                output.write(System.lineSeparator());
                output.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void logProgress(String format, Object... args) {
        System.out.printf("\r\033[2K" + format, args);
        System.out.flush();

        lastIsProgress = true;
    }

    public void close() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // in jvm shutdown sequence
            }
        }
    }
}
