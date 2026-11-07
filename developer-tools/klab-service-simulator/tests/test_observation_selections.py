"""Public SDK endpoints/collection workflow, finite synthetic admission only."""
import asyncio

import pytest

from klab_client import Client, AsyncClient, ObservationRequest, Handle, to_handle
from klab_client.errors import AuthorizationError, InvalidRequestError, MissingAssetError
from conftest import launch
from test_vertical import client_args, control


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("handle_kind", ["context", "observation", "job"])
def test_public_relationship_endpoints_collections_plan_foreign_denial_and_cleanup(native_async, handle_kind):
    with launch("observation-selections") as ready:
        async def run():
            async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
                session = await owner.create_session(); context = await session.create_context()
                try:
                    a = await (await context.observe("synthetic:Entity")).result(timeout=2)
                    b = await (await context.observe("synthetic:Entity")).result(timeout=2)
                    selected = context.between(a, b)
                    assert selected.relationship_selection.source_id == a.id
                    request = ObservationRequest(await owner.reasoner.resolve_observable("synthetic:Connection"))
                    planned = await (await owner.resolver.resolve(request.to_wire(), selected)).result(timeout=2)
                    assert planned["selection"]["source"] == a.id and planned["selection"]["target"] == b.id
                    job = await selected.observe(request)
                    relation = await job.result(timeout=2)
                    assert relation.metadata["simulator:selection"]["target"] == b.id
                    first = await context.observations(limit=2)
                    assert [row.id for row in first.items] == [a.id, b.id] and first.next_offset == 2
                    last = await context.observations(offset=first.next_offset, limit=2)
                    assert [row.id for row in last.items] == [relation.id] and last.next_offset is None and last.partial
                    child = await (await context.within(a).observe("synthetic:Entity")).result(timeout=2)
                    children = await context.within(a).observations()
                    assert [row.id for row in children.items] == [child.id]
                    assert context.between(a, b).within(a).relationship_selection is None
                    saved = Handle.loads(to_handle({"context": selected, "observation": relation, "job": job}[handle_kind]).dumps())
                    assert saved.version == 2
                    async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as reader:
                        restored = await reader.restore_handle(saved)
                        restored_context = restored if handle_kind == "context" else restored._context if handle_kind == "observation" else restored.scope
                        if handle_kind == "job": assert (await restored.result(timeout=2)).id == relation.id
                        assert restored_context.relationship_selection == selected.relationship_selection and not restored_context.owned
                        assert len((await restored_context.observations()).items) == 3
                    async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as foreign:
                        with pytest.raises(AuthorizationError): await foreign.attach_context(context.id)
                    with pytest.raises(MissingAssetError):
                        await context.between(a.id, 999).observe(request)
                    with pytest.raises(InvalidRequestError): context.between(a, a)
                finally:
                    await context.release(); await session.release()
        def sync():
            with Client(ready["endpoints"]["runtime"], **client_args(ready)) as owner:
                session = owner.create_session(); context = session.create_context()
                try:
                    a = context.observe("synthetic:Entity").result(timeout=2)
                    b = context.observe("synthetic:Entity").result(timeout=2)
                    selected = context.between(a, b)
                    assert selected.relationship_selection.target_id == b.id
                    request = ObservationRequest(owner.reasoner.resolve_observable("synthetic:Connection"))
                    planned = owner.resolver.resolve(request.to_wire(), selected).result(timeout=2)
                    assert planned["selection"]["source"] == a.id and planned["selection"]["target"] == b.id
                    job = selected.observe(request)
                    relation = job.result(timeout=2)
                    assert relation.metadata["simulator:selection"]["source"] == a.id
                    first = context.observations(limit=2)
                    assert [row.id for row in first.items] == [a.id, b.id] and first.next_offset == 2
                    last = context.observations(offset=2, limit=2)
                    assert [row.id for row in last.items] == [relation.id] and last.next_offset is None and last.partial
                    child = context.within(a).observe("synthetic:Entity").result(timeout=2)
                    assert [row.id for row in context.within(a).observations().items] == [child.id]
                    assert selected.within(a).relationship_selection is None
                    saved = Handle.loads(to_handle({"context": selected, "observation": relation, "job": job}[handle_kind]).dumps())
                    assert saved.version == 2
                    with Client(ready["endpoints"]["runtime"], **client_args(ready)) as reader:
                        restored = reader.restore_handle(saved)
                        restored_context = restored if handle_kind == "context" else restored._context if handle_kind == "observation" else restored.scope
                        if handle_kind == "job": assert restored.result(timeout=2).id == relation.id
                        assert restored_context.relationship_selection == selected.relationship_selection and not restored_context.owned
                        assert len(restored_context.observations().items) == 3
                    with Client(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as foreign:
                        with pytest.raises(AuthorizationError): foreign.attach_context(context.id)
                    with pytest.raises(MissingAssetError): context.between(a.id, 999).observe(request)
                    with pytest.raises(InvalidRequestError): context.between(a, a)
                finally:
                    context.release(); session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)["counts"].values())
