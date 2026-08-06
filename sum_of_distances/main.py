#  Copyright (c) 2026, RTE (http://www.rte-france.com)
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at http://mozilla.org/MPL/2.0/.
#  SPDX-License-Identifier: MPL-2.0
import math
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Tuple

import matplotlib.pyplot as plt
from matplotlib.axes import Axes


class Method(Enum):
    ADD_VERTEX = "v"
    ADD_EDGE = "e"
    REMOVE_EDGE = "rm"
    START_TEMPORARY_CHANGES = "start"
    UNDO_TEMPORARY_CHANGES = "undo"
    GET_COMPONENT_NUMBER = "get_num"
    SET_MAIN_COMPONENT_VERTEX = "set_main"
    GET_NB_CONNECTED_COMPONENTS = "count"
    GET_CONNECTED_COMPONENT = "get_comp"
    GET_LARGEST_CONNECTED_COMPONENT = "largest"
    GET_VERTICES_ADDED_TO_MAIN_COMPONENT = "v_added"
    GET_EDGES_ADDED_TO_MAIN_COMPONENT = "e_added"
    GET_VERTICES_REMOVED_FROM_MAIN_COMPONENT = "v_removed"
    GET_EDGES_REMOVED_FROM_MAIN_COMPONENT = "e_removed"


class Operations:
    file: Path
    operations: list[Tuple[Method, int]]

    def __init__(self, file: Path):
        self.file = file
        with file.open(mode='r', encoding='utf-8') as f:
            self.operations = [Operations.parse_line(line) for line in f]

    @staticmethod
    def parse_line(line: str) -> Tuple[Method, int]:
        parts = line.split(" ")
        return Method(parts[0]), int(parts[1])

@dataclass
class Data:
    graph_count: int
    per_connectivity: dict[str, list[Operations]]

def get_data(path: Path) -> Data:
    data = {}
    graph_count = 0
    for conn in path.iterdir():
        op = [Operations(operation_res) for operation_res in conn.iterdir()]
        op.sort(key=lambda op: op.file)
        graph_count = max(graph_count, len(op))
        data[conn.name] = op

    return Data(graph_count, data)

def plot(ax: Axes, data: Data, i: int):
    for connectivity, op in data.per_connectivity.items():
        y_data = op[i].operations
        x_data = range(0, len(y_data))

        ax.plot(x_data, [sd for (_, sd) in y_data], label=connectivity)
        ax.set_title(op[i].file.name)

if __name__ == '__main__':
    workload = Path("data/spy_10000_10_10_10000_10_10_2026-07-09T08:47:18.906235251Z.zip/")
    data = get_data(workload)

    w = int(math.ceil(math.sqrt(data.graph_count)))
    h = int(math.ceil(data.graph_count / w))
    print(w, h)

    fig, axs = plt.subplots(w, h)

    for x in range(w):
        for y in range(h):
            i = y * w + x
            if w == 1:
                ax = axs
            elif h == 1:
                ax = axs[x]
            else:
                ax = axs[x, y]

            plot(ax, data, i)

    plt.tight_layout(pad=1.01)
    plt.legend()
    plt.show()