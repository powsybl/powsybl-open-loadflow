/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Line;
import com.powsybl.openloadflow.util.ComplexMatrix;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LineAsymmetricalAdder extends AbstractExtensionAdder<Line, LineAsymmetrical> {

    private boolean isOpenA = false;
    private boolean isOpenB = false;
    private boolean isOpenC = false;
    private ComplexMatrix yabc = null;

    public LineAsymmetricalAdder(Line line) {
        super(line);
    }

    @Override
    public Class<? super LineAsymmetrical> getExtensionClass() {
        return LineAsymmetrical.class;
    }

    @Override
    protected LineAsymmetrical createExtension(Line line) {
        return new LineAsymmetrical(line, isOpenA, isOpenB, isOpenC, yabc);
    }

    public LineAsymmetricalAdder withIsOpenA(boolean isOpenA) {
        this.isOpenA = isOpenA;
        return this;
    }

    public LineAsymmetricalAdder withIsOpenB(boolean isOpenB) {
        this.isOpenB = isOpenB;
        return this;
    }

    public LineAsymmetricalAdder withIsOpenC(boolean isOpenC) {
        this.isOpenC = isOpenC;
        return this;
    }

    public LineAsymmetricalAdder withYabc(ComplexMatrix yabc) {
        this.yabc = yabc;
        return this;
    }

}
