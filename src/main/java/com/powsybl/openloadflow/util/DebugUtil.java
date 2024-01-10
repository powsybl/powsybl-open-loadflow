/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.PlatformConfig;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class DebugUtil {

    public static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-dd-M--HH-mm-ss-SSS");

    private DebugUtil() {
    }

    public static Path getDebugDir(String debugDirStr) {
        Objects.requireNonNull(debugDirStr);
        return PlatformConfig.defaultConfig().getConfigDir()
                .map(dir -> dir.getFileSystem().getPath(debugDirStr))
                .orElseThrow(() -> new PowsyblException("Cannot write to debug directory as no configuration directory has been defined"));
    }
}
