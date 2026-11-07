"""Complete numerical reference snapshot→offline arrays; NOT native science history."""
import pytest

np = pytest.importorskip("numpy", reason="Dedicated scientific CI installs analysis extras")
pytest.importorskip("xarray", reason="Dedicated scientific CI installs analysis extras")

from klab_client import (Client, ObservationRequest, open_read_plan, read_plan_shard,
    assemble_read_plan, release_read_plan, analyze_read_plan)
from conftest import launch
from test_vertical import client_args, control, geometry


def test_complete_snapshot_offline_analysis_after_grant_scope_and_service_release():
    with launch("zero-negative-nodata") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session(); context = session.create_context()
            try:
                observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
                plan = open_read_plan(observation, shard_cells=73, allow_experimental=True)
                source = assemble_read_plan(plan, tuple(read_plan_shard(plan, n, allow_experimental=True) for n in range(len(plan.shards))))
                release_read_plan(plan, allow_experimental=True)
            finally:
                context.release(); session.release()
            assert control(ready)["read_plans"] == 0 and not any(control(ready)["counts"].values())
    # Owning server has exited and HTTP client is closed; no lazy/hidden remote read.
    result = analyze_read_plan(source)
    assert result.dataset.value.shape == (20, 20) and result.dataset.valid.shape == (20, 20)
    assert result.dataset.value.values[0, 0] == 0 and result.dataset.value.values[0, 1] == -7.5
    assert np.isnan(result.dataset.value.values[0, 2:4]).all()
    np.testing.assert_array_equal(result.dataset.valid.values.ravel(), source.valid)
    np.testing.assert_equal(result.dataset.value.values.ravel(), np.array(source.values, dtype="float64"))
    assert result.dataset.x.values[[0, -1]].tolist() == [50, 1950]
    assert result.dataset.y.values[[0, -1]].tolist() == [1950, 50]
    assert result.dataset.attrs["source_observation_id"] == observation.id
    assert result.dataset.attrs["revision_kind"] == "REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT"
    assert result.dataset.attrs["reference_manifest_sha256"] == result.manifest_sha256
    assert result.dataset.attrs["manifest_hash_kind"] == "LOCAL_REFERENCE_DESCRIPTOR_NOT_NATIVE_COMMIT"
