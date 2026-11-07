"""Public SDK grants enable reads, never collaborator mutation/disposal or local authority."""
import asyncio

import httpx
import pytest

from klab_client import (AsyncClient, AsyncSharedAccess, Client, SharedAccess, ObservationRequest,
    to_handle)
from klab_client.errors import AuthorizationError, ServerError, SubmissionOutcomeUnknown
from conftest import launch
from test_vertical import client_args, control, geometry


def expire(ready, identity):
    base = ready["endpoints"]["runtime"].rsplit("/", 1)[0]
    response = httpx.post(base + "/__sim__/expireSharedGrant", headers={"Authorization": "sim-admin-control"},
        json={"grantId": identity}, trust_env=False, timeout=3)
    response.raise_for_status()


@pytest.mark.parametrize("native_async", [False, True])
def test_read_grant_public_restore_denied_mutation_revocation_expiry_and_owner_cleanup(native_async):
    with launch("shared-read") as ready:
        async def run():
            async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
                session = await owner.create_session(); context = await session.create_context()
                other = await session.create_context()
                try:
                    job = await context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
                    observation = await job.result(timeout=2)
                    sharing = AsyncSharedAccess(context, allow_experimental=True)
                    assert (await sharing.inspect()).rights == ("READ", "SUBMIT", "RELEASE", "SHARE")
                    assert (await sharing.inspect_grant("bob")).state == "ABSENT"
                    async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as reader:
                        with pytest.raises(AuthorizationError): await reader.attach_context(context.id)
                        grant = await sharing.grant_read("bob")
                        assert (await sharing.inspect_grant("bob")).grant == grant
                        restored = await reader.restore_handle(to_handle(observation))
                        assert not restored._context.owned
                        access = AsyncSharedAccess(restored._context, allow_experimental=True)
                        status = await access.inspect()
                        assert status.principal == "bob" and status.owner == "alice" and status.rights == ("READ",)
                        assert status.grant == grant
                        data = await restored.fetch_data((0, 1, 20, 399))
                        assert data.values == (100, 103, 102, 195) and data.units == "m"
                        saved_job = await reader.restore_handle(to_handle(job))
                        assert (await saved_job.result(timeout=2)).id == observation.id
                        before = control(ready)["counts"]
                        with pytest.raises(AuthorizationError): await restored._context.observe("geography:Elevation in m")
                        with pytest.raises(AuthorizationError): await restored._context.release()
                        with pytest.raises(AuthorizationError): await saved_job.cancel()
                        with pytest.raises(AuthorizationError): await access.grant_read("alice")
                        with pytest.raises(AuthorizationError): await access.revoke(grant.grant_id)
                        with pytest.raises(AuthorizationError): await access.inspect_grant("bob")
                        with pytest.raises(AuthorizationError): await reader.attach_context(other.id)
                        assert control(ready)["counts"] == before
                        assert await sharing.revoke(grant.grant_id)
                        assert (await sharing.inspect_grant("bob")).state == "REVOKED"
                        with pytest.raises(AuthorizationError): await restored.fetch_data((0,))
                        assert (await observation.fetch_data((0,))).values == (100,)
                        renewed = await sharing.grant_read("bob")
                        expire(ready, renewed.grant_id)
                        assert (await sharing.inspect_grant("bob")).state == "EXPIRED"
                        with pytest.raises(ServerError) as expired: await access.inspect()
                        assert expired.value.status_code == 410 and not isinstance(expired.value, AuthorizationError)
                        with pytest.raises(ServerError) as expired_read: await restored.fetch_data((0,))
                        assert expired_read.value.status_code == 410
                finally:
                    await other.release(); await context.release(); await session.release()
        def sync():
            with Client(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
                session = owner.create_session(); context = session.create_context(); other = session.create_context()
                try:
                    job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
                    observation = job.result(timeout=2)
                    sharing = SharedAccess(context, allow_experimental=True)
                    assert sharing.inspect().rights == ("READ", "SUBMIT", "RELEASE", "SHARE")
                    assert sharing.inspect_grant("bob").state == "ABSENT"
                    with Client(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as reader:
                        with pytest.raises(AuthorizationError): reader.attach_context(context.id)
                        grant = sharing.grant_read("bob")
                        assert sharing.inspect_grant("bob").grant == grant
                        restored = reader.restore_handle(to_handle(observation))
                        assert not restored._context.owned
                        access = SharedAccess(restored._context, allow_experimental=True)
                        status = access.inspect()
                        assert status.principal == "bob" and status.owner == "alice" and status.rights == ("READ",)
                        assert status.grant == grant
                        data = restored.fetch_data((0, 1, 20, 399))
                        assert data.values == (100, 103, 102, 195) and data.units == "m"
                        saved_job = reader.restore_handle(to_handle(job))
                        assert saved_job.result(timeout=2).id == observation.id
                        before = control(ready)["counts"]
                        with pytest.raises(AuthorizationError): restored._context.observe("geography:Elevation in m")
                        with pytest.raises(AuthorizationError): restored._context.release()
                        with pytest.raises(AuthorizationError): saved_job.cancel()
                        with pytest.raises(AuthorizationError): access.grant_read("alice")
                        with pytest.raises(AuthorizationError): access.revoke(grant.grant_id)
                        with pytest.raises(AuthorizationError): access.inspect_grant("bob")
                        with pytest.raises(AuthorizationError): reader.attach_context(other.id)
                        assert control(ready)["counts"] == before
                        assert sharing.revoke(grant.grant_id)
                        assert sharing.inspect_grant("bob").state == "REVOKED"
                        with pytest.raises(AuthorizationError): restored.fetch_data((0,))
                        assert observation.fetch_data((0,)).values == (100,)
                        renewed = sharing.grant_read("bob")
                        expire(ready, renewed.grant_id)
                        assert sharing.inspect_grant("bob").state == "EXPIRED"
                        with pytest.raises(ServerError) as expired: access.inspect()
                        assert expired.value.status_code == 410 and not isinstance(expired.value, AuthorizationError)
                        with pytest.raises(ServerError) as expired_read: restored.fetch_data((0,))
                        assert expired_read.value.status_code == 410
                finally:
                    other.release(); context.release(); session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)["counts"].values())


