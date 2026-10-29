"""F2 owned-pilot guards and recorded-identity contract tests, without real services."""
import json
from types import SimpleNamespace as NS

import pytest

from klab_client.errors import ProtocolError, JobFailedError
from workflows import resource_inspection as flow


def chart():
    return {"metadata": {"schema": "klab.resolution-graph.v1", "scope": "accepted-resolution"},
            "root": {"id": "root", "children": [{"id": "node-1", "metadata": {"kind": "model", "urn": "fixture.model"}}]}}


def test_model_references_use_canonical_metadata_not_labels():
    assert flow.model_references(chart()) == ["fixture.model"]
    assert flow.model_references(json.dumps(chart())) == ["fixture.model"]
    value = chart()
    value["root"]["children"] = [{"labels": [{"text": "model: spoof"}], "metadata": {"kind": "strategy", "urn": "spoof"}}]
    assert flow.model_references(value) == []


def test_component_inventory_reports_origin_specific_advertisement_without_imports():
    calls = []
    def request(service, method, route, **kwargs):
        calls.append((service, method, route, kwargs))
        return {"serviceId": service, "components": [{"id": "component", "version": {"major": 1}, "fileHash": None,
            "libraries": [{"services": [{"first": {"name": "fixture.compute"}},
                {"serviceInfo": {"name": "fixture.other"}}, {"name": None}]}]}]}
    client = NS(runtime_service_id="home", transport=NS(request=request, redact=lambda value: value))
    snapshot = flow.component_inventory(client)
    assert set(snapshot) == {"runtime", "resolver", "resources"}
    assert snapshot["runtime"]["service_names"] == ["fixture.compute", "fixture.other"]
    assert all(method == "GET" and route == "/public/capabilities" and options["service_id"] == "home"
               for _, method, route, options in calls)


@pytest.mark.parametrize("fault", ["shape", "count", "component", "entry", "descriptor", "functions"])
def test_component_inventory_bounds_and_malformed_metadata(fault):
    caps = {"components": [{"id": "component", "libraries": [{"services": [{"name": "function"}]}]}]}
    if fault == "shape":
        caps = {}
    elif fault == "count":
        caps["components"] *= 129
    elif fault == "component":
        caps["components"] = [None]
    elif fault == "entry":
        caps["components"][0]["libraries"][0]["services"] = [None]
    elif fault == "descriptor":
        caps["components"][0]["libraries"][0]["services"] = [{"first": None}]
    else:
        caps["components"][0]["libraries"][0]["services"] = [{"name": str(i)} for i in range(2049)]
    client = NS(runtime_service_id="home", transport=NS(request=lambda *a, **k: caps, redact=lambda value: value))
    with pytest.raises(AssertionError):
        flow.component_inventory(client)


@pytest.mark.parametrize("fault", ["oversize", "json", "shape", "schema", "root", "children", "metadata", "urn", "budget", "depth"])
def test_malformed_or_unaccepted_recorded_chart_fails(fault):
    value = chart()
    if fault == "oversize":
        value = " " * (1024 * 1024 + 1)
    elif fault == "json":
        value = "not JSON"
    elif fault == "shape":
        value = {}
    elif fault == "schema":
        value["metadata"]["scope"] = "unaccepted"
    elif fault == "root":
        value["root"] = None
    elif fault == "children":
        value["root"]["children"] = None
    elif fault == "metadata":
        value["root"]["metadata"] = []
    elif fault == "urn":
        value["root"]["children"][0]["metadata"]["urn"] = ""
    elif fault == "depth":
        node = value["root"]
        for _ in range(18):
            child = {}
            node["children"] = [child]
            node = child
    with pytest.raises((ProtocolError, AssertionError)):
        flow.model_references(value, max_nodes=1 if fault == "budget" else 256)


def recorded_stack(monkeypatch):
    numeric = NS(id=7, urn="activity:7", kind="activity", outcome="SUCCESS", activity_type="MEASURE")
    resolution = NS(id=8, urn="activity:8", kind="activity", outcome="SUCCESS", activity_type="RESOLUTION")
    observed = NS(id=42, urn="s.c.42", kind="observation")
    links = [NS(source=numeric, target=observed, relationship="MEASURED"),
             NS(source=resolution, target=observed, relationship="RESOLVED")]
    view = NS(links=links, observation=NS(runtime_id="runtime"), partial=True)
    monkeypatch.setattr(flow, "fetch_provenance", lambda observation: view)
    calls = []
    def request(service, method, route, **kwargs):
        calls.append(kwargs)
        identifier = kwargs["json"]["id"]
        return [{"id": identifier, "urn": "activity:" + str(identifier), "observationUrn": "s.c.42",
            "type": "RESOLUTION" if identifier == 8 else "MEASURE", "outcome": "SUCCESS",
            "metadata": {"delegate": {"im:resolution-graph": chart()}}}]
    client = NS(transport=NS(request=request))
    monkeypatch.setattr(flow, "scope_token", lambda context: "s.c")
    observation = NS(id=42, urn="s.c.42", _context=NS(client=client))
    return observation, view, calls


