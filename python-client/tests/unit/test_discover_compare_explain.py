"""Independent oracle and orchestration checks; not real-Java scientific acceptance."""
from dataclasses import replace
from decimal import Decimal
from types import SimpleNamespace as NS

import numpy as np
import pytest
import rasterio
import xarray as xr
from rasterio.io import MemoryFile
from rasterio.transform import Affine

from klab_client import Experiment, ExperimentBatch, ExperimentOutcome, Handle, ObservationRequest, OwnedContextPolicy
from klab_client.errors import ConfigurationError
from workflows import discover_compare_explain as flow


def oracle(**changes):
    fields = dict(source_sha256="a" * 64, units="m", crs="EPSG:3857", width=2, height=2,
                  transform=(100, 0, 200000, 0, -100, 6000400),
                  values=("0", None, "2", "3"), dtype="float64")
    return flow.RasterOracle(**{**fields, **changes})


def matrix():
    return tuple(flow.MatrixCase(Experiment(f"{unit}-{resolution}",
        ObservationRequest("geography:Elevation in " + unit), OwnedContextPolicy(), variation=unit),
        resolution, oracle(units=unit, **({} if resolution == "fine" else
            dict(width=1, height=1, transform=(200, 0, 200000, 0, -200, 6000400), values=("2",)))))
        for unit in ("m", "mm") for resolution in ("fine", "coarse"))


@pytest.mark.parametrize("changes", [dict(source_sha256="x"), dict(units=""), dict(width=True),
    dict(width=1000001), dict(transform=(1,)), dict(transform=(100, 1, 2, 0, -100, 3)),
    dict(transform=(100, 0, 2, 0, 100, 3)), dict(transform=(float("nan"), 0, 2, 0, -100, 3)),
    dict(values=("1",)), dict(values=(None,) * 4), dict(values=(True, None, "2", "3")),
    dict(values=("bad", None, "2", "3")), dict(values=("NaN", None, "2", "3")),
    dict(absolute_tolerance="-1"), dict(relative_tolerance="-1")])
def test_bad_oracles_fail_before_remote_work(changes):
    with pytest.raises(ConfigurationError):
        oracle(**changes)


def test_reference_sequences_are_detached_and_exact():
    values = ["0", None, 2, Decimal(3)]
    value = oracle(values=values)
    values[0] = "999"
    assert value.values == (Decimal(0), None, Decimal(2), Decimal(3))


def dataset():
    memory = MemoryFile()
    with memory.open(driver="GTiff", width=2, height=2, count=1, dtype="float64",
                     crs="EPSG:3857", transform=Affine(*oracle().transform), nodata=float("nan")) as writer:
        writer.write(np.array([[0, np.nan], [2, 3]], dtype="float64"), 1)
        writer.set_band_unit(1, "m")
    return memory


def test_real_raster_all_cells_orientation_mask_and_zero():
    with dataset() as memory, memory.open() as reader:
        result = flow.validate_raster(reader, oracle())
    assert result == dict(verified_cells=4, valid_cells=3, missing_cells=1,
        valid_zero_cells=1, valid_sum="5.0", maximum_error="0", reference_sha256="a" * 64)


@pytest.mark.parametrize("changes", [dict(width=1, height=4), dict(crs="EPSG:4326"),
    dict(transform=(100, 0, 200100, 0, -100, 6000400)), dict(units="mm"), dict(dtype="float32"),
    dict(values=(None, None, "2", "3")), dict(values=("1", None, "2", "3"))])
def test_independent_raster_mismatches_are_not_accepted(changes):
    with dataset() as memory, memory.open() as reader, pytest.raises(AssertionError):
        flow.validate_raster(reader, oracle(**changes))


def test_tolerance_is_explicit_and_nonfinite_valid_cell_rejected():
    with dataset() as memory, memory.open() as reader:
        assert flow.validate_raster(reader, oracle(values=("0.01", None, "2", "3"),
                                                  absolute_tolerance="0.02"))["maximum_error"] == "0.01"
    broken = NS(width=2, height=2, count=1, crs="EPSG:3857", transform=oracle().transform,
        units=("m",), dtypes=("float64",), read=lambda _: np.array([[np.inf, np.nan], [2, 3]]),
        read_masks=lambda _: np.array([[255, 0], [255, 255]]))
    with pytest.raises(AssertionError, match="nonfinite"):
        flow.validate_raster(broken, oracle())


