"""Public SDK options change executed synthetic values, not just request echoes."""
import asyncio

import pytest

from klab_client import (AsyncClient, Client, ContextOptions, GridOptions, ObservationOptions,
    ObservationRequest, RectangularGeometry)
from klab_client.errors import AuthorizationError, MissingAssetError, UnsupportedOperationError
from conftest import launch
from test_vertical import client_args, control


CASES = (({}, "synthetic.base", 0), ({"namespace": "synthetic.alternate"}, "synthetic.alternate", 10),
    ({"project": "synthetic.alternate-project"}, "synthetic.alternate", 10),
    ({"scenarios": ("synthetic.scenario",)}, "synthetic.scenario", 20),
    ({"namespace": "synthetic.alternate", "scenarios": ("synthetic.scenario",)}, "synthetic.scenario", 20))


def request(options):
    return ObservationRequest("synthetic:Signal in m", options=options,
        geometry=RectangularGeometry(0, 4, 0, 4, 4, 4))


def check(plan, observation, data, namespace, offset, span, bias, observer):
    actual = observation.metadata["simulator:selection"]
    assert actual == plan["selection"]
    assert actual["namespace"] == namespace and actual["model"] == namespace + ".model"
    assert actual["observer"] == observer and actual["grid"]["span"] == span
    n = 4 // span
    # Oracle deliberately uses no simulator code, DTO metadata or returned coordinates.
    expected = tuple(span * (col + .5) + 2 * span * (row + .5) + offset + bias
        for row in range(n) for col in range(n))
    assert data.values == expected and data.units == "m" and len(data.values) == n*n
    assert observation.geometry.size() == n*n and actual["shape"] == [n, n]
    assert "NOT_NATIVE" in actual["evidence"]


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("span", [1, 2])
def test_public_material_plan_observe_read_and_owned_cleanup(native_async, span):
    with launch("material-selections") as ready:
        async def run():
            async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as api:
                session = await api.create_session()
                context = await session.create_context(options=ContextOptions(grid=GridOptions(0, 0, span)))
                try:
                    agent_a = await (await context.observe("synthetic:ObserverA")).result(timeout=2)
                    agent_b = await (await context.observe("synthetic:ObserverB")).result(timeout=2)
                    for settings, namespace, offset in CASES:
                        for observer, bias in ((None, 0), (agent_a, 0), (agent_b, 100)):
                            options = ObservationOptions(**settings, observer=observer)
                            proposal = request(options)
                            before = control(ready)["counts"]["observations"]
                            plan = await (await context.resolve(proposal)).result(timeout=2)
                            assert control(ready)["counts"]["observations"] == before
                            observation = await (await context.observe(proposal)).result(timeout=2)
                            data = await observation.fetch_data(range((4//span)**2))
                            check(plan, observation, data, namespace, offset, span, bias, observer.id if observer else None)
                    with pytest.raises(UnsupportedOperationError): ObservationOptions(selected_model="synthetic.base.model")
                    with pytest.raises(MissingAssetError):
                        await context.resolve(request(ObservationOptions(observer=999)))
                    async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as bob:
                        with pytest.raises(AuthorizationError): await bob.attach_context(context.id)
                finally:
                    await context.release(); await session.release()
        def sync():
            with Client(ready["endpoints"]["runtime"], **client_args(ready)) as api:
                session = api.create_session()
                context = session.create_context(options=ContextOptions(grid=GridOptions(0, 0, span)))
                try:
                    agent_a = context.observe("synthetic:ObserverA").result(timeout=2)
                    agent_b = context.observe("synthetic:ObserverB").result(timeout=2)
                    for settings, namespace, offset in CASES:
                        for observer, bias in ((None, 0), (agent_a, 0), (agent_b, 100)):
                            proposal = request(ObservationOptions(**settings, observer=observer))
                            before = control(ready)["counts"]["observations"]
                            plan = context.resolve(proposal).result(timeout=2)
                            assert control(ready)["counts"]["observations"] == before
                            observation = context.observe(proposal).result(timeout=2)
                            data = observation.fetch_data(range((4//span)**2))
                            check(plan, observation, data, namespace, offset, span, bias, observer.id if observer else None)
                    with pytest.raises(UnsupportedOperationError): ObservationOptions(selected_model="synthetic.base.model")
                    with pytest.raises(MissingAssetError): context.resolve(request(ObservationOptions(observer=999)))
                    with Client(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as bob:
                        with pytest.raises(AuthorizationError): bob.attach_context(context.id)
                finally:
                    context.release(); session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)["counts"].values())
