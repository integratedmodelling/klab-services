"""Opt-in real Java async workflow with a predeclared controlled numeric oracle.

This is synthetic known-answer client acceptance, not PaL/provider reproduction.
One session, one context, two scientific Jobs, 20 cells, one cancellation request.
"""
import asyncio
from decimal import Decimal
import json
import os

import pytest

from klab_client import AsyncClient, Endpoint, ObservationRequest
from klab_client.experiment import rectangle_geometry, verify_grid
from klab_client.errors import ConfigurationError

pytestmark = [pytest.mark.functional, pytest.mark.live]


def test_real_java_async_observe_readback_local_wait_and_explicit_cancel(functional_state, record_property):
    if os.environ.get("KLAB_ASYNC_ALLOW_OWNED_WORK") != "1":
        raise ConfigurationError("Async acceptance requires explicit owned-work authorization")
    config = json.loads((functional_state / "scientist.json").read_text())
    oracle = json.loads((functional_state / "async-reference.json").read_text())
    if (oracle.get("kind") != "controlled-constant-async-v1" or oracle.get("units") != "m"
            or oracle.get("expected_decimal") != "123.25" or oracle.get("cells") != 20):
        raise ConfigurationError("Async acceptance requires the fixed independent 100 + 93/4 metre oracle")

    async def scenario():
        urls = config["urls"]
        async with AsyncClient(urls["runtime"], config["token"], agent_name=config["username"],
                **{name: Endpoint(urls[name], config["token"]) for name in ("reasoner", "resources", "resolver")},
                max_concurrency=2, timeout=15, poll_interval=.05) as owner:
            session = context = None
            report = {"kind": "async-controlled-client-acceptance", "scientific_reproduction": False,
                      "cells": 20, "session_released": False, "context_released": False}
            primary = None
            try:
                await owner.initialize_user_scope(local_federation=True)
                advertised = await owner.resources.retrieve(oracle["model_urn"], "MODEL")
                assert advertised.urn == oracle["model_urn"]
                session = await owner.create_session(name="Native async controlled acceptance")
                context = await session.create_context()
                region_job = await context.observe(ObservationRequest("geography:Region", geometry=rectangle_geometry()))
                region = await region_job.result(timeout=120)
                verify_grid(region)
                job = await context.within(region).observe(ObservationRequest("geography:Elevation in m"))
                entered = asyncio.Event()
                async def entered_status(request):
                    if request.url.path.endswith(f"/jobs/status/{job.id}"):
                        entered.set()
                owner.transport._http.event_hooks["request"].append(entered_status)
                wait = asyncio.create_task(job.result(timeout=120))
                try:
                    await asyncio.wait_for(entered.wait(), 5)
                    wait.cancel()
                    with pytest.raises(asyncio.CancelledError):
                        await wait
                finally:
                    owner.transport._http.event_hooks["request"].remove(entered_status)
                    if not wait.done():
                        wait.cancel()
                    await asyncio.gather(wait, return_exceptions=True)
                # Resume the known Job; never submit replacement scientific work.
                result = await job.result(timeout=120)
                verify_grid(result)
                data = await result.fetch_data(range(20), curve="D2_YX")
                assert result.units == data.units == oracle["units"]
                assert data.values == (Decimal(oracle["expected_decimal"]),) * 20
                a, b = await asyncio.gather(result.fetch_data([0], curve="D2_YX"),
                                            result.fetch_data([19], curve="D2_YX"))
                assert a.values == b.values == (Decimal(oracle["expected_decimal"]),)
                reference = job.to_handle()
                assert reference.job_id == job.id and reference.context_id == context.id
                accepted = await region_job.cancel()
                status = await region_job.status()
                assert type(accepted) is bool
                assert status.status in {"FINISHED", "INTERRUPTED", "EMPTY"}
                report.update(local_wait_cancelled=True, original_job_resumed=True,
                    explicit_cancel_accepted=accepted, status_after_cancel=status.status,
                    values_verified=True, units=data.units, expected_decimal=oracle["expected_decimal"],
                    cancellation_limit="Completed-Job acceptance/race only; not proof of interrupting a running simulation")
            except BaseException as error:
                primary = error
                raise
            finally:
                cleanup = []
                for key, scope in (("context_released", context), ("session_released", session)):
                    if scope is not None:
                        try:
                            report[key] = await scope.release() is True
                            if not report[key]:
                                cleanup.append(key)
                        except BaseException as error:
                            cleanup.append(type(error).__name__)
                record_property("async_controlled_workflow", json.dumps(report))
                if cleanup:
                    if primary is not None:
                        primary.add_note("Async acceptance owned-scope cleanup failed: " + ", ".join(cleanup))
                    else:
                        raise AssertionError("Async acceptance owned-scope cleanup failed: " + ", ".join(cleanup))
    asyncio.run(scenario())
