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

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public interface LfAcDcConverter extends LfElement {

    /**
     * @param iconv evaluable computing the current going from the first DC bus to the second one in per unit.
     */
    void setCalculatedIconv1(Evaluable iconv);

    /**
     * @param iconv evaluable computing the current going from the second DC bus to the first one in per unit.
     */
    void setCalculatedIconv2(Evaluable iconv);

    /**
     * @param p evaluable computing the active power on the AC side of the converter in per unit.
     */
    void setCalculatedPac(Evaluable p);

    /**
     * @param q evaluable computing the reactive power on the AC side of the converter in per unit.
     */
    void setCalculatedQac(Evaluable q);

    /**
     * @return The LfBus connected to the converter.
     */
    LfBus getBus1();

    /**
     * @return The first LfDcBus connected to the converter.
     */
    LfDcBus getDcBus1();

    /**
     * @return The second LfDcBus connected to the converter.
     */
    LfDcBus getDcBus2();

    /**
     * @return The AC active power requested by the converter from the AC network. In per unit
     * Positive value means the power flows from AC network to DC network.
     */
    double getTargetP();

    /**
     * @return The target DC voltage difference, that is the target voltage difference between the first and the second
     * DC bus. In per unit.
     */
    double getTargetVdc();

    /**
     * The three loss factors of the converter
     *
     * @param idleLoss      losses independent of the DC current. In MW.
     * @param switchingLoss losses proportional to the DC current. In MW/A.
     * @param resistiveLoss losses proportional to the current squared. In Ohm.
     */
    record LossFactors(double idleLoss, double switchingLoss, double resistiveLoss) {
    }

    /**
     * @return The three loss factor of the converter.
     */
    LossFactors getLossFactors();

    /**
     * @return The converter control mode.
     */
    AcDcConverter.ControlMode getControlMode();

    /**
     * @return the active power on the AC side of the converter in per unit.
     * Positive value means the power flows from AC network to DC network.
     */
    double getPac();

    /**
     * @param pac the active power on the AC side of the converter in per unit.
     *            Positive value means the power flows from AC network to DC network.
     */
    void setPac(double pac);

    /**
     * @return the reactive power on the AC side of the converter in per unit.
     * Positive value means the power flows from AC network to DC network.
     */
    double getQac();

    /**
     * @param qac the reactive power on the AC side of the converter in per unit.
     *            Positive value means the power flows from AC network to DC network.
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
     *               Positive value means the power flows from AC network to DC network.
     * @param qAc    the reactive power on the AC side of the converter in per unit.
     *               Positive value means the power flows from AC network to DC network.
     */
    void updateFlows(double iConv1, double iConv2, double pAc, double qAc);
}
