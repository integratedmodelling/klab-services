"""Finite declared dependency/effect fixture, not scientific or committed history.

JAVA-GAP-0016/0013: native-shaped Link/Activity/Observation/Actuator DTOs, synthetic
edges only. Generated from completed observations in one owner root, no graph cache.
"""
OBS = "org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl"
ACT = "org.integratedmodelling.klab.api.provenance.impl.ActivityImpl"
ACTUATOR = "org.integratedmodelling.common.runtime.ActuatorImpl"


def validate_children(children):
    from .state import ContractError
    if (not isinstance(children, list) or not 1 <= len(children) <= 64
            or any(not isinstance(child, dict) for child in children)):
        raise ContractError(422, "Graph fixture requires1..64structured query children")
    for child in children:
        for side in ("source", "target"):
            if side in child:
                selector = child[side]
                if (not isinstance(selector, dict) or type(selector.get("id")) is not int
                        or not 1 <= selector["id"] <= 2**63 - 1
                        or selector.get("type") not in ("OBSERVATION", "ACTIVITY", "ACTUATOR", "COHORT")):
                    raise ContractError(422, "Graph fixture requires exact typed positive endpoint identities")


def rows(state, root):
    observations = [(identity, row) for (context, identity), row in state.observations.items() if context == root]
    if state.scenario == "provenance-dependencies":
        observations.sort()
    result = []
    for identity, row in observations:
        result.append({"@CLASS": "org.integratedmodelling.klab.api.data.impl.LinkImpl", "relationship": "MEASURED",
            "source": {"@CLASS": ACT, "id": identity + 1000, "urn": f"{root}.activity.{identity + 1000}",
                "type": "MEASURE", "outcome": "SUCCESS"},
            "target": {"@CLASS": OBS, "id": identity, "urn": row["urn"]},
            "sequence": 0, "properties": {"sequence": 0}})
    if state.scenario == "provenance-dependencies":
        for (source_id, source), (target_id, target) in zip(observations, observations[1:]):
            result.append({"@CLASS": "org.integratedmodelling.klab.api.data.impl.LinkImpl", "relationship": "CONTRIBUTED_TO",
                "source": {"@CLASS": OBS, "id": source_id, "urn": source["urn"]},
                "target": {"@CLASS": OBS, "id": target_id, "urn": target["urn"]},
                "sequence": 1, "properties": {"sequence": 1, "evidence_source": "SIMULATED_PROVISIONAL",
                    "meaning": "Declared fixture edge; no dependency computation or committed history", "native_revision": None}})
            result.append({"@CLASS": "org.integratedmodelling.klab.api.data.impl.LinkImpl", "relationship": "RESOLVED",
                "source": {"@CLASS": ACTUATOR, "id": target_id + 2000, "urn": f"{root}.plan.{target_id}"},
                "target": {"@CLASS": OBS, "id": target_id, "urn": target["urn"]},
                "sequence": 2, "properties": {"sequence": 2, "evidence_source": "SIMULATED_PROVISIONAL",
                    "meaning": "Planned resolution fixture; not execution or model selection evidence"}})
    return result


def matches(row, child):
    classes = {OBS: "OBSERVATION", ACT: "ACTIVITY", ACTUATOR: "ACTUATOR"}
    if child.get("relationship") not in (None, row["relationship"]):
        return False
    return all(side not in child or (child[side].get("id") == row[side]["id"]
        and child[side].get("type") == classes.get(row[side]["@CLASS"])) for side in ("source", "target"))
