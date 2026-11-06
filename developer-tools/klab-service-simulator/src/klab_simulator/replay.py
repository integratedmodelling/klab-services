"""Finite pinned read-only wire replay, never a fallback for failed Java requests."""
from copy import deepcopy
from importlib.resources import files
import json
import hashlib
from urllib.parse import unquote, urlsplit

from .state import ContractError, State
from .contracts import SIMULATED_PROVISIONAL, VERIFIED_JAVA


class ReplayState(State):
    def __init__(self, seed=1, test_mode=False):
        super().__init__("elevation", seed, test_mode)
        self.backend, self.scenario, self.origin = "REPLAY", "pinned-wire", "REAL_CAPTURE_REPLAY"
        raw = files("klab_simulator").joinpath("fixtures/replay-cases.json").read_bytes()
        if hashlib.sha256(raw).hexdigest() != "21fc00c78be5b48617354e8c4ffa2335c76fa96f7ee460ab4c692129c2582a45":
            raise ValueError("Pinned replay bytes changed; require a new attributable capture")
        self.cases = json.loads(raw)
        if len(self.cases) > 64:
            raise ValueError("Replay case budget exceeded")

    def _contract_category(self, method, path, body, status):
        route = unquote(urlsplit(path).path)
        if status == 200 and not urlsplit(path).query and any(
                case["method"] == method and case["path"] == route
                and body == (case.get("request_text") or "").encode() for case in self.cases):
            return VERIFIED_JAVA  # Payload provenance only; backend remains REPLAY.
        return SIMULATED_PROVISIONAL  # Replay auth/control/missing-case policy is ours.

    def _request(self, method, path, headers, body):
        if urlsplit(path).query:
            raise ContractError(501, "Query differs from pinned replay contract")
        route = unquote(urlsplit(path).path)
        if route.startswith("/__sim__/"):
            return super()._request(method, path, headers, body)
        if headers.get("Authorization") not in ("sim-token-alice", "sim-token-bob"):
            raise ContractError(401, "Replay accepts synthetic credentials only")
        matches = [case for case in self.cases if case["method"] == method and case["path"] == route
                   and body == (case.get("request_text") or "").encode()]
        if len(matches) != 1:
            raise ContractError(501, "No exact pinned replay case; no Java or simulator fallback")
        case = matches[0]
        if case["status"] != 200:
            raise ContractError(case["status"], "Pinned replay failure")
        return deepcopy(case["payload"]), "application/json"
