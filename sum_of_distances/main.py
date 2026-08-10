#  Copyright (c) 2026, RTE (http://www.rte-france.com)
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at http://mozilla.org/MPL/2.0/.
#  SPDX-License-Identifier: MPL-2.0
import math
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from random import randint
from typing import Tuple

import numpy as np
import pyqtgraph as pg
from PyQt6 import QtCore, QtWidgets
from PyQt6.QtCore import Qt, QModelIndex
from PyQt6.QtGui import QStandardItem, QStandardItemModel
from PyQt6.QtWidgets import QMainWindow, QListView
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
    time: int  # nanos
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

    def __iter__(self):
        return self.operations.__iter__()

    def __len__(self):
        return self.operations.__len__()


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


class OperationsPlotCurveItem(PlotCurveItem):

    def __init__(self, connectivity: str, operations: OperationsResult, *args, **kargs):
        super().__init__(*args, **kargs)
        self.connectivity = connectivity
        self.operations = operations
        self.to_operation_index = []

    def nearest_point_index(self, obj) -> int:
        if isinstance(obj, QtCore.QPointF):
            l = obj.x() - 0.5  # - 0.5 to account for integer precision and have proper guideline and tooltip around the mouse
        else:
            raise TypeError

        i = np.clip(np.searchsorted(self.xData, l), 0, len(self.xData) - 1)

        if i <= 0 < len(self.xData) and abs(self.xData[i] - l) > 5:
            return -1
        else:
            return i

    def make_tooltip(self, x_index: int) -> str:
        r = self.operations.operations[self.to_operation_index[x_index]]

        return "{conn}: {method} ({i}) - Sd: {Sd}".format(
            conn=self.connectivity,
            method=r.method,
            i=r.operation_line,
            Sd=r.sd
        )

    def filter_data(self, filtered_methods: dict[Method, bool]):
        self.to_operation_index = [
            i for (i, r) in enumerate(self.operations) if not filtered_methods.get(r.method, False)
        ]

        y_data = [
            r.sd for r in self.operations if not filtered_methods.get(r.method, False)
        ]

        self.parentItem().setData(range(len(y_data)), y_data)


class ConnectivityOperationsResultPlot(PlotItem):

    def __init__(self, res: ConnectivityOperationsResult, **kwargs):
        super().__init__(**kwargs)
        self.setTitle(res.operations_file)
        self.addLegend()

        for i, (connectivity, op) in enumerate(res.per_connectivity.items()):
            pen = mkPen(color=(i, len(res.per_connectivity)))

            plot = self.plot()
            plot.curve = OperationsPlotCurveItem(connectivity, op)
            plot.curve.setParentItem(plot)
            plot.curve.filter_data({})
            plot.setPen(pen)

            self.legend.addItem(plot, connectivity)

        self.guideline = pg.InfiniteLine(angle=90, pen='r', label="")
        self.guideline.setVisible(True)
        self.guideline.label.setColor('k')
        self.addItem(self.guideline)

    def hoverEvent(self, ev):
        super().hoverEvent(ev)

        vb = self.getViewBox()
        if not ev.exit and vb is not None:
            mouse_pos = self.getViewBox().mapSceneToView(ev.pos())

            mouse_outside = True
            tooltip = ""
            for plot_data_item in self.curves:
                plot_curve: OperationsPlotCurveItem = plot_data_item.curve
                i = plot_curve.nearest_point_index(mouse_pos)

                if 0 <= i < len(plot_curve.xData):
                    if not tooltip.isspace():
                        tooltip += "\n"
                    tooltip += plot_curve.make_tooltip(i)
                    mouse_outside = False

            if not mouse_outside:
                self.guideline.label.setFormat(tooltip)
                self.guideline.setPos(int(mouse_pos.x() + 0.5))

    def filter_data(self, filtered_methods: dict[Method, bool]):
        for plot_data_item in self.curves:
            plot_data_item.curve.filter_data(filtered_methods)


class Window(QMainWindow):

    def __init__(self, results: Results):
        super().__init__()
        self.results = results
        self.plots = []

        self.parameters = self.make_parameters()
        self.graphics_layout = self.plot_all()
        self.mainw = self.make_main_widget()

        self.setCentralWidget(self.mainw)
        self.show()
        self.setWindowTitle("Results")
        self.resize(1200, 1200 * 3 // 4)
        self.center_window()

    def make_main_widget(self) -> QtWidgets.QWidget:
        splitter = QtWidgets.QSplitter(QtCore.Qt.Orientation.Horizontal)
        splitter.addWidget(self.parameters)
        splitter.addWidget(self.graphics_layout)
        splitter.setStretchFactor(0, 1)
        splitter.setStretchFactor(1, 4)

        return splitter

    def make_parameters(self) -> QtWidgets.QWidget:
        self.model = QStandardItemModel()

        for m in Method:
            item = QStandardItem(m.value)
            item.setCheckState(Qt.CheckState.Checked)
            item.setCheckable(True)
            item.setEditable(False)
            self.model.appendRow(item)
        self.model.itemChanged.connect(self.filter_data)

        view = QListView()
        view.setModel(self.model)

        layout = QtWidgets.QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(QtWidgets.QLabel("Filter by method"))
        layout.addWidget(view)

        params = QtWidgets.QWidget()
        params.setLayout(layout)
        return params

    def plot_all(self) -> pg.GraphicsLayoutWidget:
        w = int(math.ceil(math.sqrt(len(self.results))))
        h = int(math.ceil(len(self.results) / w))
        print(w, h)

        graphics = pg.GraphicsLayoutWidget()

        for i, operations in enumerate(sorted(self.results.per_operations.values(), key=lambda x: x.operations_file)):
            x = i % w
            y = i // w

            plot = ConnectivityOperationsResultPlot(operations)
            self.plots.append(plot)
            graphics.ci.addItem(plot, col=x, row=y)

        return graphics

    def center_window(self):
        qr = self.frameGeometry()
        cp = self.screen().availableGeometry().center()

        qr.moveCenter(cp)
        self.move(qr.topLeft())

    def filter_data(self, item):
        unchecked = {
            Method(self.model.item(row).text()): self.model.item(row).checkState() == Qt.CheckState.Unchecked
            for row in range(self.model.rowCount())
        }

        for plot in self.plots:
            plot.filter_data(unchecked)


def get_data(path: Path) -> Results:
    results = Results({})

    # iterate over results per connectivity
    for connectivity in path.iterdir():
        # iterate over results per operations
        for operation_res in connectivity.iterdir():
            if operation_res.is_file() and operation_res.name.endswith(".txt"):
                op_res = OperationsResult(operation_res)

                conn_res = results.get(op_res.operations_file)
                conn_res.set(connectivity.name, op_res)

    return results


if __name__ == '__main__':
    workload = Path("data/spy_5541_1_1_2026-07-03T12:31:54.685462530Z.txt")
    data = get_data(workload)

    pg.setConfigOptions(antialias=True)
    pg.setConfigOption('background', 'w')
    pg.setConfigOption('foreground', 'k')
    pg.mkQApp("Results")

    window = Window(data)
    pg.exec()
