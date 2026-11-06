"""Finite synthetic semantic/authority fixture, never an ontology compiler/provider.

Native request/response shapes follow SemanticExplorer and Java Reasoner contracts.
The SIM vocabulary and all answers below are SIMULATED_PROVISIONAL, not claims about
real authority catalogues or native geography ontology parentage.
"""
from copy import deepcopy
from urllib.parse import parse_qs, urlsplit

from .state import ContractError, observable

CODES = ("ELEVATION", "ALTITUDE")
URNS = ("geography:Elevation", "SIM:ELEVATION", "SIM:ALTITUDE")


def concept(urn):
    if urn not in URNS:
        raise ContractError(404, "Definition is not in the finite declared simulator vocabulary")
    result = deepcopy(observable()["semantics"])
    result.update(urn=urn, displayLabel="Elevation")
    return result


def respond(state, method, route, original_path, data, body):
    if method == "POST" and route == "/api/v1/resolve/concept":
        try:
            return concept(body.decode("utf-8")), "application/json"
        except UnicodeError:
            raise ContractError(400, "Concept definition must be UTF-8") from None
    if method == "POST" and route == "/api/v1/authority/search":
        if not isinstance(data, dict):
            raise ContractError(400, "Authority search requires a native structured request")
        status = "OK" if data.get("authority") == "SIM" else "UNSUPPORTED"
        if state.scenario == "semantic-unavailable" and status == "OK":
            status = "UNAVAILABLE"
        if status != "OK":
            return {"status": status, "matches": [], "total": 0, "nextOffset": -1, "notifications": []}, "application/json"
        query, offset, limit = data.get("query"), data.get("offset"), data.get("limit")
        if (not isinstance(query, str) or not 1 <= len(query) <= 256 or data.get("filter") is not None
                or type(offset) is not int or not 0 <= offset <= 10000 or type(limit) is not int or not 1 <= limit <= 20):
            raise ContractError(422, "Finite authority fixture supports bounded unfiltered search only")
        rows = [{"id": code, "authorityName": "SIM", "locator": "SIM:" + code,
                 "label": "Elevation", "description": "Synthetic ambiguous-label candidate", "score": None,
                 "notifications": []} for code in CODES if query.casefold() in ("elevation", "altitude", code.casefold())]
        page = rows[offset:offset + limit]
        following = offset + limit if offset + limit < len(rows) else -1
        return {"status": "OK", "matches": page, "total": len(rows), "nextOffset": following, "notifications": []}, "application/json"
    if method == "GET" and route == "/api/v1/authority/documentation":
        query = parse_qs(urlsplit(original_path).query)
        if query.get("authority") != ["SIM"] or len(query.get("identity", [])) != 1 or query["identity"][0] not in CODES:
            raise ContractError(404, "Unknown synthetic authority identity")
        return {"text/html": "https://docs.invalid/simulator/" + query["identity"][0]}, "application/json"
    if method == "POST" and route in ("/api/v1/parents", "/api/v1/children", "/api/v1/satisfiable", "/api/v1/subsumes"):
        if route.endswith("/subsumes"):
            if not isinstance(data, list) or len(data) != 2:
                raise ContractError(400, "Subsumption requires two declared native concepts")
            urns = [row.get("urn") if isinstance(row, dict) else None for row in data]
            for urn in urns:
                concept(urn)
            return (urns[0] == urns[1] or urns[0].startswith("SIM:") and urns[1] == "geography:Elevation"), "application/json"
        urn = data.get("urn") if isinstance(data, dict) else None
        concept(urn)
        if route.endswith("/satisfiable"):
            return True, "application/json"
        if route.endswith("/parents"):
            return ([concept("geography:Elevation")] if urn.startswith("SIM:") else []), "application/json"
        return ([concept("SIM:" + code) for code in CODES] if urn == "geography:Elevation" else []), "application/json"
    return None
