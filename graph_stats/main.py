#  Copyright (c) 2026, RTE (http://www.rte-france.com)
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at http://mozilla.org/MPL/2.0/.
#  SPDX-License-Identifier: MPL-2.0
import math
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

import numpy as np
import pyqtgraph as pg
import qdarktheme
from PyQt6 import QtCore, QtWidgets
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QStandardItem, QStandardItemModel
from PyQt6.QtWidgets import QMainWindow, QListView
from pyqtgraph import mkPen, PlotItem, PlotCurveItem


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
        try:
            return SingleResult(int(parts[0]), Method(parts[1]), int(parts[2]), int(parts[3]))
        except:
            return SingleResult(0, Method.ADD_EDGE, 0, 0)

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

class GraphType(Enum):
    SD = "Sd"
    TIME = "Time"

    def get_data(self, r: SingleResult):
        match self:
            case GraphType.SD:
                return r.sd
            case GraphType.TIME:
                return r.time


class OperationsPlotCurveItem(PlotCurveItem):

    def __init__(self, connectivity: str, operations: OperationsResult, *args, **kargs):
        super().__init__(*args, **kargs)
        self.connectivity = connectivity
        self.operations = operations
        self.to_operation_index = []
        self.filter = {}
        self.graph_type = GraphType.SD

    def nearest_point_index(self, obj) -> int:
        if isinstance(obj, QtCore.QPointF):
            l = obj.x() - 0.5  # - 0.5 to account for integer precision and have proper guideline and tooltip around the mouse
        else:
            raise TypeError

        i = np.clip(np.searchsorted(self.xData, l), 0, len(self.xData) - 1)

        if i < 0 or i >= len(self.xData) or abs(self.xData[i] - l) > 5:
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

    def set_filter(self, filtered_methods: dict[Method, bool]):
        if self.filter != filtered_methods:
            self.filter = filtered_methods
            self.update_data()

    def set_graph_type(self, graph_type: GraphType):
        if graph_type != self.graph_type:
            self.graph_type = graph_type
            self.update_data()

    def update_data(self):
        self.to_operation_index = [
            i for (i, r) in enumerate(self.operations) if not self.filter.get(r.method, False)
        ]

        y_data = [
            self.graph_type.get_data(r) for r in self.operations if not self.filter.get(r.method, False)
        ]

        self.parentItem().setData(range(len(y_data)), y_data)

class ConnectivityOperationsResultPlot(PlotItem):

    def __init__(self, res: ConnectivityOperationsResult, **kwargs):
        super().__init__(**kwargs)
        self.setTitle(res.operations_file)
        self.addLegend()
        self.getAxis("left").setLabel("Sum of distances")
        self.getAxis("bottom").setLabel("Operation")

        for i, (connectivity, op) in enumerate(res.per_connectivity.items()):
            pen = mkPen(color=(i, len(res.per_connectivity)))

            plot = self.plot()
            plot.curve = OperationsPlotCurveItem(connectivity, op)
            plot.curve.setParentItem(plot)
            plot.curve.update_data()
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
            mouse_pos = vb.mapSceneToView(ev.pos())

            mouse_outside = True
            tooltip = ""
            for plot_data_item in self.curves:
                plot_curve: OperationsPlotCurveItem = plot_data_item.curve
                if not isinstance(plot_curve, OperationsPlotCurveItem):
                    continue

                i = plot_curve.nearest_point_index(mouse_pos)

                if 0 <= i < len(plot_curve.xData):
                    if not tooltip.isspace():
                        tooltip += "\n"
                    tooltip += plot_curve.make_tooltip(i)
                    mouse_outside = False

            if not mouse_outside:
                self.guideline.label.setFormat(tooltip)
                self.guideline.setPos(int(mouse_pos.x() + 0.5))

    def set_filter(self, filtered_methods: dict[Method, bool]):
        for plot_data_item in self.curves:
            plot_data_item.curve.set_filter(filtered_methods)

    def set_graph_type(self, graph_type: GraphType):
        for plot_data_item in self.curves:
            plot_data_item.curve.set_graph_type(graph_type)

class Window(QMainWindow):

    def __init__(self, results: Results):
        super().__init__()
        self.results = results
        self.plots = self.create_plots()

        self.parameters = self.make_parameters()
        self.graphics_layout = pg.GraphicsLayoutWidget()
        self.graph_layout_changed("All")
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

        graph_type = QtWidgets.QComboBox()
        graph_type.addItems([g.value for g in GraphType])
        graph_type.currentTextChanged.connect(self.graph_type_changed)

        graph_layout = QtWidgets.QComboBox()
        graph_layout.addItems(["All"] + sorted(self.plots.keys()))
        graph_layout.currentTextChanged.connect(self.graph_layout_changed)

        layout = QtWidgets.QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(QtWidgets.QLabel("Filter by method:"))
        layout.addWidget(view)
        layout.addWidget(QtWidgets.QLabel("Graph:"))
        layout.addWidget(graph_type)
        layout.addWidget(QtWidgets.QLabel("Layout:"))
        layout.addWidget(graph_layout)
        layout.addStretch()

        params = QtWidgets.QWidget()
        params.setLayout(layout)
        params.setMinimumWidth(100)

        return params

    def create_plots(self) -> dict[str, ConnectivityOperationsResultPlot]:
        return {
            name: ConnectivityOperationsResultPlot(op)
            for name, op in sorted(self.results.per_operations.items(), key=lambda x: x[0])
        }

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

        for plot in self.plots.values():
            plot.set_filter(unchecked)

    def graph_type_changed(self, current):
        graph_type = GraphType(current)
        for plot in self.plots.values():
            plot.set_graph_type(graph_type)

    def graph_layout_changed(self, current):
        w = int(math.ceil(math.sqrt(len(self.results))))
        h = int(math.ceil(len(self.results) / w))

        for p in self.plots.values():
            try:
                self.graphics_layout.ci.removeItem(p)
            except ValueError:
                pass

        if current == "All":
            for i, plot in enumerate(self.plots.values()):
                x = i % w
                y = i // w

                self.graphics_layout.ci.addItem(plot, col=x, row=y)
        else:
            self.graphics_layout.ci.addItem(self.plots[current], col=0, row=0)
            self.graphics_layout.ci.getViewBox()

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
    workload = Path("data_roots/spy_10000_10_10_10000_10_10_2026-08-07T07:59:16.649371906Z.zip")
    data = get_data(workload)

    pg.setConfigOptions(antialias=True)
    pg.setConfigOption('background', 'w')
    pg.setConfigOption('foreground', 'k')
    pg.mkQApp("Results")
    qdarktheme.setup_theme()

    window = Window(data)
    pg.exec()