@pytest.mark.parametrize("native_async", [False, True])
def test_revocation_between_point_reads_publishes_no_partial_scientific_data(native_async):
    with launch("shared-read") as ready:
        async def run():
            async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
                session = await owner.create_session(); context = await session.create_context()
                try:
                    observation = await (await context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))).result(timeout=2)
                    sharing = AsyncSharedAccess(context, allow_experimental=True)
                    grant = await sharing.grant_read("bob")
                    class RevokeAfterFirstPoint(httpx.AsyncBaseTransport):
                        def __init__(self): self.delegate = httpx.AsyncHTTPTransport(trust_env=False); self.points = 0
                        async def handle_async_request(self, request):
                            response = await self.delegate.handle_async_request(request)
                            if request.url.path.endswith("/observation/value"):
                                self.points += 1
                                if self.points == 1: assert await sharing.revoke(grant.grant_id)
                            return response
                        async def aclose(self): await self.delegate.aclose()
                    transport = RevokeAfterFirstPoint()
                    async with AsyncClient(ready["endpoints"]["runtime"], http_transport=transport, **client_args(ready, "sim-token-bob")) as reader:
                        restored = await reader.restore_handle(to_handle(observation))
                        with pytest.raises(AuthorizationError): await restored.fetch_data((0, 1))
                        assert transport.points == 2
                    assert (await observation.fetch_data((0, 1))).values == (100, 103)
                finally:
                    await context.release(); await session.release()
        def sync():
            with Client(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
                session = owner.create_session(); context = session.create_context()
                try:
                    observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
                    sharing = SharedAccess(context, allow_experimental=True)
                    grant = sharing.grant_read("bob")
                    class RevokeAfterFirstPoint(httpx.BaseTransport):
                        def __init__(self): self.delegate = httpx.HTTPTransport(trust_env=False); self.points = 0
                        def handle_request(self, request):
                            response = self.delegate.handle_request(request)
                            if request.url.path.endswith("/observation/value"):
                                self.points += 1
                                if self.points == 1: assert sharing.revoke(grant.grant_id)
                            return response
                        def close(self): self.delegate.close()
                    transport = RevokeAfterFirstPoint()
                    with Client(ready["endpoints"]["runtime"], http_transport=transport, **client_args(ready, "sim-token-bob")) as reader:
                        restored = reader.restore_handle(to_handle(observation))
                        with pytest.raises(AuthorizationError): restored.fetch_data((0, 1))
                        assert transport.points == 2
                    assert observation.fetch_data((0, 1)).values == (100, 103)
                finally:
                    context.release(); session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)["counts"].values())


@pytest.mark.parametrize("native_async", [False, True])
def test_lost_grant_reply_reconciles_by_public_owner_lookup_without_replay(native_async):
    with launch("shared-read") as ready:
        class DropGrantReply(httpx.BaseTransport):
            def __init__(self): self.delegate = httpx.HTTPTransport(trust_env=False); self.grants = 0
            def handle_request(self, request):
                response = self.delegate.handle_request(request)
                if request.method == "POST" and request.url.path.endswith("/grants"):
                    self.grants += 1
                    response.read(); response.close()
                    raise httpx.ReadError("Deliberate owned-fixture grant reply loss", request=request)
                return response
            def close(self): self.delegate.close()
        class AsyncDropGrantReply(httpx.AsyncBaseTransport):
            def __init__(self): self.delegate = httpx.AsyncHTTPTransport(trust_env=False); self.grants = 0
            async def handle_async_request(self, request):
                response = await self.delegate.handle_async_request(request)
                if request.method == "POST" and request.url.path.endswith("/grants"):
                    self.grants += 1
                    await response.aread(); await response.aclose()
                    raise httpx.ReadError("Deliberate owned-fixture grant reply loss", request=request)
                return response
            async def aclose(self): await self.delegate.aclose()
        async def run():
            transport = AsyncDropGrantReply()
            async with AsyncClient(ready["endpoints"]["runtime"], http_transport=transport, **client_args(ready)) as owner:
                session = await owner.create_session(); context = await session.create_context()
                try:
                    sharing = AsyncSharedAccess(context, allow_experimental=True)
                    with pytest.raises(SubmissionOutcomeUnknown): await sharing.grant_read("bob")
                    status = await sharing.inspect_grant("bob")
                    assert status.state == "ACTIVE" and status.grant.grantee == "bob" and transport.grants == 1
                    assert await sharing.revoke(status.grant.grant_id)
                    assert (await sharing.inspect_grant("bob")).state == "REVOKED"
                finally:
                    await context.release(); await session.release()
        def sync():
            transport = DropGrantReply()
            with Client(ready["endpoints"]["runtime"], http_transport=transport, **client_args(ready)) as owner:
                session = owner.create_session(); context = session.create_context()
                try:
                    sharing = SharedAccess(context, allow_experimental=True)
                    with pytest.raises(SubmissionOutcomeUnknown): sharing.grant_read("bob")
                    status = sharing.inspect_grant("bob")
                    assert status.state == "ACTIVE" and status.grant.grantee == "bob" and transport.grants == 1
                    assert sharing.revoke(status.grant.grant_id)
                    assert sharing.inspect_grant("bob").state == "REVOKED"
                finally:
                    context.release(); session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)["counts"].values())
