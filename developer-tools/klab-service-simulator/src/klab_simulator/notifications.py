"""SIMULATED_PROVISIONAL/JAVA-GAP-0005: finite retained Job hints, not AMQP or science."""
from copy import deepcopy
import secrets
from urllib.parse import parse_qs, urlsplit

from .state import ContractError

PROTOCOL = "klab.experimental.notifications/1"
ROUTE = "/experimental/notifications/v1/events"
COVERAGE = "SINCE_EXPLICIT_REFERENCE_ACTIVATION_ONLY"


class Notifications:
    def __init__(self, state):
        self.state = state
        self.logs = {}

    def request(self, original_path, scope, owner, headers):
        root, _ = self.state._context(scope, owner)
        if root != scope or headers.get("klab-service") != self.state.service_ids["runtime"]:
            raise ContractError(403, "Retained notifications require bound originating root context")
        params = parse_qs(urlsplit(original_path).query, keep_blank_values=True)
        if (not set(params) <= {"protocol", "limit", "epoch", "after"} or any(len(v) != 1 for v in params.values())
                or params.get("protocol") != [PROTOCOL] or params.get("limit", [""])[0] not in map(str, range(1, 65))):
            raise ContractError(422, "Explicit reference version and bounded batch size required")
        after = None
        if "epoch" in params or "after" in params:
            try:
                after_text = params["after"][0]
                if (not params["epoch"][0] or len(params["epoch"][0]) > 128 or not after_text.isascii()
                        or not after_text.isdecimal() or len(after_text) > 16):
                    raise ValueError("Invalid cursor")
                after = int(after_text)
                if after >= 2**53:
                    raise ValueError("Invalid cursor")
            except (KeyError, ValueError):
                raise ContractError(422, "Cursor epoch and exact sequence required") from None
        row = self.logs.setdefault(root, {"epoch": secrets.token_hex(16), "next": 1, "events": []})
        first = row["events"][0]["sequence"] if row["events"] else row["next"]
        result = {"protocol": PROTOCOL, "epoch": row["epoch"], "first": first, "next": row["next"],
            "state": "OK", "events": [], "coverage": COVERAGE}
        if after is None:
            return result, "application/json"  # Explicit activation establishes tail; no invented prior history.
        if params["epoch"] != [row["epoch"]] or not first - 1 <= after < row["next"]:
            result["state"] = "RESYNC_REQUIRED"
        else:
            result["events"] = deepcopy([e for e in row["events"] if e["sequence"] > after][:int(params["limit"][0])])
        return result, "application/json"

    def changed_jobs(self, before):
        for (service, identity), job in self.state.jobs.items():
            root = job["scope"]
            if root in self.logs and before.get((service, identity)) != job["status"]:
                row = self.logs[root]
                if job["status"] not in ("WAITING", "STARTED", "FINISHED", "ABORTED", "INTERRUPTED"):
                    continue
                row["events"].append({"sequence": row["next"], "kind": "JOB_" + job["status"], "job_id": identity, "service": service})
                row["next"] += 1
                row["events"] = row["events"][-128:]
        self.logs = {root: row for root, row in self.logs.items() if root in self.state.contexts}
