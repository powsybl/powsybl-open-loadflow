/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.AcDcConverter;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public interface LfAcDcConverter extends LfElement {

    Evaluable getCalculatedPac();

    void setCalculatedPac(Evaluable p);

    Evaluable getCalculatedIconv();

    void setCalculatedIconv(Evaluable iconv);

    Evaluable getCalculatedQac();

    void setCalculatedQac(Evaluable q);

    LfBus getBus1();

    LfDcNode getDcNode1();

    LfDcNode getDcNode2();

    double getTargetP();

    double getTargetVdc();

    double getTargetVac();

    List<Double> getLossFactors();

    AcDcConverter.ControlMode getControlMode();

    boolean isBipolar();

    double getPac();

    void setPac(double pac);

    double getQac();

    void setQac(double qac);

    void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport);

    void updateFlows(double iConv, double pAc, double qAc);
}