def test_recorded_models_stay_scoped_and_require_real_numeric_effect(monkeypatch):
    observation, view, calls = recorded_stack(monkeypatch)
    result = flow.recorded_models(observation)
    assert result["accepted_model_urns"] == ["fixture.model"]
    assert result["numerical_activity_ids"] == [7] and result["resolution_activity_ids"] == [8]
    assert all(c["scope"] == "s.c" and c["service_id"] == "runtime" for c in calls)
    view.links[0].source.outcome = "FAILURE"
    with pytest.raises(AssertionError, match="numerical"):
        flow.recorded_models(observation)


@pytest.mark.parametrize("fault", ["identity", "observation", "metadata", "delegate", "no_chart", "activity_budget"])
def test_recorded_models_reject_cross_association_and_missing_evidence(monkeypatch, fault):
    observation, view, _ = recorded_stack(monkeypatch)
    if fault == "activity_budget":
        for i in range(20):
            view.links.append(NS(source=NS(id=100+i, urn=str(i), kind="activity", outcome=None, activity_type="RESOLUTION"), target=view.links[0].target))
    else:
        original = observation._context.client.transport.request
        def request(*args, **kwargs):
            result = original(*args, **kwargs)
            row = result[0]
            if fault == "identity":
                row["urn"] = "different"
            elif fault == "observation":
                row["observationUrn"] = "foreign"
            elif fault == "metadata":
                row["metadata"] = None
            elif fault == "delegate":
                row["metadata"] = {"delegate": None}
            else:
                row["metadata"] = {}
            return result
        observation._context.client.transport.request = request
    with pytest.raises(AssertionError):
        flow.recorded_models(observation)


class Pilot:
    def __init__(self, monkeypatch, fault=None):
        self.fault, self.submissions, self.releases = fault, 0, []
        self.requests = []
        self.observation = NS(units="mm" if fault == "units" else "m", to_handle=self.handle,
            geometry=NS(raw={"dimensions": [{"type": "SPACE", "shape": [5, 4], "regular": True,
                "parameters": {"delegate": {"proj": "EPSG:3857", "bbox": [200000, 200500, 6000000, 6000400]}}}]}),
            fetch_data=lambda *a, **k: NS(values=([None] * 20 if fault == "nodata" else [float("nan")] * 20 if fault == "nonfinite" else [1] * 20)))
        model = NS(urn="fixture.model", namespace="fixture", project_name="fixture.project",
            origin=NS(service_id="resources", service_url="https://resources.invalid"),
            advertised_observables=() if fault == "advertisement" else (NS(urn="geography:Elevation in m", unit="m"),),
            listed_dependencies=(), resource_urns=(), selection="advertised candidate", dependency_completeness="unknown: listed only",
            raw={"contextualization": [{"serviceCall": {"urn": "fixture.compute"}}]})
        monkeypatch.setattr(flow.DeploymentProfile, "load", lambda path: "profile")
        monkeypatch.setattr(flow, "HubCertificateProvider", lambda path: "private-provider")
        monkeypatch.setattr(flow, "ResourceInspector", lambda client: NS(retrieve=lambda urn: model))
        monkeypatch.setattr(flow, "connect", lambda *a, **k: self)
        monkeypatch.setattr(flow, "component_inventory", lambda client: {"runtime": {"service_names": ["fixture.compute"]}})
        monkeypatch.setattr(flow, "recorded_models", lambda observation: {"accepted_model_urns": [] if fault == "model" else ["fixture.model"]})
        self.client = NS(create_session=self.create_session)
        self.selected = {kind: NS(service_id=kind) for kind in ("runtime", "resolver", "reasoner", "resources")}

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    @staticmethod
    def handle():
        return NS(to_dict=lambda: {"id": "opaque-test-reference"})

    def create_session(self, **kwargs):
        if self.fault == "setup":
            raise RuntimeError("private diagnostic must not be published")
        return NS(id="s", create_context=lambda **k: self.context(), release=lambda: self.release("session"))

    def context(self):
        return NS(id="s.c", observe=self.observe, within=lambda region: self.context(), release=lambda: self.release("context"))

    def observe(self, request):
        self.requests.append(request)
        self.submissions += 1
        if self.fault == "submit" and self.submissions == 2:
            raise RuntimeError("private diagnostic")
        def result(**kwargs):
            if self.fault == "wait":
                raise TimeoutError("private diagnostic")
            if self.fault == "empty":
                raise JobFailedError(2, "Server returned an empty scientific result")
            return self.observation
        def status():
            if self.fault == "wait":
                raise RuntimeError("private diagnostic unavailable")
            return NS(status="FINISHED", stack_trace=None)
        return NS(to_handle=self.handle, result=result, status=status)

    def release(self, kind):
        self.releases.append(kind)
        if self.fault == "cleanup-error":
            raise RuntimeError("private cleanup diagnostic")
        return self.fault != "cleanup-unconfirmed"