def test_four_matrix_cells_require_real_definition_and_resolution_differences():
    assert flow.validate_matrix(matrix()) == matrix()
    cases = matrix()
    bad_cases = [(), (None,) * 4,
        tuple(replace(c, experiment=replace(c.experiment, variation="same")) for c in cases),
        tuple(replace(c, resolution="same") for c in cases),
        tuple(replace(c, experiment=replace(c.experiment, name="same")) for c in cases),
        tuple(replace(c, experiment=replace(c.experiment, request=ObservationRequest("same"))) for c in cases),
        tuple(replace(c, oracle=oracle(units=c.oracle.units)) for c in cases),
        (replace(cases[0], experiment=replace(cases[0].experiment, request=ObservationRequest("changed"))), *cases[1:]),
        (replace(cases[0], oracle=oracle(transform=(100, 0, 0, 0, -100, 400))), *cases[1:])]
    for value in bad_cases:
        with pytest.raises(ConfigurationError):
            flow.validate_matrix(value)
    from klab_client.api.knowledge import ObservableImpl
    from klab_client.requests import ObservationRequest as Request
    # The public typed request constructor rejects unresolved DTOs; use an actual
    # supported observable from the existing wire decoder for this workflow guard.
    from klab_client.dto import observable_from_wire
    observable = observable_from_wire({"@CLASS": "org.integratedmodelling.common.knowledge.ObservableImpl",
        "urn": "geography:Elevation", "contextualization": "MEASURE",
        "semantics": {"urn": "geography:Elevation", "type": ["QUALITY", "QUANTIFIABLE"]}})
    assert isinstance(observable, ObservableImpl)
    with pytest.raises(ConfigurationError, match="text"):
        flow.validate_matrix((replace(cases[0], experiment=replace(cases[0].experiment,
            request=Request(observable))), *cases[1:]))


def evidence():
    return NS(provenance=NS(observation=NS(observation_id=42), links=[NS(target=NS(id=42),
        source=NS(id=9, kind="activity", outcome="SUCCESS", activity_type="MEASURE"), relationship="MEASURED")]),
        activities=[NS(id=9, charts=[NS(nodes=[NS(id="fixture-computation")])])],
        partial=True, durable_root_commit="unknown")


def test_recorded_evidence_requires_success_effect_and_expected_nodes():
    assert flow.validate_recorded_evidence(NS(id=42), evidence(), ("fixture-computation",))["partial"]
    with pytest.raises(AssertionError, match="another observation"):
        flow.validate_recorded_evidence(NS(id=43), evidence(), ("fixture-computation",))
    for nodes in ((), ("not-recorded",)):
        with pytest.raises(AssertionError, match="lacks"):
            flow.validate_recorded_evidence(NS(id=42), evidence(), nodes)
    failed = evidence()
    failed.provenance.links[0].source.outcome = "FAILED"
    with pytest.raises(AssertionError):
        flow.validate_recorded_evidence(NS(id=42), failed, ("fixture-computation",))


def test_workflow_preflight_guards_do_not_call_factory(tmp_path):
    def forbidden():
        pytest.fail("Remote factory must not be called")
    for nodes in ((), (None,)):
        with pytest.raises(ConfigurationError):
            flow.run_matrix(forbidden, matrix(), tmp_path / "unused", model_urn="model", expected_node_ids=nodes)
    released = tuple(replace(c, experiment=replace(c.experiment,
        context_policy=OwnedContextPolicy(release="terminal"))) for c in matrix())
    with pytest.raises(ConfigurationError):
        flow.run_matrix(forbidden, released, tmp_path / "unused", model_urn="model", expected_node_ids=("node",))
    assert not (tmp_path / "unused").exists()
    for options in ({"workers": True}, {"workers": 2}, {"timeout": 0}, {"timeout": float("inf")}, {"model_urn": ""}):
        with pytest.raises(ConfigurationError):
            flow.run_matrix(forbidden, matrix(), tmp_path / "unused", **{
                "model_urn": "model", "expected_node_ids": ("node",), **options})


def retained_batch():
    outcomes = []
    for index, case in enumerate(matrix()):
        root = f"session{index}.context"
        fields = dict(runtime_id="runtime", runtime_url="http://127.0.0.1:8094/runtime", context_id=root, scope=root)
        context = Handle("context", **fields)
        job = Handle("job", **fields, job_id=index + 1, service="runtime", service_id="runtime",
                     service_url=fields["runtime_url"], result_kind="observation")
        observation = Handle("observation", **fields, observation_id=index + 10)
        outcomes.append(ExperimentOutcome(case.experiment.name, state="succeeded", stage="processing",
            session_id=f"session{index}", context_handle=context, job_handles=(job,), result_handles=(observation,),
            terminal_outcome="job_result_returned"))
    return ExperimentBatch(tuple(outcomes), 1, 1)


