"""Real unchanged SDK -> separate simulator process -> every400cells; never Java science."""
import asyncio
from decimal import Decimal
import json

import httpx
import pytest

from klab_client import (AsyncClient, Client, ConnectionIdentity, DeploymentProfile, Endpoint,
    GeometryImpl, IssuedTokenProvider, ObservationRequest, RectangularGeometry, ScientificData,
    ServiceReference, async_fetch_provenance, connect, fetch_provenance)
from klab_client.errors import AuthorizationError, JobCancelledError, JobFailedError, ProtocolError
from klab_simulator.fixtures import authentic_fixture
from conftest import launch, ROOT


def control(ready):
    base = ready["endpoints"]["runtime"].rsplit("/", 1)[0]
    response = httpx.get(base + "/__sim__/state", headers={"Authorization": "sim-admin-control"},
                         timeout=3, trust_env=False)
    response.raise_for_status()
    return response.json()


def geometry(grid=None):
    if grid is None:
        return RectangularGeometry(0, 2000, 0, 2000, 20, 20)
    raw = RectangularGeometry(0, 2000, 0, 2000, 20, 20).to_geometry().to_wire()
    west, south, east, north = grid["bounds"]
    params = raw["dimensions"][0]["parameters"]["delegate"]
    params.pop("sgrid")  # Do not carry the projected100m request into native arcseconds.
    params.update(proj="EPSG:4269", shape=f"EPSG:4269 POLYGON (({west} {south},{west} {north},{east} {north},{east} {south},{west} {south}))")
    return GeometryImpl.from_wire(raw)


def client_args(ready, token="sim-token-alice"):
    endpoints = ready["endpoints"]
    return {"credential": token, "agent_name": "alice", "runtime_service_id": ready["service_ids"]["runtime"],
            "reasoner": Endpoint(endpoints["reasoner"], token), "resources": Endpoint(endpoints["resources"], token),
            "resolver": Endpoint(endpoints["resolver"], token), "poll_interval": .001, "timeout": 3}


def verify_data(observation, blocks, expected, grid):
    actual = [x for block in blocks for x in block.values]
    frozen = [x for row in expected for x in row]
    assert len(actual) == len(frozen) == 400
    # Native recorded float32 values were stored in JSON as Python floats. Match
    # exact native numeric values, not Decimal-versus-binary representation types.
    assert [float(x) if isinstance(x, Decimal) else x for x in actual] == frozen
    metadata = observation.metadata["grid"]
    for key in ("shape", "bounds", "crs", "unit", "row_orientation", "axis_order", "centre_origin", "cell_size", "precision"):
        assert metadata[key] == grid[key]
    # Verify centres for every cell independently from server state and metadata.
    for index in range(400):
        row, column = divmod(index, 20)
        x = metadata["centre_origin"][0] + column * metadata["cell_size"][0]
        y = metadata["centre_origin"][1] + row * metadata["cell_size"][1]
        assert x == pytest.approx(grid["centre_origin"][0] + column * grid["cell_size"][0])
        assert y == pytest.approx(grid["centre_origin"][1] + row * grid["cell_size"][1])
    for block in blocks:
        restored = ScientificData.loads(block.dumps())
        assert restored.values == block.values
        assert block.units == "m"


def test_synthetic400_discovery_resolver_values_provenance_and_owned_cleanup(simulator):
    expected = json.loads((ROOT / "src/klab_simulator/fixtures/synthetic-oracle.json").read_text())
    refs = tuple(ServiceReference(kind, url) for kind, url in simulator["endpoints"].items())
    profile = DeploymentProfile("simulator", refs, timeout=3, poll_interval=.001)
    credentials = IssuedTokenProvider(ConnectionIdentity("alice", "synthetic"),
                                      {ref.url: "sim-token-alice" for ref in refs})
    with connect(profile, credentials=credentials) as connection:
        client = connection.client
        assert len(connection.catalog.entries) == 4
        session = client.create_session()
        context = session.create_context()
        try:
            model = client.resources.list("MODEL")[0]
            assert client.resources.retrieve(model.urn, "MODEL").urn == "simulator.elevation"
            resolved = client.reasoner.resolve_observable("geography:Elevation in m")
            proposal = ObservationRequest(resolved, geometry=geometry()).to_wire()
            selection = client.resolver.resolve(proposal, context)
            assert selection.status().status == "WAITING"
            assert selection.status().status == "STARTED"
            plan = selection.result(timeout=2)
            assert plan["resolutionOutcome"] == "RESOLVED"
            assert plan["requirements"]["results"][0]["resourceUrn"] == model.urn
            job = context.observe(ObservationRequest(resolved, geometry=geometry()))
            assert job.status().status == "WAITING"
            assert job.status().status == "STARTED"
            observation = job.result(timeout=2)
            blocks = [observation.fetch_data(range(0, 256)), observation.fetch_data(range(256, 400))]
            verify_data(observation, blocks, expected["values"], expected)
            view = fetch_provenance(observation)
            assert len(view.links) == 1 and view.links[0].relationship == "MEASURED"
            assert view.links[0].target.id == observation.id
            assert view.links[0].source.outcome == "SUCCESS"
            assert view.partial  # finite scenario is not complete Java lineage
            with Client(simulator["endpoints"]["runtime"], **client_args(simulator, "sim-token-bob")) as bob:
                with pytest.raises(AuthorizationError):
                    bob.attach_context(context.id)
        finally:
            assert context.release() is True
            assert session.release() is True
    report = control(simulator)
    assert report["counts"] == {"sessions": 0, "contexts": 0, "jobs": 0, "observations": 0}
    assert all(row["backend"] == "SIMULATED" for row in report["trace"])
    assert all(row["contract_category"] == "SIMULATED_PROVISIONAL" for row in report["trace"])
    assert all(row["fixture_origin"] == "SYNTHETIC_SIMULATOR" for row in report["trace"])
    assert "sim-token-" not in json.dumps(report)


