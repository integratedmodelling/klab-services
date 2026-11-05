"""Current-tuple controlled TEST journey; no production Hub or new client facade."""
import json
import os
from pathlib import Path
import subprocess
import sys

from klab_client import load_handle
from .completed_recovery import allowed_request, expected_values
from .connection import access, connected_client
from .geojson_export import run as native_export, validate_reference
from .scientific_checks import require


def allowed_recovery(call, context_id):
    return allowed_request(call, context_id) or (call["method"] == "GET" and
        call["path"].startswith("/runtime/export/OBSERVATION/"))


def recover(state, provider, handle, destination, report_path):
    reference = load_handle(handle)
    require(reference.kind == "observation", "Onboarding recovery requires the original observation")
    calls = []
    with connected_client(state, provider) as client:
        client.initialize_user_scope(local_federation=True)
        def capture(request):
            call = {"method": request.method, "path": request.url.path}
            if call["path"].endswith("/api/v1/connect"):
                call["context_id"] = json.loads(request.content).get("configuration", {}).get("id")
            calls.append(call)
            require(allowed_recovery(call, reference.context_id), "Fresh connection attempted an unexpected operation")
        client.transport._http.event_hooks["request"].append(capture)
        observation = client.restore_handle(reference)
        require(not observation._context.owned, "Fresh connection acquired disposal ownership")
        exported = observation.export_to_file(destination, media_type="application/geo+json", max_bytes=1024 * 1024)
        report = {"passed": True, "pid": os.getpid(), "observation_id": observation.id,
            "bytes": exported.bytes_written, "provider": provider, "owned": False}
    require(all(allowed_recovery(call, reference.context_id) for call in calls), "Connection close was not local")
    report.update(calls=calls, local_close_only=True, scientific_submissions=0)
    Path(report_path).write_text(json.dumps(report, indent=2))
    return report


def journey(state):
    # The independent Java writer is deliberately single-use. Compute once, then
    # both credential routes must read/recover the same original scientific work.
    connections, selected, workers, token_reports, redactors = [], [], [], [], []
    scalar = {}
    scalar_handle = state / "onboarding-scalar.json"
    worker = Path(__file__).parents[1] / "recover_onboarding.py"
    def capture_connection(connection):
        require(set(connection.selected) == {"runtime", "reasoner", "resources", "resolver"},
            "Connection did not select all four required services")
        require(all(entry.ready for entry in connection.selected.values()), "Selected services are not ready")
        selected.append({kind: {"url": entry.reference.url, "service_id": entry.service_id}
            for kind, entry in connection.selected.items()})
        connections.append(connection)
        redactors.append(connection.client.transport.redact)
    def recovery(handle, destination, provider="certificate"):
        report = destination.with_suffix(".recovery.json")
        workers.append((provider, report))
        return [sys.executable, str(worker), "--state-dir", str(state), "--provider", provider,
                "--report", str(report), str(handle), str(destination)]
    def scientific(client, context, region):
        job = context.within(region).observe("consolidation:Elevation of consolidation:Region in m")
        observation = job.result(120)
        require(observation.units == "m", "Served observation unit differs from the requested native unit")
        require(observation.fetch_data(range(20), curve="D2_YX").values == expected_values(),
            "Connection computation differs from independent nonuniform cells")
        observation.to_handle().save(scalar_handle)
        scalar.update(job_id=job.id, observation_id=observation.id, verified_cells=20, units=observation.units)
        return dict(scalar)
    def token_phase(handle, directory, region):
        with connected_client(state, "token", on_connect=capture_connection) as client:
            original = client.restore_handle(load_handle(scalar_handle))
            require(original.id == scalar["observation_id"] and original.units == "m" and
                original.fetch_data(range(20), curve="D2_YX").values == expected_values(),
                "Token connection did not read the same original scientific cells")
        destination = directory / "token-region.geojson"
        command = recovery(handle, destination, "token")
        env = {key: value for key, value in os.environ.items() if not key.startswith("KLAB_")}
        process = subprocess.run(command, env=env, capture_output=True, timeout=120,
            **({"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}))
        (directory / "token-worker.stdout").write_bytes(process.stdout)
        (directory / "token-worker.stderr").write_bytes(process.stderr)
        require(process.returncode == 0, "Token fresh-process recovery failed; inspect private evidence")
        feature = validate_reference(json.loads(destination.read_bytes()), region)
        token_reports.append({"provider": "token", "observation_id": region.id, "geometry": feature["geometry"],
            "scientific_check": dict(scalar), "original_cells_independently_read": True,
            "new_scientific_submissions": 0})
    report = native_export(state, client_factory=lambda: connected_client(state, "certificate", on_connect=capture_connection),
        recovery_command=recovery, scientific_check=scientific, before_recovery=lambda: connections[-1].close(),
        region_observable="consolidation:Region", after_recovery=token_phase)
    require(len(workers) == 2 and len(token_reports) == 1, "Expected two fresh-process authenticated recoveries")
    reports = [report | {"provider": "certificate"}, token_reports[0]]
    for result, (provider, path) in zip(reports, workers):
        recovered = json.loads(path.read_text())
        require(recovered["passed"] and recovered["pid"] != os.getpid() and recovered["provider"] == provider and
            recovered["observation_id"] == report["observation_id"] and recovered["local_close_only"] and
            recovered["scientific_submissions"] == 0 and recovered["owned"] is False,
            "Fresh authenticated process did not preserve original observation/lifecycle")
        result.update(recovery=recovered, creator_transport_closed=True,
            authentication_evidence="controlled TEST only, not production Hub")
    require(all(snapshot == selected[0] for snapshot in selected), "Cleanup connection changed selected service identity")
    for result in reports:
        result["selected"] = selected[0]
    texts = [json.dumps(reports), scalar_handle.read_text(),
             (state / "connection-profile.json").read_text(),
             *(path.read_text() for path in state.glob("geojson-export-*/native-observation.json"))]
    require(all(redact(text) == text for redact in redactors for text in texts),
        "Connection report/profile/handle contains acquired credential material")
    return reports


def run(state):
    state = Path(state)
    reports = journey(state)
    security = access(state)
    config = json.loads((state / "scientist.json").read_text())
    for path in (state / "connection-profile.json", state / "onboarding-scalar.json",
                 *state.glob("geojson-export-*/native-observation.json")):
        text = path.read_text()
        require(all(token not in text for token in (config["token"], config["other_token"])),
            "Saved profile/handle contains credential material")
    report = {"passed": True, "journeys": reports, "access": security,
        "production_hub": "NOT_RUN", "historical_replication": "NOT_CLAIMED", "secret_free_profiles_handles": True}
    encoded = json.dumps(report, indent=2)
    require(all(token not in encoded for token in (config["token"], config["other_token"])), "Report contains credential material")
    (state / "onboarding-journey.json").write_text(encoded)
    return report
