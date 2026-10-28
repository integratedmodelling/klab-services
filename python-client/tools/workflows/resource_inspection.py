"""One explicitly authorized, owned 20-cell F2 pilot; no provisioning or model forcing.

This checks model advertisement/accepted resolution/result association, not a full
scientific accuracy benchmark or the later F3 diagram-rendering capability.
"""
import hashlib
import json
import math
from pathlib import Path
import platform
from collections.abc import Mapping

from klab_client import (ContextOptions, DeploymentProfile, HubCertificateProvider, ObservationOptions,
                         ObservationRequest, RectangularGeometry, ResourceInspector, connect, fetch_provenance)
from klab_client.client import scope_token
from klab_client.errors import ProtocolError
from klab_client.experiment import verify_grid
from .scientific_checks import require


def component_inventory(client):
    """Snapshot advertised component/functions only; never import or follow a descriptor URL."""
    result = {}
    for service in ("runtime", "resolver", "resources"):
        caps = client.transport.request(service, "GET", "/public/capabilities",
            service_id=client.runtime_service_id, max_response_bytes=1024 * 1024)
        require(isinstance(caps, dict) and isinstance(caps.get("components"), list), "Component advertisement unavailable")
        require(len(caps["components"]) <= 128, "Component inventory exceeds this pilot budget")
        components, names = [], set()
        for component in caps["components"]:
            require(isinstance(component, dict), "Malformed component advertisement")
            components.append({"id": component.get("id"), "version": component.get("version"), "fileHash": component.get("fileHash")})
            for library in component.get("libraries", []):
                for entry in library.get("services", []):
                    require(isinstance(entry, dict), "Malformed function advertisement")
                    info = entry.get("first", entry.get("serviceInfo", entry))
                    require(isinstance(info, dict), "Malformed function descriptor")
                    name = info.get("name")
                    if isinstance(name, str):
                        names.add(name)
                    require(len(names) <= 2048, "Function inventory exceeds this pilot budget")
        result[service] = {"service_id": caps.get("serviceId"), "components": components, "service_names": sorted(names)}
    return json.loads(client.transport.redact(json.dumps(result, allow_nan=False)))


def model_references(chart, *, max_nodes=256):
    """Extract only documented model metadata from an accepted recorded resolution.

    No label matching, arbitrary metadata scanning, graph execution or extra resolve.
    """
    if isinstance(chart, str):
        if len(chart.encode("utf-8")) > 1024 * 1024:
            raise ProtocolError("Recorded chart exceeds the response budget")
        try:
            chart = json.loads(chart)
        except ValueError:
            raise ProtocolError("Recorded chart is not JSON") from None
    if not isinstance(chart, dict) or not isinstance(chart.get("metadata"), dict):
        raise ProtocolError("Recorded resolution chart lacks its schema metadata")
    require(chart["metadata"].get("schema") == "klab.resolution-graph.v1"
            and chart["metadata"].get("scope") == "accepted-resolution", "Unknown or unaccepted resolution chart")
    pending, references, count = [(chart.get("root"), 0)], set(), 0
    while pending:
        node, depth = pending.pop()
        count += 1
        if not isinstance(node, dict) or depth > 16 or count > max_nodes:
            raise ProtocolError("Recorded resolution nodes exceed shape/depth/item bounds")
        metadata = node.get("metadata", {})
        children = node.get("children", [])
        if not isinstance(metadata, dict) or not isinstance(children, list) or len(children) > max_nodes:
            raise ProtocolError("Malformed recorded resolution node")
        if metadata.get("kind") == "model":
            urn = metadata.get("urn")
            if not isinstance(urn, str) or not urn.strip():
                raise ProtocolError("Recorded model identity is missing")
            references.add(urn)
        pending.extend((child, depth + 1) for child in children)
    return sorted(references)


def recorded_models(observation):
    """Read only this observation's bounded current provenance/activity neighborhood."""
    view = fetch_provenance(observation)
    numeric = {link.source.id for link in view.links if link.target.id == observation.id
        and link.source.kind == "activity" and link.source.outcome == "SUCCESS"
        and ((link.source.activity_type == "MEASURE" and link.relationship == "MEASURED")
             or (link.source.activity_type == "QUANTIFICATION" and link.relationship == "QUANTIFIED"))}
    require(numeric, "No successful numerical execution effect linked to this observation")
    activities = {node.id: node for link in view.links for node in (link.source, link.target) if node.kind == "activity"}
    require(len(activities) <= 16, "Recorded activity count exceeds this pilot budget")
    client, context = observation._context.client, observation._context
    models, resolutions = set(), []
    for identifier, node in activities.items():
        rows = client.transport.request("runtime", "POST", "/api/v1/query",
            json={"type": "QUERY", "resultType": "ACTIVITY", "id": identifier, "limit": 2},
            scope=scope_token(context), service_id=view.observation.runtime_id, max_response_bytes=1024 * 1024)
        require(isinstance(rows, list) and len(rows) == 1 and rows[0].get("id") == identifier
                and rows[0].get("urn") == node.urn, "Scoped activity lookup changed identity")
        activity = rows[0]
        if activity.get("observationUrn") is not None:
            require(activity["observationUrn"] == observation.urn, "Recorded activity belongs to another observation")
        metadata = activity.get("metadata", {})
        require(isinstance(metadata, dict), "Activity metadata must be a map")
        metadata = metadata.get("delegate", metadata)
        require(isinstance(metadata, dict), "Activity metadata delegate must be a map")
        chart = metadata.get("im:resolution-graph")
        if chart is not None and activity.get("type") == "RESOLUTION" and activity.get("outcome") == "SUCCESS":
            models.update(model_references(chart))
            resolutions.append(identifier)
    require(resolutions, "Recorded successful accepted resolution metadata unavailable")
    return {"accepted_model_urns": sorted(models), "resolution_activity_ids": sorted(resolutions),
            "numerical_activity_ids": sorted(numeric), "partial": view.partial,
            "meaning": "accepted resolution linked to this numerical result; not exhaustive lineage or root-commit proof"}


