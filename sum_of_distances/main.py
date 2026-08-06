#  Copyright (c) 2026, RTE (http://www.rte-france.com)
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at http://mozilla.org/MPL/2.0/.
#  SPDX-License-Identifier: MPL-2.0

from pathlib import Path

import matplotlib.pyplot as plt


class Operations:
    graph_build : list[int]
    after: list[int]

    def __init__(self, file: Path):
        self.graph_build = []
        self.after = []

        graph_build_done = False
        with file.open(mode='r', encoding='utf-8') as f:
            for line in f:
                if line.strip() == "initial graph build":
                    graph_build_done = True
                else:
                    sd = int(line)
                    if graph_build_done:
                        self.after.append(sd)
                    else:
                        self.graph_build.append(sd)


def get_data(path: Path) -> dict[str, list[Operations]]:
    data = {}
    for conn in path.iterdir():
        data[conn] = [Operations(operation_res) for operation_res in conn.iterdir()]

    return data

if __name__ == '__main__':
    workload = Path("spy_5541_1_1_2026-07-03T12:31:54.685462530Z.txt/")
    data = get_data(workload)

    for connectivity, res in data.items():
        plt.plot(range(0, len(res[0].after) + len(res[0].graph_build)), res[0].graph_build + res[0].after)

    plt.show()