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

    /**
     * Attach the evaluable computing the current going from the first DC bus of a converter to the second one.
     *
     * @param iconv evaluable computing the current going from the first DC bus to the second one in per unit.
     */
    void setCalculatedIconv1(Evaluable iconv);

    /**
     * Attach the evaluable computing the current going from the second DC bus of a converter to the first one.
     *
     * @param iconv evaluable computing the current going from the second DC bus to the first one in per unit.
     */
    void setCalculatedIconv2(Evaluable iconv);

    /**
     * Attach the evaluable computing the active power on the AC side of the converter.
     *
     * @param p evaluable computing the active power on the AC side of the converter in per unit.
     */
    void setCalculatedPac(Evaluable p);

    /**
     * Attach the evaluable computing the reactive power on the AC side of the converter.
     *
     * @param q evaluable computing the reactive power on the AC side of the converter in per unit.
     */
    void setCalculatedQac(Evaluable q);

    /**
     * Get the AC bus connected to the converter.
     *
     * @return The LfBus connected to the converter.
     */
    LfBus getBus1();

    /**
     * Get the first DC bus connected to the converter.
     *
     * @return The first LfDcBus connected to the converter.
     */
    LfDcBus getDcBus1();

    /**
     * Get the second DC bus connected to the converter.
     *
     * @return The second LfDcBus connected to the converter.
     */
    LfDcBus getDcBus2();

    /**
     * Get the AC active power the converter request from the AC network.
     *
     * @return The AC active power requested by the converter from the AC network. In per unit
     */
    double getTargetP();

    /**
     * Get the target DC voltage of the converter, that is the target voltage difference between the first and the
     * second DC bus.
     *
     * @return The target DC voltage difference. In per unit.
     */
    double getTargetVdc();

    /**
     * Get the three losses factors of the converter.
     * 1. idle losses: losses independent of the DC current. In MW.
     * 2. switching losses: losses proportional to the DC current. In MW/A.
     * 3. resistive losses: losses proportional to the current squared. In Ohm.
     *
     * @return The three loss factor of the converter.
     */
    List<Double> getLossFactors();

    /**
     * Get the converter control mode.
     *
     * @return The converter control mode.
     */
    AcDcConverter.ControlMode getControlMode();

    /**
     * Get the active power on the AC side of the converter in per unit.
     *
     * @return the active power on the AC side of the converter in per unit.
     */
    double getPac();

    /**
     * Sets the active power on the AC side of the converter in per unit.
     *
     * @param pac the active power on the AC side of the converter in per unit.
     */
    void setPac(double pac);

    /**
     * Get the reactive power on the AC side of the converter in per unit.
     *
     * @return the reactive power on the AC side of the converter in per unit.
     */
    double getQac();

    /**
     * Sets the reactive power on the AC side of the converter in per unit.
     *
     * @param qac the reactive power on the AC side of the converter in per unit.
     */
    void setQac(double qac);

    /**
     * Update the AC/DC converter state after the load flow.
     *
     * @param parameters   Parameters of state update.
     * @param updateReport report of connected/disconnected branches and open/closed switch count.
     */
    void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport);

    /**
     * Update the DC line terminals current and power at the end of the load flow.
     *
     * @param iConv1 current going from the first DC bus to the second one in per unit.
     * @param iConv2 current going from the second DC bus to the first one in per unit.
     * @param pAc    the active power on the AC side of the converter in per unit.
     * @param qAc    the reactive power on the AC side of the converter in per unit.
     */
    void updateFlows(double iConv1, double iConv2, double pAc, double qAc);
}
