"""Finite deterministic protocol state, not ontology reasoning or scientific computation.

DTO/route evidence is recorded in contract-cases.json. The synthetic formula and
poll-step scheduling are explicit simulator assumptions, not Java guarantees.
"""
from collections import deque
from copy import deepcopy
import json
import math
import re
import threading
from urllib.parse import unquote, urlsplit

from .contracts import SIMULATED_PROVISIONAL

OBS = "org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl"
ACT = "org.integratedmodelling.klab.api.provenance.impl.ActivityImpl"
GEO = "org.integratedmodelling.klab.api.geometry.impl.GeometryImpl"
MODEL = "simulator.elevation"
SERVICES = ("reasoner", "resources", "resolver", "runtime")
SCENARIOS = ("elevation", "zero-negative-nodata", "empty-result", "malformed-empty", "failure", "usgs-recorded", "http500")
EXPORT_SCENARIOS = ("export-interrupted", "export-media-mismatch", "export-denied")
SCENARIOS += EXPORT_SCENARIOS + ("import-workflow", "nonuniform-export", "semantic-unavailable")


class ContractError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


def observable():
    return {"@CLASS": "org.integratedmodelling.common.knowledge.ObservableImpl",
        "urn": "geography:Elevation in m", "artifactType": "NUMBER", "contextualization": "MEASURE",
        "semantics": {"@CLASS": "org.integratedmodelling.common.knowledge.ConceptImpl",
            "urn": "geography:Elevation", "type": ["QUALITY", "QUANTIFIABLE", "LENGTH", "INTENSIVE", "OBSERVABLE"],
            "collective": False, "notifications": []},
        "unit": {"@CLASS": "org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl",
                 "definition": "m", "contextual": False, "unitless": False, "aggregatedDimensions": {}},
        "resolutionDirectives": [], "optional": False, "notifications": []}