def package_identity():
    import klab_client
    root = Path(klab_client.__file__).parent
    files = {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
             for p in sorted(root.rglob("*.py"))}
    return {"python": platform.python_version(),
            "installed_source_sha256": hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()}


def run(profile_path, certificate_path, model_urn, *, allow_owned_work=False):
    require(allow_owned_work is True, "Explicit owned-pilot authorization is required")
    profile = DeploymentProfile.load(profile_path)
    report = {"version": 1, "passed": False, "stage": "connect", "model_urn": model_urn,
        "package": package_identity(), "scientific_accuracy": "not assessed: F2 model association pilot only",
        "deployed_binary_provenance": "unverified: service identity is not a binary/source commit",
        "cleanup": {}, "jobs": []}
    session = context = job = None
    try:
        with connect(profile, credentials=HubCertificateProvider(Path(certificate_path))) as connection:
            client = connection.client
            report["service_ids"] = {kind: entry.service_id for kind, entry in connection.selected.items()}
            try:
                report["stage"] = "inspect_model"
                model = ResourceInspector(client).retrieve(model_urn)
                require(any(v.urn == "geography:Elevation in m" and v.unit == "m" for v in model.advertised_observables or ()),
                        "Pilot model must explicitly advertise the requested elevation/unit")
                report["inspection"] = {"urn": model.urn, "namespace": model.namespace, "project": model.project_name,
                    "origin": {"service_id": model.origin.service_id, "service_url": model.origin.service_url},
                    "dependencies": None if model.listed_dependencies is None else [v.urn for v in model.listed_dependencies],
                    "resources": None if model.resource_urns is None else list(model.resource_urns), "selection": model.selection,
                    "dependency_completeness": model.dependency_completeness}
                report["stage"] = "runtime_prerequisites"
                report["advertised_components"] = component_inventory(client)
                required = []
                for item in model.raw.get("contextualization", ()):
                    require(isinstance(item, Mapping), "Malformed model contextualization advertisement")
                    call = item.get("serviceCall")
                    if call is not None:
                        require(isinstance(call, Mapping) and isinstance(call.get("urn"), str), "Missing contextualizer identity")
                        required.append(call["urn"])
                require(required and len(required) <= 16, "This pilot requires bounded explicit contextualizer identities")
                report["required_service_calls"] = sorted(set(required))
                report["preflight_policy"] = "require Runtime advertisement; dynamic remote component loading is not assumed"
                require(set(required) <= set(report["advertised_components"]["runtime"]["service_names"]),
                        "Required computation services are not advertised by the selected Runtime")
                report["stage"] = "create_owned_context"
                session = client.create_session(name="PR99: single 20-cell inspector pilot")
                context = session.create_context(options=ContextOptions(name="PR99 owned pilot", persistence="EXPLICIT_ACTION"))
                report["session_id"], report["context_id"] = session.id, context.id
                report["stage"] = "observe_region"
                job = context.observe(ObservationRequest("earth:Region",
                    geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))
                report["jobs"].append(job.to_handle().to_dict())
                region = job.result(timeout=90)
                verify_grid(region)
                report["stage"] = "observe_elevation"
                job = context.within(region).observe(ObservationRequest("geography:Elevation in m",
                    options=ObservationOptions(namespace=model.namespace)))
                report["jobs"].append(job.to_handle().to_dict())
                observation = job.result(timeout=90)
                report["observation"] = observation.to_handle().to_dict()
                verify_grid(observation)
                require(observation.units == "m", "Computed observation did not preserve advertised units")
                report["stage"] = "read_result"
                data = observation.fetch_data(range(20), curve="D2_YX")
                require(len(data.values) == 20 and any(v is not None for v in data.values)
                        and all(v is None or math.isfinite(v) for v in data.values), "Pilot has no finite actual numerical data")
                report["data"] = {"cells_read": 20, "valid_cells": sum(v is not None for v in data.values), "units": observation.units,
                    "verified_grid": {"shape": [5, 4], "crs": "EPSG:3857", "bbox": [200000, 200500, 6000000, 6000400]}}
                report["stage"] = "recorded_model_association"
                report["recorded"] = recorded_models(observation)
                require(model.urn in report["recorded"]["accepted_model_urns"],
                        "Inspected candidate was not identified in this result's accepted recorded resolution")
                report["passed"], report["stage"] = True, "verified"
            except Exception as error:
                if job is not None:
                    # Capture only enum/availability, not arbitrary server stack text or credentials.
                    try:
                        status = job.status()
                        report["last_known_job_status"] = {"status": status.status, "stack_trace_available": bool(status.stack_trace)}
                    except Exception as diagnostic:
                        report["job_diagnostic_error_type"] = type(diagnostic).__name__
                    report["failure_kind"] = ("empty_scientific_result" if str(error).endswith(": Server returned an empty scientific result")
                                              else "unclassified; no arbitrary error text published")
                raise
            finally:
                for name, owned in (("context", context), ("session", session)):
                    if owned is not None:
                        try:
                            report["cleanup"][name] = "confirmed" if owned.release() is True else "unconfirmed"
                        except Exception as error:
                            report["cleanup"][name] = type(error).__name__
                        if report["cleanup"][name] != "confirmed":
                            report["passed"] = False
                            if report["stage"] == "verified":
                                report["stage"] = "cleanup_failed"
    except Exception as error:
        report["passed"], report["error_type"] = False, type(error).__name__
    return report
