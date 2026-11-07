"""SIMULATED_PROVISIONAL/JAVA-GAP-0009: private draft CAS, no parser/compiler/publication."""
from copy import deepcopy
import hashlib
import re
from urllib.parse import parse_qs, urlsplit

from .state import ContractError

PROTOCOL = "klab.experimental.drafts/1"
PREFIX = "/experimental/drafts/v1/"


def retained(rows, contexts):
    """Validate all retained private drafts before changing any live state."""
    if not isinstance(rows, dict) or len(rows) > 4096:
        raise ValueError("Invalid retained draft contexts")
    for root, names in rows.items():
        if root not in contexts or not isinstance(names, dict) or not 1 <= len(names) <= 8:
            raise ValueError("Invalid retained draft owner or names")
        for name, history in names.items():
            if (not isinstance(name, str) or re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", name) is None or name in (".", "..")
                    or not isinstance(history, list) or not 1 <= len(history) <= 8):
                raise ValueError("Invalid retained draft history")
            previous = None
            for row in history:
                if (not isinstance(row, dict) or set(row) != {"protocol", "name", "revision", "source", "source_sha256", "validation"}
                        or row["protocol"] != PROTOCOL or row["name"] != name
                        or type(row["revision"]) is not int or not 1 <= row["revision"] < 2**53
                        or previous is not None and row["revision"] != previous + 1
                        or row["validation"] != "UNVALIDATED_PRIVATE_DRAFT" or not isinstance(row["source"], str)):
                    raise ValueError("Invalid retained draft revision")
                try:
                    source = row["source"].encode("utf-8")
                except UnicodeError:
                    raise ValueError("Invalid retained draft UTF-8") from None
                if not row["source"].strip() or len(source) > 16384 or hashlib.sha256(source).hexdigest() != row["source_sha256"]:
                    raise ValueError("Invalid retained draft digest")
                previous = row["revision"]
    return deepcopy(rows)


class Drafts:
    def __init__(self, state):
        self.state = state
        self.rows = {}

    def request(self, method, original_path, scope, owner, headers, data):
        root, _ = self.state._context(scope, owner)
        if root != scope or headers.get("klab-service") != self.state.service_ids["runtime"]:
            raise ContractError(403, "Private drafts require exact owned originating root context")
        url = urlsplit(original_path)
        name = url.path[len(PREFIX):]
        if re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", name) is None:
            raise ContractError(422, "Invalid private draft identity")
        if name in (".", ".."):
            raise ContractError(422, "Draft identity cannot be a path segment")
        rows = self.rows.get(root, {})
        history = rows.get(name, [])
        if method == "GET":
            params = parse_qs(url.query, keep_blank_values=True)
            if (not set(params) <= {"protocol", "revision"} or params.get("protocol") != [PROTOCOL]
                    or any(len(value) != 1 for value in params.values())):
                raise ContractError(422, "Explicit private draft protocol required")
            if not history:
                raise ContractError(404, "Private draft absent")
            if "revision" not in params:
                return deepcopy(history[-1]), "application/json"
            value = params["revision"][0]
            if not value.isascii() or not value.isdecimal() or len(value) > 16 or int(value) < 1:
                raise ContractError(422, "Invalid private draft revision")
            selected = next((row for row in history if row["revision"] == int(value)), None)
            if selected is None:
                raise ContractError(410, "Private draft revision is not retained")
            return deepcopy(selected), "application/json"
        if method != "PUT" or url.query:
            raise ContractError(404, "Unsupported private draft operation; no publication or execution")
        if (not isinstance(data, dict) or set(data) != {"protocol", "source", "source_sha256", "expected_revision"}
                or data["protocol"] != PROTOCOL or type(data["expected_revision"]) is not int
                or not 0 <= data["expected_revision"] < 2**53 or not isinstance(data["source"], str)):
            raise ContractError(422, "Exact draft CAS request required")
        source = data["source"]
        try:
            encoded = source.encode("utf-8")
        except UnicodeError:
            raise ContractError(422, "Draft source must be valid UTF-8") from None
        if not source.strip() or len(encoded) > 16384 or hashlib.sha256(encoded).hexdigest() != data["source_sha256"]:
            raise ContractError(422, "Invalid bounded draft source/digest")
        revision = history[-1]["revision"] if history else 0
        if revision >= 2**53 - 1:
            raise ContractError(429, "Private draft revision budget exhausted")
        if revision != data["expected_revision"]:
            raise ContractError(409, "Draft compare-and-swap conflict")
        if not history and len(rows) >= 8:
            raise ContractError(429, "Private draft capacity exceeded")
        row = {"protocol": PROTOCOL, "name": name, "revision": revision + 1, "source": source,
            "source_sha256": data["source_sha256"], "validation": "UNVALIDATED_PRIVATE_DRAFT"}
        rows[name] = [*history, row][-8:]
        self.rows[root] = rows
        return deepcopy(row), "application/json"