@pytest.mark.parametrize("scenario", ["elevation", "usgs-recorded"])
def test_native_async400_and_authentic_recorded_delivery_not_java(scenario):
    if scenario == "elevation":
        oracle = json.loads((ROOT / "src/klab_simulator/fixtures/synthetic-oracle.json").read_text())
        expected, grid, geo = oracle["values"], oracle, geometry()
    else:
        oracle = authentic_fixture()
        expected = oracle["allCellsNorthToSouthWestToEast"]
        grid = {"shape": [20, 20], "bounds": oracle["bounds"], "crs": "EPSG:4269", "unit": "m",
                "row_orientation": "north_to_south", "axis_order": "x,y", "precision": "float32",
                "centre_origin": [oracle["affine"][2] + oracle["affine"][0] / 2,
                                  oracle["affine"][5] + oracle["affine"][4] / 2],
                "cell_size": [oracle["affine"][0], oracle["affine"][4]]}
        geo = geometry(oracle)
    with launch(scenario) as ready:
        async def run():
            async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                await client.initialize_user_scope()
                session = await client.create_session()
                context = await session.create_context()
                try:
                    job = await context.observe(ObservationRequest("geography:Elevation in m", geometry=geo))
                    assert (await job.status()).status == "WAITING"
                    assert (await job.status()).status == "STARTED"
                    observation = await job.result(timeout=2)
                    blocks = [await observation.fetch_data(range(256)), await observation.fetch_data(range(256, 400))]
                    verify_data(observation, blocks, expected, grid)
                    view = await async_fetch_provenance(observation)
                    assert len(view.links) == 1 and view.links[0].target.id == observation.id
                finally:
                    assert await context.release()
                    assert await session.release()
        asyncio.run(run())
        report = control(ready)
        assert not any(report["counts"].values())
        origin = "SYNTHETIC_SIMULATOR" if scenario == "elevation" else "AUTHENTIC_FIXTURE_SIMULATED_DELIVERY"
        assert all(row["fixture_origin"] == origin for row in report["trace"])


@pytest.mark.parametrize("scenario,error", [("empty-result", JobFailedError),
    ("malformed-empty", ProtocolError), ("failure", JobFailedError)])
def test_deliberate_job_failures_are_visible_and_cleanup_is_owned(scenario, error):
    with launch(scenario) as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session()
            context = session.create_context()
            try:
                job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
                with pytest.raises(error):
                    job.result(timeout=2)
            finally:
                context.release()
                session.release()
        assert not any(control(ready)["counts"].values())


def test_cancellation_is_terminal_and_is_not_a_success(simulator):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session()
        context = session.create_context()
        try:
            job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
            assert job.cancel() is True
            assert job.cancel() is False
            with pytest.raises(JobCancelledError):
                job.result(timeout=1)
            assert control(simulator)["counts"]["observations"] == 0
        finally:
            context.release()
            session.release()


def test_separately_named_zero_negative_nodata_and_nan_fixture():
    with launch("zero-negative-nodata") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session()
            context = session.create_context()
            try:
                observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
                values = observation.fetch_data((0, 1, 2, 3)).values
                assert values[:3] == (0, -7.5, None)
                assert values[3].is_nan()
            finally:
                context.release()
                session.release()
