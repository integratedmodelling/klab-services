"""Finite reference checkpoints, NOT native committed scientific revisions.

Captures only completed synthetic observation identities under the State lock.
No values/graph lineage, durable restart history, shared grants or notification
ordering guarantees. Explicit client opt-in protocol and expected-head CAS only.
"""
from copy import deepcopy
from urllib.parse import parse_qs, urlsplit

from .state import ContractError

PROTOCOL = "klab.experimental.revision-snapshots/1"
KIND = "SYNTHETIC_OBSERVATION_CHECKPOINT_NOT_NATIVE_COMMIT"
PREFIX = "/experimental/revision-snapshots/v1/"


class RevisionSnapshots:
    def __init__(self, state):
        self.state = state
        self.rows = {}

    def request(self, method, path, scope, owner, headers, data):
        if self.state.scenario != "revision-convergence" or self.state.store is not None:
            raise ContractError(409, "Reference revision fixture requires explicit ephemeral scenario; no durable claim")
        root, _ = self.state._context(scope, owner)
        if scope != root or headers.get("klab-service") != self.state.service_ids["runtime"]:
            raise ContractError(403, "Checkpoint requires the exact owned root and originating Runtime")
        url = urlsplit(path)
        if url.path != PREFIX + "checkpoint":
            raise ContractError(404, "Unknown reference checkpoint operation")
        history = self.rows.get(root, [])
        head = history[-1]["revision"] if history else 0
        if method == "GET":
            params = parse_qs(url.query, keep_blank_values=True)
            if (not set(params) <= {"protocol", "revision"} or params.get("protocol") != [PROTOCOL]
                    or any(len(value) != 1 for value in params.values())):
                raise ContractError(422, "Exact reference checkpoint protocol required")
            selected = head
            if "revision" in params:
                value = params["revision"][0]
                if not value.isascii() or not value.isdecimal() or len(value) > 16 or int(value) < 1:
                    raise ContractError(422, "Invalid reference checkpoint revision")
                selected = int(value)
            row = next((row for row in history if row["revision"] == selected), None)
            if row is None:
                raise ContractError(410, "Reference checkpoint absent or no longer retained")
            return deepcopy(row), "application/json"
        if method != "POST" or url.query:
            raise ContractError(404, "No automatic commit/replay/merge operation")
        if (not isinstance(data, dict) or set(data) != {"protocol", "expected_revision"}
                or data["protocol"] != PROTOCOL or type(data["expected_revision"]) is not int
                or not 0 <= data["expected_revision"] < 2**53):
            raise ContractError(422, "Exact reference checkpoint compare-and-swap required")
        if data["expected_revision"] != head:
            raise ContractError(409, "Reference head changed; explicitly reread and reconcile")
        identities = sorted(identity for (context, identity) in self.state.observations if context == root)
        if len(identities) > 256 or head >= 2**53 - 1:
            raise ContractError(429, "Reference checkpoint budget exhausted")
        row = {"protocol": PROTOCOL, "kind": KIND, "runtime_id": self.state.service_ids["runtime"],
            "context_id": root, "revision": head + 1, "previous_revision": head,
            "observation_ids": identities, "complete_lineage": False, "durable": False}
        self.rows[root] = [*history, row][-8:]
        return deepcopy(row), "application/json"
