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

import numpy as np
import pyqtgraph as pg
from PyQt6 import QtCore
from PyQt6.QtWidgets import QMainWindow
from pyqtgraph import mkPen, PlotItem, PlotDataItem, PlotCurveItem


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


@dataclass
class SingleResult:
    operation_line: int
    method: Method
    time: int # nanos
    sd: int

@dataclass
class OperationsResult:
    operations_file: str
    operations: list[SingleResult]

    def __init__(self, file: Path):
        self.file = file
        with file.open(mode='r', encoding='utf-8') as f:
            line = f.readline()

            if line.startswith("source "):
                self.operations_file = line.removeprefix("source").strip()
                self.operations = []
            else:
                self.operations_file = file.name
                self.operations = [OperationsResult.parse_line(line)]

            self.operations += [OperationsResult.parse_line(line) for line in f]

    @staticmethod
    def parse_line(line: str) -> SingleResult:
        parts = line.split(" ")
        return SingleResult(int(parts[0]), Method(parts[1]), int(parts[2]), int(parts[3]))


@dataclass
class ConnectivityOperationsResult:
    operations_file: str
    # map a connectivity implementation (and implicitly an operations file) to associated results
    per_connectivity: dict[str, OperationsResult]

    def set(self, connectivity: str, results: OperationsResult):
        if connectivity in self.per_connectivity:
            raise ValueError("Duplicate results for ", connectivity)

        self.per_connectivity[connectivity] = results


@dataclass
class Results:
    # map an Operations (aka either a single threaded workload or sub-workload of a multithreaded workload)
    # to associated results for all connectivity
    per_operations: dict[str, ConnectivityOperationsResult]

    def get(self, operations: str) -> ConnectivityOperationsResult:
        if operations not in self.per_operations:
            self.per_operations[operations] = ConnectivityOperationsResult(operations, {})

        return self.per_operations[operations]

    def __len__(self):
        return len(self.per_operations)


class ConnectivityOperationsResultPlot(PlotItem):

    def __init__(self, res: ConnectivityOperationsResult, **kwargs):
        super().__init__(**kwargs)
        self.setTitle(res.operations_file)
        self.addLegend()
        self.tooltips = {}

        for i, (connectivity, op) in enumerate(res.per_connectivity.items()):
            x_data = [r.operation_line for r in op.operations]
            y_data = [r.sd for r in op.operations]

            pen = mkPen(color=(i, len(res.per_connectivity)))

            plot = self.plot()
            plot.curve = HoverPlotCurveItem(self, connectivity, op)
            plot.curve.setParentItem(plot)
            plot.setData(x_data, y_data)
            plot.setPen(pen)

            self.legend.addItem(plot, connectivity)

        self.guideline = pg.InfiniteLine(angle=90, pen='r', label="")
        self.guideline.setVisible(True)
        self.guideline.label.setColor('k')
        self.addItem(self.guideline)

    def update_tooltip(self, plot: PlotCurveItem, tooltip: str):
        self.tooltips[plot] = str(tooltip)

        full_tooltip = "\n".join(self.tooltips.values())
        self.guideline.label.setText(full_tooltip)

    def set_guideline(self, x: int):
        if x >= 0:
            self.guideline.setPos(x)
            self.guideline.setVisible(True)
        else:
            self.guideline.setVisible(False)


class HoverPlotCurveItem(PlotCurveItem):

    def __init__(self, plot_item: ConnectivityOperationsResultPlot, connectivity: str, operations: OperationsResult, *args, **kargs):
        super().__init__(*args, **kargs)
        self.plot_item = plot_item
        self.connectivity = connectivity
        self.operations = operations

    def nearest_point_index(self, obj) -> int:
        if isinstance(obj, QtCore.QPointF):
            l = obj.x()# - 0.5 # - 0.5 to account for integer precision and have proper guideline and tooltip around the mouse
        else:
            raise TypeError

        i = np.clip(np.searchsorted(self.xData, l), 0, len(self.xData) - 1)

        if abs(self.xData[i] - l) > 5:
            return -1
        else:
            return i

    def hoverEvent(self, ev):
        vb = self.getViewBox()
        if vb is None:
            return

        if ev.exit:
            self.plot_item.update_tooltip(self, "")
            self.plot_item.set_guideline(-1)
        else:
            i = self.nearest_point_index(ev.pos())

            if vb is not None:
                if 0 <= i < len(self.xData):
                    self.plot_item.update_tooltip(self, self.tooltip_for(i))
                    self.plot_item.set_guideline(self.xData[i])
                else:
                    self.plot_item.update_tooltip(self, "")
                    self.plot_item.set_guideline(-1)
            else:
                self.plot_item.set_guideline(-1)


    def tooltip_for(self, x_index: int) -> str:
        r = self.operations.operations[x_index]

        return "{conn}: {method} ({i}) - Sd: {Sd}".format(
            conn=self.connectivity,
            method=r.method,
            i=r.operation_line,
            Sd = r.sd
        )

def get_data(path: Path) -> Results:
    results = Results({})

    # iterate over results per connectivity
    for connectivity in path.iterdir():
        # iterate over results per operations
        for operation_res in connectivity.iterdir():
            op_res = OperationsResult(operation_res)

            conn_res = results.get(op_res.operations_file)
            conn_res.set(connectivity.name, op_res)

    return results


class Window(QMainWindow):

    def __init__(self, results: Results):
        super().__init__()
        self.results = results

        self.setCentralWidget(self.plot_all())
        self.show()
        self.setWindowTitle("Results")
        self.resize(1200, 1200 * 3 // 4)
        self.center_window()

    def plot_all(self) -> pg.GraphicsLayoutWidget:
        w = int(math.ceil(math.sqrt(len(self.results))))
        h = int(math.ceil(len(self.results) / w))
        print(w, h)

        graphics = pg.GraphicsLayoutWidget()

        for i, operations in enumerate(self.results.per_operations.values()):
            x = i % w
            y = i // w

            plot = ConnectivityOperationsResultPlot(operations)
            graphics.ci.addItem(plot, col=x, row=y)

        return graphics

    def center_window(self):
        qr = self.frameGeometry()
        cp = self.screen().availableGeometry().center()

        qr.moveCenter(cp)
        self.move(qr.topLeft())


if __name__ == '__main__':
    workload = Path("data/spy_5541_1_1_2026-07-03T12:31:54.685462530Z.txt")
    data = get_data(workload)

    pg.setConfigOptions(antialias=True)
    pg.setConfigOption('background', 'w')
    pg.setConfigOption('foreground', 'k')
    pg.mkQApp("Results")

    window = Window(data)
    pg.exec()
