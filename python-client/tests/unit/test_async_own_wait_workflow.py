import asyncio
from types import SimpleNamespace

import pytest

from workflows.async_own_wait import cancel_own_wait, forbidden_during_wait


@pytest.mark.parametrize("path,method,forbidden", [
    ("/runtime/jobs/status/3", "GET", False), ("/runtime/jobs/retrieve/3", "GET", False),
    ("/runtime/jobs/cancel/3", "GET", True), ("/runtime/api/v1/submit", "POST", True),
    ("/runtime/createContext", "POST", True), ("/runtime/releaseContext", "GET", True),
    ("/runtime/unexpected", "DELETE", True)])
def test_wait_audit_rejects_mutations_and_remote_cancel(path, method, forbidden):
    assert forbidden_during_wait({"path": path, "method": method}) is forbidden


@pytest.mark.parametrize("mutate", [False, True])
def test_one_wait_cancel_leaves_other_wait_same_job_and_restores_hooks(mutate):
    async def scenario():
        calls = []
        http = SimpleNamespace(event_hooks={"request": []})
        result = object()
        requests = 0
        async def wait(timeout):
            nonlocal requests
            requests += 1
            request = SimpleNamespace(url=SimpleNamespace(path="/runtime/jobs/status/3"))
            calls.append({"method": "GET", "path": request.url.path})
            for hook in list(http.event_hooks["request"]):
                await hook(request)
            if mutate:
                calls.append({"method": "GET", "path": "/runtime/jobs/cancel/3"})
            return result
        job = SimpleNamespace(id=3, result=wait)
        if mutate:
            with pytest.raises(AssertionError, match="remote cancellation"):
                await cancel_own_wait(job, http, calls)
        else:
            actual, audit = await cancel_own_wait(job, http, calls)
            assert actual is result and len(audit) == 2
        assert requests == 2 and http.event_hooks["request"] == []
        assert not [task for task in asyncio.all_tasks() if task is not asyncio.current_task() and not task.done()]
    asyncio.run(scenario())