def test_pilot_runs_once_and_releases_only_its_owned_objects(monkeypatch):
    pilot = Pilot(monkeypatch)
    result = flow.run("profile", "private-path", "fixture.model", allow_owned_work=True)
    assert result["passed"] and result["stage"] == "verified" and pilot.submissions == 2
    assert pilot.releases == ["context", "session"] and result["cleanup"] == {"context": "confirmed", "session": "confirmed"}
    assert result["inspection"]["resources"] == [] and result["inspection"]["selection"] == "advertised candidate"
    assert result["deployed_binary_provenance"].startswith("unverified")
    assert result["region_definition"] == "earth:Terrestrial earth:Region"
    assert [request.observable for request in pilot.requests] == [
        "earth:Terrestrial earth:Region", "geography:Elevation in m"]


@pytest.mark.parametrize("fault", ["advertisement", "setup", "submit", "wait", "empty", "units", "nodata", "nonfinite", "model", "cleanup-error", "cleanup-unconfirmed"])
def test_pilot_failure_never_retries_or_exposes_private_errors(monkeypatch, fault):
    pilot = Pilot(monkeypatch, fault)
    result = flow.run("profile", "private-path", "fixture.model", allow_owned_work=True)
    assert not result["passed"] and pilot.submissions <= 2
    if fault not in ("advertisement", "setup"):
        assert pilot.releases == ["context", "session"]
    assert "private" not in json.dumps(result).lower()


def test_missing_runtime_generator_blocks_before_any_session_or_job(monkeypatch):
    pilot = Pilot(monkeypatch)
    monkeypatch.setattr(flow, "component_inventory", lambda client: {"runtime": {"service_names": []}})
    result = flow.run("profile", "private-path", "fixture.model", allow_owned_work=True)
    assert not result["passed"] and result["stage"] == "runtime_prerequisites"
    assert pilot.submissions == 0 and pilot.releases == []


@pytest.mark.parametrize("contextualization", [[None], [{"serviceCall": {}}], [], [{}]])
def test_missing_contextualizer_contract_blocks_before_submission(monkeypatch, contextualization):
    pilot = Pilot(monkeypatch)
    original = flow.ResourceInspector
    def inspector(client):
        model = original(client).retrieve("fixture.model")
        model.raw["contextualization"] = contextualization
        return NS(retrieve=lambda urn: model)
    monkeypatch.setattr(flow, "ResourceInspector", inspector)
    assert not flow.run("profile", "private-path", "fixture.model", allow_owned_work=True)["passed"]
    assert pilot.submissions == 0


def test_pilot_requires_explicit_authorization_before_connect():
    with pytest.raises(AssertionError, match="authorization"):
        flow.run("missing", "missing", "fixture.model")


def test_recorded_activity_without_optional_observation_urn_uses_scoped_link(monkeypatch):
    observation, _, _ = recorded_stack(monkeypatch)
    original = observation._context.client.transport.request
    def request(*args, **kwargs):
        rows = original(*args, **kwargs)
        rows[0].pop("observationUrn")
        return rows
    observation._context.client.transport.request = request
    assert flow.recorded_models(observation)["accepted_model_urns"] == ["fixture.model"]


def test_cli_requires_auth_and_fresh_report(tmp_path, monkeypatch):
    import sys
    import run_resource_inspector_acceptance as runner
    profile, certificate, report = tmp_path / "profile", tmp_path / "private", tmp_path / "report"
    profile.write_text("{}")
    certificate.write_text("private-canary")
    arguments = ["runner", "--profile", str(profile), "--certificate", str(certificate), "--model", "fixture.model", "--report", str(report)]
    monkeypatch.setattr(sys, "argv", arguments)
    with pytest.raises(SystemExit) as error:
        runner.main()
    assert error.value.code == 2
    monkeypatch.setattr(runner, "run", lambda *a, **k: {"passed": True, "stage": "verified"})
    monkeypatch.setattr(sys, "argv", arguments + ["--allow-owned-work"])
    assert runner.main() == 0 and json.loads(report.read_text())["passed"]
    with pytest.raises(SystemExit):
        runner.main()
    import runpy
    monkeypatch.setattr(flow, "run", lambda *a, **k: {"passed": False, "stage": "controlled-offline-failure"})
    arguments[-1] = str(tmp_path / "other-report")
    monkeypatch.setattr(sys, "argv", arguments + ["--allow-owned-work"])
    with pytest.raises(SystemExit) as status:
        runpy.run_path(str(runner.__file__), run_name="__main__")
    assert status.value.code == 1
