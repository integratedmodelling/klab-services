"""Public production imports over isolated HTTP, not a positive Java upload claim."""
import asyncio
import hashlib

import pytest

from klab_client import AsyncClient, AsyncResourceImporter, Client, ResourceImporter
from klab_client.errors import AuthorizationError, InvalidRequestError, MissingAssetError
from klab_simulator.fixtures import CROP_SHA
from klab_simulator.resource_imports import MEDIA, SCHEMA
from klab_simulator.state import State
from conftest import ROOT
from test_state import call, setup
from test_vertical import client_args, control


def source(tmp_path):
    content = (ROOT / "src/klab_simulator/fixtures/usgs-native-20x20.tif").read_bytes()
    path = tmp_path / "private-original-name.tif"
    path.write_bytes(content)
    assert hashlib.sha256(content).hexdigest() == CROP_SHA
    return path


def arguments():
    return {"urn": "simulator:alice:uploaded", "schema_id": SCHEMA, "media_type": MEDIA, "allow_write": True}


def test_owned_upload_job_private_catalog_receipt_and_cleanup(simulator, tmp_path):
    path = source(tmp_path)
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session()
        context = session.create_context()
        try:
            importer = ResourceImporter(client, context)
            assert importer.formats()[0].schema_id == SCHEMA
            with pytest.raises(InvalidRequestError):
                importer.upload(path, **(arguments() | {"allow_write": False}))
            receipt = importer.upload(path, **arguments())
            assert receipt.source_bytes == 2032 and receipt.source_sha256 == CROP_SHA
            assert receipt.service_id == simulator["service_ids"]["resources"]
            assert control(simulator)["resources"] == 0  # Not admitted on acknowledgement.
            with pytest.raises(MissingAssetError):
                client.resources.retrieve(receipt.suggested_urn, "RESOURCE")
            assert receipt.job.status().status == "WAITING"
            assert receipt.job.status().status == "STARTED"
            result = receipt.job.result(timeout=2)
            assert result["results"][0]["resourceUrn"] == receipt.suggested_urn
            assert receipt.suggested_urn in [row.urn for row in client.resources.list("RESOURCE")]
            asset = client.resources.retrieve(receipt.suggested_urn, "RESOURCE")
            assert asset.raw["metadata"]["delegate"]["sourceSha256"] == receipt.source_sha256
            with Client(simulator["endpoints"]["runtime"], **client_args(simulator, "sim-token-bob")) as bob:
                assert receipt.suggested_urn not in [row.urn for row in bob.resources.list("RESOURCE")]
                with pytest.raises(AuthorizationError):
                    bob.resources.retrieve(receipt.suggested_urn, "RESOURCE")
        finally:
            context.release()
            session.release()
    report = control(simulator)
    assert report["resources"] == 0 and not any(report["counts"].values())
    assert sum(row["method"] == "POST" and "/import/" in row["path"] for row in report["trace"]) == 1
    assert path.name not in str(report) and str(tmp_path) not in str(report)


def test_native_async_upload_materialization_and_terminal_cancel(simulator, tmp_path):
    path = source(tmp_path)
    async def run():
        async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            session = await client.create_session()
            context = await session.create_context()
            try:
                importer = AsyncResourceImporter(client, context)
                receipt = await importer.upload(path, **arguments())
                assert await receipt.job.cancel()
                assert (await receipt.job.status()).status == "INTERRUPTED"
                assert control(simulator)["resources"] == 0
                other = await importer.upload(path, **(arguments() | {"urn": "simulator:alice:second"}))
                assert (await other.job.result(timeout=2))["results"][0]["resourceUrn"] == other.suggested_urn
                asset = await client.resources.retrieve(other.suggested_urn, "RESOURCE")
                assert asset.raw["metadata"]["delegate"]["sourceSha256"] == CROP_SHA
            finally:
                await context.release()
                await session.release()
    asyncio.run(run())
    assert control(simulator)["resources"] == 0


def multipart(content=None, name="file", media=MEDIA):
    if content is None:
        content = (ROOT / "src/klab_simulator/fixtures/usgs-native-20x20.tif").read_bytes()
    return (f'--boundary\r\nContent-Disposition: form-data; name="{name}"; filename="upload.tif"\r\n'
            f'Content-Type: {media}\r\n\r\n').encode() + content + b'\r\n--boundary--\r\n'


def upload(state, scope, body, urn="simulator:upload", owner="alice", schema=SCHEMA):
    return state.request("POST", f"/resources/import/{schema}/{urn}",
        {"Authorization": "sim-token-" + owner, "klab-scope": scope,
         "Content-Type": "multipart/form-data; boundary=boundary"}, body)[:2]


@pytest.mark.parametrize("body", [multipart(b"not-a-raster"), multipart(name="other"),
    multipart(media="image/png"), b"", multipart() + multipart()])
def test_malformed_or_unsupported_import_cannot_create_catalog_or_job(body):
    state = State()
    scope = setup(state)
    assert upload(state, scope, body)[0] in (400, 422)
    assert not state.jobs and not state.imported_resources


def test_import_privacy_duplicate_admission_and_resources_job_origin():
    state = State()
    scope = setup(state)
    body = multipart()
    assert upload(state, scope, body, owner="bob")[0] == 403
    assert upload(state, scope, body, schema="unadvertised")[0] == 422
    status, identity = upload(state, scope, body)
    assert status == 200
    assert upload(state, scope, body)[0] == 409
    assert call(state, "GET", f"/jobs/status/{identity}", scope=scope)[0] == 404  # Runtime cannot poll Resources Job.
    assert call(state, "GET", f"/jobs/status/{identity}", scope=scope, service="resources", owner="bob")[0] == 403
    for expected in ("WAITING", "STARTED", "FINISHED"):
        assert call(state, "GET", f"/jobs/status/{identity}", scope=scope, service="resources")[1]["status"] == expected
    assert len(state.imported_resources) == 1
    assert upload(state, scope, body)[0] == 409
    assert call(state, "GET", "/releaseContext", scope=scope)[0] == 200
    assert not state.imported_resources


def test_pending_imports_reserve_the_bounded_catalog_budget():
    state = State()
    scope = setup(state)
    body = multipart()
    for index in range(32):
        assert upload(state, scope, body, urn=f"simulator:upload{index}")[0] == 200
    assert upload(state, scope, body, urn="simulator:overflow")[0] == 429
    assert not state.imported_resources
    assert call(state, "GET", "/releaseSession", scope="s1")[0] == 200
    assert not state.jobs
