"""Finite synthetic state machine; no k.Actors parsing, compilation or execution.

JAVA-GAP-0008. Existing native Runtime/AMQP contracts are not replaced or accepted.
Only caller-owned root BEHAVIOR-like lifecycle is referenced; no private sessions.
"""
import json
import re
import secrets
from urllib.parse import parse_qs, urlsplit

from .state import ContractError

PROTOCOL = "klab.experimental.managed-agents/1"
PREFIX = "/experimental/managed-agents/v1/"
EXECUTION = "SYNTHETIC_STATE_MACHINE_NO_KACTORS_EXECUTION"
TERMINAL = ("STOPPED", "FINISHED", "FAILED")


class ManagedAgents:
    def __init__(self, state):
        self.state = state
        self.epoch = secrets.token_hex(16)
        self.rows = {}

    def cleanup(self):
        self.rows = {key: row for key, row in self.rows.items() if row["scope"] in self.state.contexts}

    def view(self, row):
        return {"protocol": PROTOCOL, "execution": EXECUTION, "epoch": self.epoch,
            "runtime_id": self.state.service_ids["runtime"], **row}

    def request(self, method, route, scope, owner, headers, body):
        root, _ = self.state._context(scope, owner)
        if root != scope or headers.get("klab-service") != self.state.service_ids["runtime"]:
            raise ContractError(403, "Managed reference requires originating root/Runtime")
        self.cleanup()
        url = urlsplit(route)
        pieces = url.path[len(PREFIX):].split("/")
        data = None
        if method == "POST":
            if len(body) > 4096:
                raise ContractError(413, "Managed reference request byte budget exceeded")
            try:
                def unique(items):
                    result = {}
                    for key, value in items:
                        if key in result:
                            raise ValueError("Duplicate managed field")
                        result[key] = value
                    return result
                data = json.loads(body, object_pairs_hook=unique)
            except (ValueError, UnicodeError, RecursionError):
                raise ContractError(422, "Malformed managed reference JSON") from None
            if not isinstance(data, dict) or data.get("protocol") != PROTOCOL or url.query:
                raise ContractError(422, "Explicit managed reference protocol required")
        if method == "POST" and pieces == ["create"]:
            if set(data) != {"protocol", "fixture"} or data["fixture"] not in ("idle", "finish", "fail"):
                raise ContractError(422, "Only finite synthetic lifecycle fixtures, not source code, are supported")
            if len(self.rows) >= 16:
                raise ContractError(429, "Managed reference retention capacity exceeded; explicitly purge cleaned terminal rows")
            identity = secrets.token_hex(16)
            row = {"agent_id": identity, "scope": root, "revision": 1, "fixture": data["fixture"],
                "state": "READY", "cleanup_confirmed": False}
            self.rows[identity] = row
            return self.view(row), "application/json"
        if not pieces or re.fullmatch(r"[0-9a-f]{32}", pieces[0]) is None:
            raise ContractError(422, "Invalid managed reference identity")
        if method == "GET":
            params = parse_qs(url.query, keep_blank_values=True)
            if body or set(params) != {"protocol", "epoch"} or params["protocol"] != [PROTOCOL] or len(params["epoch"]) != 1:
                raise ContractError(422, "Explicit managed epoch query required")
            epoch = params["epoch"][0]
        elif method == "POST":
            if set(data) != {"protocol", "epoch", "expected_revision"} or type(data["expected_revision"]) is not int or not 1 <= data["expected_revision"] <= 16:
                raise ContractError(422, "Choose exact observed managed revision")
            epoch = data["epoch"]
        else:
            raise ContractError(404, "Unsupported managed reference operation")
        row = self.rows.get(pieces[0])
        if row is None or epoch != self.epoch:
            raise ContractError(410, "Managed reference absent, purged or lost on restart; never auto-create/replay")
        if row["scope"] != root:
            raise ContractError(403, "Managed reference belongs to another root")
        if method == "GET" and len(pieces) == 1:
            # Explicit deterministic fixture ticks, not a real actor/scheduler/science.
            if row["state"] == "STOPPING":
                row["state"] = "STOPPED"; row["revision"] += 1
            elif row["state"] == "RUNNING" and row["fixture"] in ("finish", "fail"):
                row["state"] = "FINISHED" if row["fixture"] == "finish" else "FAILED"
                row["revision"] += 1
            elif row["state"] in TERMINAL and not row["cleanup_confirmed"]:
                row["cleanup_confirmed"] = True; row["revision"] += 1
            return self.view(row), "application/json"
        if method != "POST" or len(pieces) != 2 or pieces[1] not in ("start", "stop", "purge"):
            raise ContractError(404, "Unsupported managed reference operation")
        if data["expected_revision"] != row["revision"]:
            raise ContractError(409, "Managed lifecycle changed; explicitly reconcile current state")
        command = pieces[1]
        if command == "purge":
            if row["state"] not in TERMINAL or not row["cleanup_confirmed"]:
                raise ContractError(409, "Terminal cleanup must be independently confirmed before purge")
            del self.rows[pieces[0]]
            return {"protocol": PROTOCOL, "agent_id": pieces[0], "epoch": self.epoch, "purged": True}, "application/json"
        if command == "start" and row["state"] == "READY":
            row["state"] = "RUNNING"
        elif command == "stop" and row["state"] in ("READY", "RUNNING"):
            row["state"] = "STOPPING"
        else:
            raise ContractError(409, "Managed reference is single-use; invalid/repeated lifecycle command")
        row["revision"] += 1
        return self.view(row), "application/json"
