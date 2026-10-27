#  Copyright (c) 2026, RTE (http://www.rte-france.com)
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at http://mozilla.org/MPL/2.0/.
#  SPDX-License-Identifier: MPL-2.0
import lttb
import numpy as np

from common import *

def downsample(x, y, n):
    data = np.column_stack((x, y))
    downsampled = lttb.downsample(data, n_out=n)
    indices = np.searchsorted(x, downsampled[:, 0])
    return indices

if __name__ == '__main__':
    inputs = [
        "data/spy_5541_1_1_2026-07-03T12:31:54.685462530Z.txt",
        "data/spy_5541_1_1_5541_1_1_2026-07-03T11:50:06.510031405Z.txt",
        "data/spy_10000_10_10_10000_10_10_2026-08-07T07:59:16.649371906Z.zip"
    ]

    for input in inputs:
        data = get_data(Path(input))

        for (workload, results) in data.per_operations.items():
            for (connectivity, result) in results.per_connectivity.items():
                operation_lines = np.array([r.operation_line for r in result.operations])
                # vertex_count = sum([1 if r.method == Method.ADD_VERTEX else 0 for r in result.operations])
                sds = np.array([r.sd for r in result.operations])

                indices = downsample(operation_lines, sds, 500)

                output = Path("downsampled") / Path(input) / connectivity / result.operations_file
                output.parent.mkdir(parents=True, exist_ok=True)

                with output.open(mode='w', encoding='utf-8') as f:
                    for i in indices:
                        operation = result.operations[i]
                        f.write("{op} {method} {time} {sd}\n".format(
                            op=operation.operation_line,
                            method=operation.method.value,
                            time=operation.time,
                            sd=operation.sd))
                print(output)
