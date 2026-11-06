"""Finite explicit input binding, not a k.IM parser/compiler or local model engine.

The exact source form `model <resource URN> as <observable>;` was accepted in the
historical USGS namespace receipt. This module accepts that one namespace fixture
and the one nonexecuting BEHAVIOR draft required by SIM-BEHAVIOR-DRAFT-001.
All other source is refused, not interpreted. Admission/selection remain provisional.
"""
import json
from urllib.parse import parse_qs, urlsplit

from .state import ContractError

NAMESPACE = "simulator.uploaded"
RESOURCE = "simulator:alice:uploaded"
MODEL = NAMESPACE + ".elevation-quantifier"
SOURCE = f"namespace {NAMESPACE};\nmodel {RESOURCE} as geography:Elevation in m;\n"
CLASS = "org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl"
BEHAVIOR = "simulator.draft"
BEHAVIOR_CLASS = "org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl"
# Literal synthetic draft only; not parsing, translation, compilation or execution.
BEHAVIOR_SOURCE = '''behavior simulator.draft
    "Finite synthetic source-only draft."
    version 1.0

action main:
    return 0
'''


class AuthoringState:
    def __init__(self, state):
        self.state = state
        self.assets = {}
        self.project_locks = {}

    def reset(self):
        self.assets.clear()
        self.project_locks.clear()

    def project_lock(self, route, principal):
        # SIM-PROJECT-LOCK-001 only. Principal-shared booleans, not editor leases.
        parts = route.split("/")
        if len(parts) != 6:
            raise ContractError(422, "One simple local project identifier is required")
        action, project = parts[-2:]
        projects = [key for kind, key in self.assets if kind == "PROJECT" and key.split("/", 1)[-1] == project]
        if len(projects) != 1:
            return False, "application/json"
        owner = self.project_locks.get(project)
        if action == "lock":
            if owner not in (None, principal):
                return False, "application/json"
            self.project_locks[project] = principal
            return True, "application/json"
        if owner != principal:
            return False, "application/json"
        del self.project_locks[project]
        return True, "application/json"

    def binding(self, scope, owner):
        row = self.assets.get(("NAMESPACE", NAMESPACE))
        resource = self.state.imported_resources.get(RESOURCE)
        if row and resource and row["owner"] == owner and resource["owner"] == owner and resource["scope"] == scope:
            return {"resourceUrn": RESOURCE, "modelUrn": MODEL, **resource["asset"]["metadata"]["delegate"]}
        raise ContractError(422, "Import fixture and explicitly admit its exact semantic source before resolving/computing")

    def request(self, method, route, original_path, principal, body):
        if method == "POST" and route == "/parseAsset/BEHAVIOR":
            if body != BEHAVIOR_SOURCE.encode("utf-8"):
                raise ContractError(422, "Only the literal SIM-BEHAVIOR-DRAFT-001 source is admitted; no language parser")
            return {"@CLASS": BEHAVIOR_CLASS, "urn": BEHAVIOR, "sourceCode": BEHAVIOR_SOURCE,
                    "behaviorType": "BEHAVIOR", "notifications": [], "metadata": {}}, "application/json"
        if method == "POST" and route == "/parseAsset/NAMESPACE":
            try:
                source = body.decode("utf-8")
            except UnicodeError:
                raise ContractError(400, "Source must be UTF-8") from None
            if source != SOURCE:
                raise ContractError(422, "Only the declared finite fixture source is supported; no ontology compiler is simulated")
            return {"@CLASS": CLASS, "urn": NAMESPACE, "sourceCode": SOURCE, "notifications": [],
                    "metadata": {}, "statements": []}, "application/json"
        if method != "PUT" or not route.startswith("/api/v1/submit/"):
            return None
        parts = route.split("/")
        if len(parts) != 6 or parts[-1] != "ADD" or parts[-2] not in ("WORKSPACE", "PROJECT", "NAMESPACE", "BEHAVIOR"):
            raise ContractError(501, "Only bounded explicit ADD operations are simulated; no source overwrite/history protocol")
        kind = parts[-2]
        urns = parse_qs(urlsplit(original_path).query).get("urn", [])
        if len(urns) != 1 or not 0 < len(urns[0]) <= 256:
            raise ContractError(400, "One explicit asset URN is required")
        urn = urns[0]
        try:
            payload = json.loads(body)
        except (ValueError, UnicodeError):
            raise ContractError(400, "Native ADD requires a JSON source object") from None
        if not isinstance(payload, dict):
            raise ContractError(400, "Native ADD requires an object")
        if (kind, urn) in self.assets:
            raise ContractError(409, "ADD cannot overwrite an existing asset")
        if len(self.assets) >= 16:
            raise ContractError(429, "Finite authoring asset budget exceeded")
        if kind == "WORKSPACE":
            if payload != {"urn": urn} or not urn.isidentifier():
                raise ContractError(422, "Choose a simple workspace identity")
        elif kind == "PROJECT":
            workspace, separator, name = urn.partition("/")
            parent = self.assets.get(("WORKSPACE", workspace))
            if not separator or not name.isidentifier() or payload != {"urn": urn} or not parent or parent["owner"] != principal:
                raise ContractError(403, "Project requires an owned explicit workspace")
        else:
            projects = [row for (asset_kind, key), row in self.assets.items() if asset_kind == "PROJECT"
                        and key.split("/", 1)[-1] == payload.get("projectName") and row["owner"] == principal]
            expected_urn, expected_class, expected_source = ((NAMESPACE, CLASS, SOURCE) if kind == "NAMESPACE"
                else (BEHAVIOR, BEHAVIOR_CLASS, BEHAVIOR_SOURCE))
            if (urn != expected_urn or payload.get("urn") != expected_urn or payload.get("@CLASS") != expected_class
                    or payload.get("sourceCode") != expected_source or len(projects) != 1
                    or (kind == "BEHAVIOR" and payload.get("behaviorType") != "BEHAVIOR")):
                raise ContractError(422, "Document requires its exact fixture source and one owned project")
            if kind == "NAMESPACE":
                self.state._owner(self.state.imported_resources, RESOURCE, principal)
        self.assets[(kind, urn)] = {"owner": principal, "payload": payload}
        descriptor = {"resourceUrn": urn.split("/", 1)[-1] if kind == "PROJECT" else urn,
            "knowledgeClass": kind, "operation": "CREATE", "serviceId": self.state.service_ids["resources"]}
        result = {"empty": False, "notifications": [], "results": [descriptor]}
        if kind == "PROJECT":
            result["workspace"] = urn.split("/", 1)[0]
        return [result], "application/json"
