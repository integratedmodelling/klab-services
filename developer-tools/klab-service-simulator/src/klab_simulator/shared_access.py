"""Independent ephemeral proposed READ grants, never a native Java ACL implementation."""
from copy import deepcopy
import time
import uuid
from urllib.parse import parse_qs, urlsplit

from .state import ContractError

PROTOCOL = "klab.experimental.shared-access/1"
PREFIX = "/experimental/shared-access/v1/"
OWNER_RIGHTS = ["READ", "SUBMIT", "RELEASE", "SHARE"]


class SharedReads:
    def __init__(self, state, clock=time.time):
        self.state, self.clock = state, clock
        self.rows = {}

    def release(self, root):
        self.rows = {identity: row for identity, row in self.rows.items() if row["contextId"] != root}

    def _read(self, root, principal):
        candidates = [row for row in self.rows.values() if row["contextId"] == root and row["grantee"] == principal]
        if not candidates: raise ContractError(403, "No shared read grant for current authenticated principal")
        row = candidates[-1]
        if row["revoked"]: raise ContractError(403, "Read grant revoked; owner state unchanged")
        if row["expiresAt"] <= self.clock(): raise ContractError(410, "Read grant expired; do not recreate or resubmit")
        return row

    def _wire(self, row):
        return {key: deepcopy(row[key]) for key in ("protocol", "contextId", "grantId", "grantee", "expiresAt", "rights")}

    def respond(self, service, method, route, principal, scope, headers, data, original_path):
        if service != "runtime": return None
        if self.state.store is not None:
            raise ContractError(422, "Shared-read grants are ephemeral only; no durable authorization claim")
        connecting = method == "POST" and route == "/api/v1/connect"
        configuration = data.get("configuration") if isinstance(data, dict) else None
        selected = configuration.get("id", "") if connecting and isinstance(configuration, dict) else scope
        if not isinstance(selected, str): raise ContractError(422, "Shared root identity must be text")
        root = ".".join(selected.split("#")[0].split(".")[:2])
        context = self.state.contexts.get(root)
        if context is None:
            if route.startswith(PREFIX) or connecting: raise ContractError(404, "Unknown or released shared root")
            return None
        owner = context["owner"]
        if route.startswith(PREFIX):
            if selected != root or headers.get("klab-service") != self.state.service_ids["runtime"]:
                raise ContractError(422, "Shared-read management requires bound root and Runtime identity")
            query = parse_qs(urlsplit(original_path).query)
            protocol = query.get("protocol", [None])[0] if method == "GET" else data.get("protocol") if isinstance(data, dict) else None
            if protocol != PROTOCOL: raise ContractError(422, "Explicit shared-read protocol required")
            if method == "GET" and route == PREFIX + "access":
                grant = None if principal == owner else self._read(root, principal)
                return {"protocol": PROTOCOL, "contextId": root, "principal": principal, "owner": owner,
                    "rights": OWNER_RIGHTS[:] if grant is None else ["READ"],
                    "grantId": grant["grantId"] if grant else None,
                    "expiresAt": grant["expiresAt"] if grant else None}, "application/json"
            if principal != owner: raise ContractError(403, "Only current owner can grant or revoke")
            if method == "GET" and route == PREFIX + "grants":
                grantee = query.get("grantee", [None])[0]
                if grantee not in ("alice", "bob"): raise ContractError(422, "Explicit known grantee required")
                matches = [row for row in self.rows.values() if row["contextId"] == root and row["grantee"] == grantee]
                latest = matches[-1] if matches else None
                status = "ABSENT" if latest is None else "REVOKED" if latest["revoked"] else "EXPIRED" if latest["expiresAt"] <= self.clock() else "ACTIVE"
                return {"protocol": PROTOCOL, "contextId": root, "grantee": grantee, "state": status,
                    "grant": self._wire(latest) if latest is not None else None}, "application/json"
            if method == "POST" and route == PREFIX + "grants":
                if (not isinstance(data, dict) or set(data) != {"protocol", "grantee", "ttlSeconds"}
                        or data["grantee"] not in ("alice", "bob") or data["grantee"] == owner
                        or type(data["ttlSeconds"]) is not int or not 1 <= data["ttlSeconds"] <= 3600):
                    raise ContractError(422, "Grant requires known other principal and1..3600second READ TTL")
                existing = [row for row in self.rows.values() if row["contextId"] == root and row["grantee"] == data["grantee"]
                    and not row["revoked"] and row["expiresAt"] > self.clock()]
                if existing: raise ContractError(409, "A live grant already exists; explicitly revoke before replacing")
                if len(self.rows) >= 128: raise ContractError(429, "Grant/tombstone budget exceeded; explicit owner cleanup required")
                identity = uuid.uuid4().hex
                row = {"protocol": PROTOCOL, "contextId": root, "grantId": identity, "grantee": data["grantee"],
                    "expiresAt": self.clock() + data["ttlSeconds"], "rights": ["READ"], "revoked": False}
                self.rows[identity] = row
                return self._wire(row), "application/json"
            if method == "POST" and route.startswith(PREFIX + "grants/") and route.endswith("/revoke"):
                identity = route[len(PREFIX + "grants/"):-len("/revoke")]
                revoked_grant = self.rows.get(identity)
                if revoked_grant is None or revoked_grant["contextId"] != root: raise ContractError(404, "Grant absent from this owned root")
                if data != {"protocol": PROTOCOL}: raise ContractError(422, "Exact revocation protocol required")
                revoked_grant["revoked"] = True
                return {"protocol": PROTOCOL, "contextId": root, "grantId": identity, "revoked": True}, "application/json"
            raise ContractError(404, "Unknown shared-read reference operation")
        if principal == owner: return None
        self._read(root, principal)
        if headers.get("klab-service") not in (None, self.state.service_ids["runtime"]):
            raise ContractError(403, "Shared read originating Runtime does not match")
        if selected != root: raise ContractError(403, "Shared reference permits explicit root reads only")
        if connecting:
            return deepcopy(context["configuration"]), "application/json"
        if method == "GET" and route.startswith("/jobs/"):
            try: job_id = int(route.rsplit("/", 1)[1])
            except ValueError: raise ContractError(422, "Numeric Job identity required") from None
            job = self.state.jobs.get(("runtime", job_id))
            if job is None or job["scope"] != root: raise ContractError(404, "Job absent from shared root")
            if route.startswith("/jobs/status/"):
                return {"status": "EMPTY" if job.get("expired") else job["status"]}, "application/json"
            if route.startswith("/jobs/retrieve/"):
                if job.get("expired"): raise ContractError(409, "Expired result is unavailable; no replay")
                if job["status"] != "FINISHED": raise ContractError(409, "Shared result not finished")
                return deepcopy(job["result"]), "application/json"
            raise ContractError(403, "Read collaborator cannot cancel or mutate Jobs")
        if method == "POST" and route == "/api/v1/query":
            if (not isinstance(data, dict) or data.get("resultType") != "OBSERVATION"
                    or type(data.get("id")) is not int):
                raise ContractError(422, "Shared reference supports explicit observation-ID reads only")
            observation = self.state.observations.get((root, data["id"]))
            return [deepcopy(observation)] if observation else [], "application/json"
        if method == "POST" and route == "/api/v1/observation/value":
            if not isinstance(data, dict): raise ContractError(422, "Structured native point required")
            source_id, offset = data.get("sourceId"), data.get("offset")
            if type(source_id) is not int or (root, source_id) not in self.state.observations:
                raise ContractError(404, "Observation absent from shared root")
            if (type(offset) is not int or not 0 <= offset < 400 or data.get("semantics") is not None
                    or data.get("curve") != "UNSPECIFIED" or data.get("slice") !=
                    {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0}):
                raise ContractError(422, "Shared fixture supports only declared native-unit initialization points")
            value = self.state.values[offset]
            return "null" if value is None else str(value), "text/plain"
        raise ContractError(403, "Read collaborator cannot submit, write, release or use ungranted routes")
