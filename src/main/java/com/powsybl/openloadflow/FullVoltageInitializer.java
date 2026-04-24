/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.ac.VoltageMagnitudeInitializer;
import com.powsybl.openloadflow.dc.DcValueVoltageInitializer;
import com.powsybl.openloadflow.network.LfAcDcNetwork;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Reports;

import java.util.Objects;

/**
 * A voltage initializer that rely on {@link VoltageMagnitudeInitializer} for magnitude calculation and on
 * {@link DcValueVoltageInitializer} for angle calculation.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class FullVoltageInitializer implements VoltageInitializer {

    public static final String NAME = "Full Voltage";

    private final VoltageMagnitudeInitializer magnitudeInitializer;

    private final DcValueVoltageInitializer angleInitializer;

    public FullVoltageInitializer(VoltageMagnitudeInitializer magnitudeInitializer, DcValueVoltageInitializer angleInitializer) {
        this.magnitudeInitializer = Objects.requireNonNull(magnitudeInitializer);
        this.angleInitializer = Objects.requireNonNull(angleInitializer);
    }

    @Override
    public void prepare(LfNetwork network, ReportNode reportNode) {
        ReportNode initReportNode = Reports.reportVoltageInitializer(reportNode, NAME);
        if (network instanceof LfAcDcNetwork) {
            // Throw exception here, otherwise DC load flow will run anyway by angle initializer
            throw new PowsyblException("Full voltage initialization is not yet supported with AcDcNetwork");
        }
        magnitudeInitializer.prepare(network, initReportNode);
        angleInitializer.prepare(network, initReportNode);
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return magnitudeInitializer.getMagnitude(bus);
    }

    @Override
    public double getAngle(LfBus bus) {
        return angleInitializer.getAngle(bus);
    }

    @Override
    public double getMagnitude(LfDcBus dcBus) {
        throw new PowsyblException("Full voltage initialization is not yet supported with AcDcNetwork");
    }
}