class State:
    def __init__(self, scenario="elevation", seed=1, test_mode=False):
        if scenario not in SCENARIOS or type(seed) is not int or not 0 <= seed < 2**31:
            raise ValueError("Choose a known scenario and bounded integer seed")
        self.scenario, self.seed, self.test_mode = scenario, seed, test_mode
        self.backend = "SIMULATED"
        self.service_ids = {name: f"sim-{name}-{seed}" for name in SERVICES}
        self.sessions, self.contexts, self.jobs, self.observations = {}, {}, {}, {}
        self.imported_resources = {}
        self.query_faults = {}
        self.worldview_id = "synthetic"
        from .authoring import AuthoringState
        self.authoring = AuthoringState(self)
        self.next_context, self.next_job, self.next_observation = 1, 1, 1
        self.trace = deque(maxlen=4096)
        self.lock = threading.RLock()
        self.values = [100 + 2 * row + 3 * column for row in range(20) for column in range(20)]
        self.grid = {"shape": [20, 20], "bounds": [0, 0, 2000, 2000], "crs": "EPSG:3857",
                     "unit": "m", "row_orientation": "north_to_south", "axis_order": "x,y",
                     "centre_origin": [50, 1950], "cell_size": [100, -100], "precision": "float64"}
        self.origin = "SYNTHETIC_SIMULATOR"
        if scenario == "zero-negative-nodata":
            self.values[:4] = [0, -7.5, None, "NaN"]
        if scenario in ("usgs-recorded", "import-workflow") or scenario in EXPORT_SCENARIOS:
            from .fixtures import authentic_fixture
            fixture = authentic_fixture()
            self.values = [x for row in fixture["allCellsNorthToSouthWestToEast"] for x in row]
            self.grid.update(bounds=fixture["bounds"], crs="EPSG:4269", precision="float32",
                             cell_size=[fixture["affine"][0], fixture["affine"][4]],
                             centre_origin=[fixture["affine"][2] + fixture["affine"][0] / 2,
                                            fixture["affine"][5] + fixture["affine"][4] / 2])
            self.origin = "AUTHENTIC_FIXTURE_SIMULATED_DELIVERY"
        if scenario == "nonuniform-export":
            from .fixtures import nonuniform_fixture
            fixture, _ = nonuniform_fixture()
            self.grid.update(shape=fixture["shape"], bounds=[200000, 6000000, 200500, 6000400],
                centre_origin=[200050, 6000350])

    def counts(self):
        return {"sessions": len(self.sessions), "contexts": len(self.contexts),
                "jobs": len(self.jobs), "observations": len(self.observations)}

    def _owner(self, mapping, key, principal):
        if key not in mapping:
            raise ContractError(404, "Unknown or released simulator identity")
        row = mapping[key]
        if row["owner"] != principal:
            raise ContractError(403, "Private scope is not authorized for this synthetic principal")
        return row

    def _context(self, scope, principal):
        root = ".".join(scope.split("#")[0].split(".")[:2])
        return root, self._owner(self.contexts, root, principal)

    def _job(self, service, scope, owner, result):
        if len(self.jobs) >= 128:
            raise ContractError(429, "Simulator job budget exceeded")
        identity = self.next_job
        self.next_job += 1
        self.jobs[(service, identity)] = {"owner": owner, "scope": scope, "result": result,
                                        "poll": 0, "status": "WAITING"}
        return identity

    def _observation(self, context, request):
        identity = self.next_observation
        self.next_observation += 1
        if request.get("observable", {}).get("urn") != "geography:Elevation in m":
            raise ContractError(422, "Only the declared fixture observable is supported")
        geometry = request.get("geometry")
        if not isinstance(geometry, dict) or not geometry.get("dimensions"):
            raise ContractError(400, "The vertical slice requires an explicit native grid geometry")
        rows, columns = self.grid["shape"]
        if geometry["dimensions"][0].get("shape") != [columns, rows]:
            raise ContractError(422, "This scenario supports exactly its declared native fixture shape")
        parameters = geometry["dimensions"][0].get("parameters", {}).get("delegate", {})
        polygon = parameters.get("shape", "").split("POLYGON", 1)
        numbers = re.findall(r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?", polygon[1]) if len(polygon) == 2 else []
        west, south, east, north = self.grid["bounds"]
        expected = [west, south, west, north, east, north, east, south, west, south]
        if (parameters.get("proj") != self.grid["crs"] or len(numbers) != 10
                or any(not math.isclose(float(a), b, rel_tol=0, abs_tol=1e-10) for a, b in zip(numbers, expected))):
            raise ContractError(422, "Requested bounds/CRS differ from this fixed scenario; no resampling is simulated")
        if "sgrid" in parameters:
            span = re.fullmatch(r"([0-9.]+)\s+m", str(parameters["sgrid"]))
            if self.grid["crs"] != "EPSG:3857" or span is None or float(span[1]) != 100:
                raise ContractError(422, "Declared grid span differs from the fixed fixture; no implicit mediation")
        result = {"@CLASS": OBS, "id": identity, "urn": f"{context}.{identity}", "name": "elevation",
            "observable": observable(), "geometry": deepcopy(geometry), "empty": False, "notifications": [],
            "resolvedCoverage": 1.0, "metadata": {"delegate": {"grid": deepcopy(self.grid)}}}
        if self.scenario == "import-workflow":
            owner = self.contexts[context]["owner"]
            result["metadata"]["delegate"]["input"] = self.authoring.binding(context, owner)
        return result

    def request(self, method, path, headers, body=b""):
        with self.lock:
            status, result, content_type = 200, None, "application/json"
            try:
                result, content_type = self._request(method, path, headers, body)
            except ContractError as error:
                status, result = error.status, {"error": error.message}
            self.trace.append({"backend": self.backend, "scenario": self.scenario, "seed": self.seed,
                "method": method, "path": urlsplit(path).path,
                "status": None if content_type == "application/x-simulator-disconnect" else status,
                "transport_outcome": "DISCONNECTED_BY_TEST" if content_type == "application/x-simulator-disconnect" else "RESPONSE",
                "workflow": headers.get("klab-scope"), "fixture_origin": self.origin,
                "contract_category": self._contract_category(method, path, body, status)})
            return status, result, content_type

    def _contract_category(self, method, path, body, status):
        # Native DTO provenance does not establish the finite state machine as Java.
        return SIMULATED_PROVISIONAL

    def _request(self, method, path, headers, body):
        original_path = path
        path = unquote(urlsplit(path).path)
        if path.startswith("/__sim__/"):
            if not self.test_mode or headers.get("Authorization") != "sim-admin-control":
                raise ContractError(403, "Test controls disabled or unauthorized")
            if method == "GET" and path == "/__sim__/state":
                return {"counts": self.counts(), "resources": len(self.imported_resources),
                        "authoring_assets": len(self.authoring.assets), "trace": list(self.trace)}, "application/json"
            if method == "POST" and path == "/__sim__/expireJobs":
                for job in self.jobs.values():
                    job["expired"] = True
                return True, "application/json"
            if method == "POST" and path == "/__sim__/queryFault":
                if len(body) > 4096:
                    raise ContractError(413, "Fault-control byte budget exceeded")
                try:
                    control = json.loads(body)
                except (ValueError, UnicodeError):
                    raise ContractError(400, "Fault control requires a bounded object") from None
                if (not isinstance(control, dict) or set(control) != {"scope", "mode"}
                        or not isinstance(control["scope"], str)
                        or control["scope"] not in self.contexts
                        or control["mode"] not in ("none", "denied", "http500", "disconnect")):
                    raise ContractError(422, "Select an existing simulator context and declared query fault")
                if control["mode"] == "none":
                    self.query_faults.pop(control["scope"], None)
                else:
                    self.query_faults[control["scope"]] = control["mode"]
                return True, "application/json"
            if method == "POST" and path == "/__sim__/changeWorldview":
                self.worldview_id = "synthetic-next"
                return True, "application/json"
            if method == "POST" and path == "/__sim__/reset":
                self.sessions.clear()
                self.contexts.clear()
                self.jobs.clear()
                self.observations.clear()
                self.imported_resources.clear()
                self.query_faults.clear()
                self.authoring.reset()
                self.worldview_id = "synthetic"
                self.next_context = self.next_job = self.next_observation = 1
                return True, "application/json"
            raise ContractError(404, "Unknown test control")
        pieces = path.strip("/").split("/", 1)
        if len(pieces) != 2 or pieces[0] not in SERVICES:
            raise ContractError(404, "A named service prefix is required")
        service, route = pieces[0], "/" + pieces[1]
        if method == "GET" and route in ("/public/health", "/public/status", "/public/capabilities"):
            if route.endswith("health"):
                return {"status": "UP"}, "application/json"
            if route.endswith("status"):
                return {"serviceId": self.service_ids[service], "serviceType": service.upper(),
                        "available": True, "operational": True, "connected": True}, "application/json"
            from .resource_imports import schemas
            from .exports import export_schemas
            return {"serviceId": self.service_ids[service], "type": service.upper(), "version": "simulator-0.1.0",
                    "worldviewUrn": self.worldview_id, "worldviewId": self.worldview_id, "consistent": True,
                    "importSchemata": schemas() if service == "resources" else {},
                    "exportSchemata": export_schemas() if service == "runtime" and (
                        self.origin == "AUTHENTIC_FIXTURE_SIMULATED_DELIVERY" or self.scenario == "nonuniform-export") else {}}, "application/json"
        principal = {"sim-token-alice": "alice", "sim-token-bob": "bob"}.get(headers.get("Authorization"))
        if principal is None:
            raise ContractError(401, "A synthetic test credential is required")
        scope = headers.get("klab-scope", "")
        if self.scenario == "http500" and service == "resources" and route.startswith("/api/v1/retrieve/"):
            raise ContractError(500, "Explicitly injected Resources failure; no fallback")
        if len(body) > 1024 * 1024:
            raise ContractError(413, "Simulator request budget exceeded")
        try:
            data = json.loads(body) if body and headers.get("Content-Type", "").startswith("application/json") else None
        except (ValueError, UnicodeError):
            raise ContractError(400, "Invalid JSON request") from None
        if data is not None and not isinstance(data, dict) and not (service == "reasoner" and route == "/api/v1/subsumes"):
            raise ContractError(400, "A JSON object is required")
        if method == "POST" and route == "/notifyUserScope":
            if not isinstance(data, dict) or not isinstance(data.get("services"), list):
                raise ContractError(400, "Scope advertisement requires service descriptors")
            return True, "application/json"
        if service == "reasoner" and method == "POST" and route == "/api/v1/resolve/observable":
            if body.decode() != "geography:Elevation in m":
                raise ContractError(404, "Observable not in finite simulator vocabulary")
            return observable(), "application/json"
        if service == "reasoner":
            from .semantics import respond
            semantic = respond(self, method, route, original_path, data, body)
            if semantic is not None:
                return semantic
        if service == "resources":
            authored = self.authoring.request(method, route, original_path, principal, body)
            if authored is not None:
                return authored
            if method == "POST" and route.startswith("/import/"):
                from .resource_imports import SCHEMA, parse_upload
                root, _ = self._context(scope, principal)
                parts = route.split("/", 3)
                if len(parts) != 4 or parts[2] != SCHEMA:
                    raise ContractError(422, "Importer is not advertised")
                urn = parts[3]
                if not re.fullmatch(r"[A-Za-z0-9:_.-]{1,256}", urn):
                    raise ContractError(422, "Finite importer requires a bounded simple resource URN")
                if urn in self.imported_resources or any(
                        key[0] == "resources" and row.get("import_urn") == urn for key, row in self.jobs.items()):
                    raise ContractError(409, "Resource already present or import pending; overwrite/replay is not supported")
                if sum("import_urn" in row for row in self.jobs.values()) >= 32:
                    raise ContractError(429, "Simulator resource budget exceeded")
                source = parse_upload(headers, body)
                result = {"empty": False, "notifications": [], "results": [{"resourceUrn": urn,
                    "knowledgeClass": "RESOURCE", "serviceId": self.service_ids["resources"]}]}
                identity = self._job(service, root, principal, result)
                self.jobs[(service, identity)].update(import_urn=urn, import_source=source)
                return identity, "application/json"
            catalog = {"PROJECT": [{"urn": "simulator.project"}], "NAMESPACE": [{"urn": "simulator"}],
                "MODEL": [{"urn": MODEL, "projectName": "simulator.project", "namespace": "simulator",
                    "observables": [{"urn": "geography:Elevation in m", "unit": "m", "optional": False}],
                    "dependencies": [], "resourceUrns": ["simulator:fixture:elevation"]}],
                "RESOURCE": [{"urn": "simulator:fixture:elevation", "adapterType": "raster"}]}
            from .authoring import MODEL as INPUT_MODEL, NAMESPACE, RESOURCE
            namespace = self.authoring.assets.get(("NAMESPACE", NAMESPACE))
            if namespace and namespace["owner"] == principal and RESOURCE in self.imported_resources:
                payload = deepcopy(namespace["payload"])
                catalog["NAMESPACE"].append(payload)
                catalog["MODEL"].append({"urn": INPUT_MODEL, "namespace": NAMESPACE,
                    "projectName": payload["projectName"], "resourceUrns": [RESOURCE],
                    "observables": [{"urn": "geography:Elevation in m", "unit": "m", "optional": False}], "dependencies": []})
            for kind, entries in catalog.items():
                if method == "GET" and route == "/api/v1/list/" + kind:
                    if kind == "RESOURCE":
                        entries = entries + [deepcopy(row["asset"]) for row in self.imported_resources.values()
                                             if row["owner"] == principal]
                    return entries, "application/json"
                resolve_prefix = "/api/v1/resolve/" + kind + "/"
                if method == "GET" and route.startswith(resolve_prefix):
                    urn = route[len(resolve_prefix):]
                    if kind == "RESOURCE" and urn in self.imported_resources:
                        selected = [self._owner(self.imported_resources, urn, principal)["asset"]]
                    else:
                        selected = [row for row in entries if row["urn"] == urn]
                    if not selected:
                        raise ContractError(404, "Missing finite catalog asset")
                    return {"empty": False, "results": [{"resourceUrn": urn, "knowledgeClass": kind,
                        "serviceId": self.service_ids["resources"]}],
                        "services": {self.service_ids["resources"]: "https://catalog.invalid/resources"}}, "application/json"
                prefix = "/api/v1/retrieve/" + kind + "/"
                if method == "GET" and route.startswith(prefix):
                    urn = route[len(prefix):]
                    if kind == "RESOURCE" and urn in self.imported_resources:
                        row = self._owner(self.imported_resources, urn, principal)
                        return deepcopy(row["asset"]), "application/json"
                    selected = [x for x in entries if x["urn"] == route[len(prefix):]]
                    if not selected:
                        raise ContractError(404, "Missing finite catalog asset")
                    return selected[0], "application/json"
        if service == "runtime" and method == "POST" and route == "/createSession":
            identity = (data or {}).get("configuration", {}).get("id")
            if not isinstance(identity, str) or not identity.isalnum() or len(identity) > 64:
                raise ContractError(400, "Caller-specified simple session identity required")
            if identity in self.sessions:
                raise ContractError(409, "Duplicate session identity; no invented idempotent replay")
            if len(self.sessions) >= 16:
                raise ContractError(429, "Simulator session budget exceeded")
            self.sessions[identity] = {"owner": principal}
            return identity, "text/plain"
        if service == "runtime" and method == "GET" and route.startswith("/export/OBSERVATION/"):
            from .exports import export_bytes
            root, _ = self._context(scope, principal)
            if headers.get("klab-service") != self.service_ids["runtime"]:
                raise ContractError(403, "Export requires the bound originating Runtime identity")
            urn = route[len("/export/OBSERVATION/"):]
            matches = [row for (context, identity), row in self.observations.items()
                       if context == root and urn == f"{root}.{identity}"]
            if len(matches) != 1:
                raise ContractError(404, "Observation is not materialized in the selected context")
            return export_bytes(self, headers, original_path)
        if service == "runtime" and method == "POST" and route == "/createContext":
            self._owner(self.sessions, scope, principal)
            configuration = (data or {}).get("configuration", {})
            if not isinstance(configuration, dict) or configuration.get("persistence", "ONE_OFF") != "ONE_OFF":
                raise ContractError(422, "This finite scenario supports explicit ONE_OFF contexts only")
            if len(self.contexts) >= 32:
                raise ContractError(429, "Simulator context budget exceeded")
            identity = f"{scope}.c{self.next_context}"
            self.next_context += 1
            config = {"id": identity, "serviceId": self.service_ids[service], "persistence": "ONE_OFF"}
            self.contexts[identity] = {"owner": principal, "configuration": config}
            return config, "application/json"
        if service == "runtime" and method == "POST" and route == "/api/v1/connect":
            identity = (data or {}).get("configuration", {}).get("id", "")
            return self._owner(self.contexts, identity, principal)["configuration"], "application/json"
        if service == "runtime" and method == "GET" and route in ("/releaseContext", "/releaseSession"):
            if route == "/releaseSession":
                self._owner(self.sessions, scope, principal)
                roots = [key for key in self.contexts if key.split(".")[0] == scope]
                del self.sessions[scope]
            else:
                root, _ = self._context(scope, principal)
                roots = [root]
            for root in roots:
                del self.contexts[root]
                self.query_faults.pop(root, None)
                self.jobs = {key: row for key, row in self.jobs.items() if row["scope"] != root}
                self.observations = {key: row for key, row in self.observations.items() if key[0] != root}
                self.imported_resources = {key: row for key, row in self.imported_resources.items() if row["scope"] != root}
            return True, "application/json"
        if route.startswith("/jobs/") and method == "GET":
            try:
                identity = int(route.rsplit("/", 1)[1])
            except ValueError:
                raise ContractError(400, "Invalid Job identity") from None
            job = self._owner(self.jobs, (service, identity), principal)
            root, _ = self._context(scope, principal)
            if job["scope"] != root:
                raise ContractError(403, "Job belongs to a different context")
            if job.get("expired"):
                # JobManager.status returns native EMPTY for absent/expired results;
                # the test control is provisional, not a Java retention duration.
                if "/status/" in route:
                    return {"status": "EMPTY"}, "application/json"
                if "/cancel/" in route:
                    return False, "application/json"
                raise ContractError(409, "Expired result is unavailable; no synthetic replay")
            if "/cancel/" in route:
                accepted = job["status"] not in ("FINISHED", "ABORTED", "INTERRUPTED")
                if accepted:
                    job["status"] = "INTERRUPTED"
                return accepted, "application/json"
            if "/status/" in route:
                if job["status"] not in ("FINISHED", "ABORTED", "INTERRUPTED"):
                    job["status"] = ("WAITING", "STARTED", "FINISHED")[min(job["poll"], 2)]
                    job["poll"] += 1
                    if self.scenario == "failure" and job["status"] == "FINISHED":
                        job["status"] = "ABORTED"
                    if (job["status"] == "FINISHED" and service == "runtime"
                            and self.scenario not in ("empty-result", "malformed-empty")):
                        result = job["result"]
                        self.observations[(root, result["id"])] = result
                    if job["status"] == "FINISHED" and service == "resources" and "import_urn" in job:
                        urn = job["import_urn"]
                        self.imported_resources[urn] = {"owner": principal, "scope": root,
                            "asset": {"urn": urn, "adapterType": "raster", "metadata": {"delegate": job["import_source"]}}}
                return {"status": job["status"]}, "application/json"
            if "/retrieve/" in route:
                if job["status"] != "FINISHED":
                    raise ContractError(409, "Job has not finished")
                if self.scenario == "empty-result":
                    return {"empty": True, "notifications": []}, "application/json"
                if self.scenario == "malformed-empty":
                    return {"empty": "false"}, "application/json"
                return job["result"], "application/json"
        if service in ("runtime", "resolver") and method == "POST" and route in ("/api/v1/submit", "/api/v1/resolve"):
            root, _ = self._context(scope, principal)
            if headers.get("klab-service") != self.service_ids["runtime"]:
                raise ContractError(403, "Originating Runtime identity does not match this simulator")
            if not isinstance(data, dict) or not isinstance(data.get("observation"), dict):
                raise ContractError(400, "Submission requires an observation envelope")
            if data.get("resolutionConstraints"):
                raise ContractError(422, "Resolution constraints are not implemented by this fixed default-model scenario")
            if service == "resolver":
                selected_model = MODEL
                binding = None
                if self.scenario == "import-workflow":
                    binding = self.authoring.binding(root, principal)
                    selected_model = binding["modelUrn"]
                result = {"resolutionOutcome": "RESOLVED", "empty": False, "notifications": [],
                    "computation": [{"id": -1, "actuatorType": "OBSERVE", "strategyUrn": "simulator.elevation.strategy",
                                     "computation": [{"urn": selected_model}], "children": []}],
                    "requirements": {"results": [{"resourceUrn": selected_model, "knowledgeClass": "MODEL",
                                                  "serviceId": self.service_ids["resources"]}]},
                    "resolvedCoverage": 1.0, "coverage": {"definition": "S2(20,20)"}}
                if binding:
                    result["requirements"]["results"].append({"resourceUrn": binding["resourceUrn"],
                        "knowledgeClass": "RESOURCE", "serviceId": self.service_ids["resources"]})
            else:
                result = self._observation(root, data["observation"])
            return self._job(service, root, principal, result), "application/json"
        if service == "runtime" and method == "POST" and route in ("/api/v1/observation/value", "/api/v1/query"):
            root, _ = self._context(scope, principal)
            fault = self.query_faults.get(root) if route.endswith("/query") else None
            if fault in ("denied", "http500"):
                raise ContractError(403 if fault == "denied" else 500, "Explicit isolated query fault; no fallback")
            if fault == "disconnect":
                return None, "application/x-simulator-disconnect"
            if not isinstance(data, dict):
                raise ContractError(400, "A structured request is required")
            if route.endswith("/value"):
                if self.scenario == "nonuniform-export":
                    raise ContractError(422, "This fixture proves raster export only; native point-offset mapping is not established")
                identity, offset = data.get("sourceId"), data.get("offset")
                if type(identity) is not int or not 1 <= identity <= 2**63 - 1:
                    raise ContractError(422, "A positive native observation identity is required")
                if (root, identity) not in self.observations:
                    raise ContractError(404, "Observation absent from this context")
                if type(offset) is not int or not 0 <= offset < 400:
                    raise ContractError(422, "Cell offset outside the declared native grid")
                semantics = data.get("semantics")
                native = semantics is None or (isinstance(semantics, dict)
                    and semantics.get("unit") == "m" and semantics.get("observable") == "geography:Elevation in m"
                    and semantics.get("range", "") == "" and semantics.get("currency", "") == ""
                    and semantics.get("contextualDimensions", "{}") == "{}"
                    and semantics.get("meaning", "geography:Elevation|MEASURE|") == "geography:Elevation|MEASURE|")
                if (not native or data.get("curve") != "UNSPECIFIED"
                        or data.get("slice") != {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0}):
                    raise ContractError(422, "This fixed fixture has only native metre/initialization/declared-offset reads; no silent mediation")
                value = self.values[offset]
                return ("null" if value is None else str(value)), "text/plain"
            if data.get("resultType") == "OBSERVATION":
                row = self.observations.get((root, data.get("id")))
                return [row] if row else [], "application/json"
            if data.get("resultType") == "LINK":
                rows = [{"@CLASS": "org.integratedmodelling.klab.api.data.impl.LinkImpl", "relationship": "MEASURED",
                    "source": {"@CLASS": ACT, "id": identity + 1000, "urn": f"{root}.activity.{identity + 1000}",
                               "type": "MEASURE", "outcome": "SUCCESS"},
                    "target": {"@CLASS": OBS, "id": identity, "urn": row["urn"]},
                    "sequence": 0, "properties": {"sequence": 0}}
                    for (context, identity), row in self.observations.items() if context == root]
                offset, limit = data.get("offset", 0), data.get("limit", 64)
                if type(offset) is not int or offset < 0 or type(limit) is not int or not 1 <= limit <= 129:
                    raise ContractError(422, "Graph page exceeds the bounded scenario contract")
                children = data.get("children", [data])
                def matches(row, child):
                    if child.get("relationship") not in (None, row["relationship"]):
                        return False
                    return all(side not in child or (
                        child[side].get("id") == row[side]["id"]
                        and child[side].get("type") == ("ACTIVITY" if side == "source" else "OBSERVATION"))
                        for side in ("source", "target"))
                rows = [row for row in rows if any(matches(row, child) for child in children)]
                return rows[offset:offset + limit], "application/json"
        raise ContractError(501, "Route/method not implemented by this finite simulator contract")
