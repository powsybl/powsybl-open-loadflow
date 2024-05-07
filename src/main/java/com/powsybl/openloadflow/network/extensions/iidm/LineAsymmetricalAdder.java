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

    private ComplexMatrix yabc = null;
    private AsymmetricalBranchConnector c1 = null;
    private AsymmetricalBranchConnector c2 = null;

    public LineAsymmetricalAdder(Line line) {
        super(line);
    }

    @Override
    public Class<? super LineAsymmetrical> getExtensionClass() {
        return LineAsymmetrical.class;
    }

    @Override
    protected LineAsymmetrical createExtension(Line line) {
        return new LineAsymmetrical(line, c1, c2, yabc);
    }

    public LineAsymmetricalAdder withYabc(ComplexMatrix yabc) {
        this.yabc = yabc;
        return this;
    }

    public LineAsymmetricalAdder withAsymConnector1(AsymmetricalBranchConnector c1) {
        this.c1 = c1;
        return this;
    }

    public LineAsymmetricalAdder withAsymConnector2(AsymmetricalBranchConnector c2) {
        this.c2 = c2;
        return this;
    }
}