def test_shared_workflow_processes_real_exports_without_claiming_final_acceptance(tmp_path, monkeypatch):
    class Owner:
        def __enter__(self):
            return self
        def __exit__(self, *args):
            pass
    class Runner(Owner):
        def __init__(self, factory, *, workers, processor):
            self.processor = processor
        def run(self, experiments, **kwargs):
            outcome = self.processor(experiments[0], NS(id=42, units="m"))
            assert outcome["raster"]["valid_zero_cells"] == 1
            assert outcome["evidence"]["matched_node_ids"] == ["fixture-computation"]
            return retained_batch()
    monkeypatch.setattr(flow, "SemanticExplorer", lambda client: NS(resolve_observable=lambda value: value))
    monkeypatch.setattr(flow, "ResourceInspector", lambda client: NS(retrieve=lambda value: value))
    monkeypatch.setattr(flow, "ExperimentRunner", Runner)
    monkeypatch.setattr(flow, "fetch_execution_evidence", lambda observation: evidence())
    with dataset() as memory:
        path = tmp_path / "reference-export.tif"
        path.write_bytes(memory.read())
    monkeypatch.setattr(flow, "export_for_analysis", lambda observation, output, **kwargs:
        NS(export=NS(path=path), sha256="b" * 64))
    # Use the real analysis adapter on a local independent raster; only transport
    # and service objects are replaced here, not the scientific validation path.
    real_open = flow.open_raster
    monkeypatch.setattr(flow, "open_raster", lambda artifact: real_open(path))
    result = flow.run_matrix(Owner, matrix(), tmp_path / "outputs", model_urn="existing.model",
                             expected_node_ids=("fixture-computation",), workers=4)
    assert result["matrix_verified"] and not result["scientific_acceptance"]
    assert result["model"] == "existing.model" and len(result["semantics"]) == 2
    assert "fresh-process restoration" in result["remaining_gates"]
    assert result["elapsed_seconds"] >= result["setup_seconds"] >= 0
    assert ExperimentBatch.loads((tmp_path / "outputs/batch.json").read_text()).counts == {"succeeded": 4}


@pytest.mark.parametrize("fault", ["units", "precision", "mask", "value", "coordinates"])
def test_processor_rejects_analysis_changes_and_wrong_observation_units(tmp_path, monkeypatch, fault):
    with dataset() as memory:
        path = tmp_path / "field.tif"
        path.write_bytes(memory.read())
    monkeypatch.setattr(flow, "export_for_analysis", lambda *args, **kwargs:
        NS(export=NS(path=path), sha256="b" * 64))
    analysis = flow.open_raster(path)
    data = analysis.dataset
    if fault == "precision":
        data["value"] = data.value.astype("float32")
    elif fault == "mask":
        data.valid.values[0, 0] = False
    elif fault == "value":
        data.value.values[0, 0] = 100
    elif fault == "coordinates":
        data = data.assign_coords(x=data.x + 1)
    assert isinstance(data, xr.Dataset)
    monkeypatch.setattr(flow, "open_raster", lambda artifact: NS(dataset=data))
    with pytest.raises(AssertionError):
        flow.inspect_result(NS(id=42, units="mm" if fault == "units" else "m"), oracle(), path, ("node",))


def test_restore_uses_original_handles_without_submission(tmp_path, monkeypatch):
    restored = []
    class Owner:
        def __enter__(self):
            return self
        def __exit__(self, *args):
            pass
        def restore_handle(self, handle):
            restored.append(handle)
            return NS(to_handle=lambda: handle)
    class Connection(Owner):
        @property
        def client(self):
            return self
    monkeypatch.setattr(flow, "Connection", Connection)
    monkeypatch.setattr(flow, "inspect_result", lambda *args: {"raster": "verified"})
    batch = retained_batch()
    result = flow.restore_matrix(Connection, matrix(), batch.dumps(), tmp_path / "restored",
                                 expected_node_ids=("node",))
    assert restored == [o.result_handle for o in batch.outcomes]
    assert [r["observation_handle"] for r in result] == [o.result_handle.to_dict() for o in batch.outcomes]
    with pytest.raises(AssertionError, match="order"):
        flow.restore_matrix(Owner, matrix()[::-1], batch.dumps(), tmp_path / "wrong", expected_node_ids=("node",))
    released = replace(batch, outcomes=(replace(batch.outcomes[0], remote_release="confirmed"), *batch.outcomes[1:]))
    with pytest.raises(AssertionError, match="retained"):
        flow.restore_matrix(Owner, matrix(), released.dumps(), tmp_path / "released", expected_node_ids=("node",))
    def wrong_restore(self, handle):
        return NS(to_handle=lambda: restored[-1])
    monkeypatch.setattr(Owner, "restore_handle", wrong_restore)
    with pytest.raises(AssertionError, match="replacement"):
        flow.restore_matrix(Owner, matrix(), batch.dumps(), tmp_path / "replaced", expected_node_ids=("node",))
