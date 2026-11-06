"""Finite explicit input binding, not a k.IM parser/compiler or local model engine.

The exact source form `model <resource URN> as <observable>;` was accepted in the
historical USGS namespace receipt. This module accepts ONE declared fixture source;
all other source is refused, not interpreted. Admission/selection remain provisional.
"""
import json
from urllib.parse import parse_qs, urlsplit

from .state import ContractError

NAMESPACE = "simulator.uploaded"
RESOURCE = "simulator:alice:uploaded"
MODEL = NAMESPACE + ".elevation-quantifier"
SOURCE = f"namespace {NAMESPACE};\nmodel {RESOURCE} as geography:Elevation in m;\n"
CLASS = "org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl"


class AuthoringState:
    def __init__(self, state):
        self.state = state
        self.assets = {}

    def reset(self):
        self.assets.clear()

    def binding(self, scope, owner):
        row = self.assets.get(("NAMESPACE", NAMESPACE))
        resource = self.state.imported_resources.get(RESOURCE)
        if row and resource and row["owner"] == owner and resource["owner"] == owner and resource["scope"] == scope:
            return {"resourceUrn": RESOURCE, "modelUrn": MODEL, **resource["asset"]["metadata"]["delegate"]}
        raise ContractError(422, "Import fixture and explicitly admit its exact semantic source before resolving/computing")

    def request(self, method, route, original_path, principal, body):
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
        if len(parts) != 6 or parts[-1] != "ADD" or parts[-2] not in ("WORKSPACE", "PROJECT", "NAMESPACE"):
            raise ContractError(501, "Only bounded explicit ADD operations are simulated; no overwrite/history/lock protocol")
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
            if (urn != NAMESPACE or payload.get("urn") != NAMESPACE or payload.get("@CLASS") != CLASS
                    or payload.get("sourceCode") != SOURCE or len(projects) != 1):
                raise ContractError(422, "Namespace requires the exact fixture source and one owned project")
            self.state._owner(self.state.imported_resources, RESOURCE, principal)
        self.assets[(kind, urn)] = {"owner": principal, "payload": payload}
        descriptor = {"resourceUrn": urn.split("/", 1)[-1] if kind == "PROJECT" else urn,
            "knowledgeClass": kind, "operation": "CREATE", "serviceId": self.state.service_ids["resources"]}
        result = {"empty": False, "notifications": [], "results": [descriptor]}
        if kind == "PROJECT":
            result["workspace"] = urn.split("/", 1)[0]
        return [result], "application/json"
