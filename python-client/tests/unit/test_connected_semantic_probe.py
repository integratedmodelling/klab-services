"""Paced shared-deployment diagnostics must not create work or overstate science."""
from contextlib import contextmanager
from types import SimpleNamespace

import pytest

import run_connected_semantic_probe as probe


def observable(unit="m", urn="geography:Elevation", artifact="NUMBER"):
    raw = {"artifactType": artifact}
    if unit is not None:
        raw["unit"] = {"definition": unit}
    return SimpleNamespace(raw=raw, semantics=SimpleNamespace(urn=urn))


def test_measure_checks_identity_and_units_and_includes_pacing_in_rate(monkeypatch):
    sleeps = []
    calls = []
    clock = iter([0, .1, 1, 1, 1.2, 2, 2.3, 3])
    monkeypatch.setattr(probe.time, "perf_counter", lambda: next(clock))
    monkeypatch.setattr(probe.time, "sleep", sleeps.append)
    def resolve(definition):
        calls.append(definition)
        return observable()
    client = SimpleNamespace(reasoner=SimpleNamespace(resolve_observable=resolve))
    result = probe.measure(client, definition="geography:Elevation in m", expected_urn="geography:Elevation",
                           expected_unit="m", samples=2, warmup=1, interval=.5)
    assert len(calls) == 3 and sleeps == [.5, .5]
    assert result["verified_requests"] == 2
    assert result["measurement_wall_seconds"] == 2
    assert result["paced_diagnostic_requests_per_second"] == 1
    assert result["request_latency_seconds"]["samples"] == pytest.approx([.2, .3])


@pytest.mark.parametrize("unit,urn,artifact", [(None,"geography:Elevation","NUMBER"),
    ("mm","geography:Elevation","NUMBER"), ("m","wrong:Concept","NUMBER"),
    ("m","geography:Elevation","VOID")])
def test_missing_units_or_wrong_semantics_cannot_pass_unit_gate(unit, urn, artifact):
    with pytest.raises(probe.ProbeValidationError):
        probe.check_observable(observable(unit, urn, artifact), "geography:Elevation", "m")


def test_identity_only_diagnostic_keeps_unknown_units_unknown():
    assert probe.check_observable(observable(None), "geography:Elevation", None)["unit_definition"] is None


@pytest.mark.parametrize("samples,warmup,interval", [(0,0,.5), (21,0,.5), (1,3,.5),
    (1,-1,.5), (True,0,.5), (1,0,0), (1,0,.49), (1,0,float("inf")), (1,0,float("nan"))])
def test_budget_rejected_before_connection(samples, warmup, interval, monkeypatch):
    monkeypatch.setattr(probe, "connect", lambda *args, **kwargs: pytest.fail("No network for invalid budgets"))
    with pytest.raises(ValueError):
        probe.run(None, None, definition="x", expected_urn="x", samples=samples, warmup=warmup, interval=interval)


@pytest.mark.parametrize("generation", [None, 0, -1, True, "1"])
def test_operational_flag_is_not_accepted_generation(generation):
    entry = SimpleNamespace(status={"operational": True, "metadata": {"delegate": {
        "semantic.candidate": {"acceptedGeneration": generation}}}})
    with pytest.raises(probe.ProbeValidationError):
        probe.candidate_summary(entry)


@pytest.mark.parametrize("failure", ["none", "request", "unit", "close", "revision"])
def test_probe_never_initializes_peers_or_reports_science_and_redacts_errors(monkeypatch, failure):
    profile, provider = object(), object()
    entry = SimpleNamespace(status={"metadata": {"delegate": {"semantic.candidate": {
        "acceptedGeneration": 1, "beanStatus": "VALID", "logicalStatus": "VALID",
        "acceptedSources": {"ONTOLOGY:imod": "hash"}, "diagnostics": ["semantic warning"]}}}})
    def resolve(definition):
        if failure == "request":
            raise RuntimeError("SECRET upstream token must not be reported")
        return observable(None if failure == "unit" else "m")
    client = SimpleNamespace(reasoner=SimpleNamespace(resolve_observable=resolve))
    @contextmanager
    def connect(p, *, credentials, initialize):
        assert p is profile and credentials is provider and initialize is False
        def refresh():
            if failure == "revision":
                entry.status["metadata"]["delegate"]["semantic.candidate"]["acceptedGeneration"] = 2
        yield SimpleNamespace(catalog=SimpleNamespace(summary=lambda: ()), selected={"reasoner": entry}, client=client, refresh=refresh)
        if failure == "close":
            raise RuntimeError("SECRET cleanup detail")
    monkeypatch.setattr(probe, "connect", connect)
    result = probe.run(profile, provider, definition="geography:Elevation in m",
                       expected_urn="geography:Elevation", expected_unit="m", samples=1, warmup=0)
    assert result["scientific_acceptance"] is False
    assert result["passed"] == (failure == "none")
    assert "SECRET" not in str(result)
    if failure != "none":
        assert "measurement" not in result
    else:
        assert result["reasoner"]["diagnostic_count"] == 1
