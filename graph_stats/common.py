#  Copyright (c) 2026, RTE (http://www.rte-france.com)
#  This Source Code Form is subject to the terms of the Mozilla Public
#  License, v. 2.0. If a copy of the MPL was not distributed with this
#  file, You can obtain one at http://mozilla.org/MPL/2.0/.
#  SPDX-License-Identifier: MPL-2.0
from dataclasses import dataclass
from enum import Enum
from pathlib import Path


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
    sd: float


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
            return SingleResult(int(parts[0]), Method(parts[1]), int(parts[2]), float(parts[3]))
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