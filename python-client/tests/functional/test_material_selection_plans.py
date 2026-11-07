"""Opt-in authorized native typed-plan compatibility; no Runtime execution.

NOT_RUN here. Fixture explicitly authorizes bounded Resolver Jobs and supplies
maintained native definition/options/expected Dataflow subsets. No simulated
receipt field is required from Java; native numerical execution is a separate gate.
"""
import asyncio
import json

import pytest

from klab_client import AsyncClient, Client, Handle, ObservationOptions, ObservationRequest

pytestmark = [pytest.mark.live, pytest.mark.functional]


def subset(actual, expected):
    if isinstance(expected, dict):
        assert isinstance(actual, dict)
        for key, value in expected.items(): subset(actual[key], value)
    else: assert actual == expected


@pytest.mark.parametrize("native_async", [False, True])
def test_native_typed_plan_options_on_authorized_prepared_context(functional_state, native_async):
    fixture = json.loads((functional_state / "material-selection-plans.json").read_text(encoding="utf-8"))
    assert fixture["allow_owned_resolver_jobs"] is True and fixture["resolver_job_retention_and_cleanup_policy"]
    assert 1 <= len(fixture["cases"]) <= 8
    reference = Handle.loads(json.dumps(fixture["context"]))
    async def run():
        async with AsyncClient.from_env() as api:
            context = await api.restore_handle(reference)
            for case in fixture["cases"]:
                assert case["expected_native_plan_subset"]
                proposal = ObservationRequest(case["definition"], options=ObservationOptions(**case["options"]))
                job = await context.resolve(proposal)
                assert job.service == "resolver" and not context.owned
                subset(await job.result(timeout=30), case["expected_native_plan_subset"])
    if native_async: asyncio.run(run())
    else:
        with Client.from_env() as api:
            context = api.restore_handle(reference)
            for case in fixture["cases"]:
                assert case["expected_native_plan_subset"]
                proposal = ObservationRequest(case["definition"], options=ObservationOptions(**case["options"]))
                job = context.resolve(proposal)
                assert job.service == "resolver" and not context.owned
                subset(job.result(timeout=30), case["expected_native_plan_subset"])
