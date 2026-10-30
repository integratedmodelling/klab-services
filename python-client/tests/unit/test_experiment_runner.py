"""Deterministic lifecycle controls and genuine bounded thread overlap; no Java claims."""
from copy import deepcopy
import json
from pathlib import Path
from threading import Barrier, Event, Lock

import httpx
import pytest

from klab_client import (BatchInterrupted, Client, Endpoint, Experiment, ExperimentBatch, ExperimentRunner,
    OwnedContextPolicy, ObservationRequest)
from klab_client.client import Job
from klab_client.errors import (ConfigurationError, InvalidRequestError, JobCancelledError, JobFailedError,
    JobUnavailableError, ProtocolError, SubmissionOutcomeUnknown, TransportError, WaitTimeout, WaitCancelled)

NATIVE_RESULT = Job.result


def experiments(count=4, *, release="retain", initializer=False):
    return tuple(Experiment("case" + str(i), ObservationRequest("geography:Elevation in m"),
        OwnedContextPolicy(release=release), context_request=ObservationRequest("earth:Region") if initializer else None,
        variation="supplied request option") for i in range(count))


class Clock:
    def __init__(self):
        self.now = 0

    def __call__(self):
        return self.now

    def advance(self, seconds):
        self.now += seconds


class Stack:
    def __init__(self, monkeypatch, waiter=None):
        self.clients, self.calls, self.submissions, self.releases, self.names = [], [], [], [], {}
        self.lock = Lock()
        self.waiter = waiter
        self.setup_status, self.submit_status, self.cancel_status, self.release_status = 200, 200, 200, 200
        self.release_accepted = True
        self.cancel_accepted = True
        self.payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
        self.payload["observable"]["contextualization"] = "MEASURE"
        monkeypatch.setattr(Job, "result", lambda job, timeout=None, **kwargs: self.wait(job, timeout))

    def factory(self):
        local = {"jobs": {}}
        def handler(request):
            with self.lock:
                self.calls.append(request)
            path = request.url.path
            if path.endswith("/createSession"):
                if self.setup_status != 200:
                    return httpx.Response(self.setup_status, json={"message": "setup unavailable"})
                configuration = json.loads(request.content)["configuration"]
                self.names[configuration["id"]] = configuration["name"].removeprefix("experiment-")
                return httpx.Response(200, text=configuration["id"])
            if path.endswith("/createContext"):
                return httpx.Response(200, json={"id": request.headers["klab-scope"] + ".c", "serviceId": "home"})
            if path.endswith("/resolve/observable"):
                return httpx.Response(200, json=self.payload["observable"])
            if path.endswith("/submit"):
                id = len(local["jobs"]) + 1
                local["jobs"][id] = request.headers["klab-scope"]
                with self.lock:
                    self.submissions.append((self.name_from_scope(request.headers["klab-scope"]), id, request.headers["klab-scope"]))
                return httpx.Response(self.submit_status, json=id if self.submit_status == 200 else {"message": "submission rejected"})
            if "/jobs/cancel/" in path:
                return httpx.Response(self.cancel_status, json=self.cancel_accepted)
            if "/jobs/status/" in path:
                return httpx.Response(200, json={"status": "FINISHED"})
            if "/jobs/retrieve/" in path:
                id = int(path.rsplit("/", 1)[-1])
                payload = deepcopy(self.payload)
                payload["id"] = id + 40
                payload["urn"] = ".".join(local["jobs"][id].split(".")[:2]) + "." + str(payload["id"])
                return httpx.Response(200, json=payload)
            if path.endswith(("/releaseContext", "/releaseSession")):
                self.releases.append((path, request.headers["klab-scope"]))
                return httpx.Response(self.release_status, json=self.release_accepted)
            raise AssertionError("Unexpected test route")
        client = Client("https://runtime.invalid", "credential-secret", agent_name="scientist", runtime_service_id="home",
            reasoner=Endpoint("https://reasoner.invalid", "reasoner-secret"), http_transport=httpx.MockTransport(handler))
        self.clients.append(client)
        return client

    def name_from_scope(self, token):
        return self.names[token.split(".")[0]]

    def wait(self, job, timeout):
        if self.waiter is not None:
            result = self.waiter(job, timeout)
            if result is not None:
                return result
        payload = deepcopy(self.payload)
        payload["id"] = job.id + 40
        payload["urn"] = job.scope.id + "." + str(payload["id"])
        return job._decoder(payload)


def test_four_sequential_owned_experiments_have_stable_associations_and_local_close(monkeypatch):
    stack = Stack(monkeypatch)
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(initializer=True))
        assert batch.counts == {"succeeded": 4}
        assert [item.name for item in batch.outcomes] == ["case0", "case1", "case2", "case3"]
        for outcome in batch.outcomes:
            assert len(outcome.job_handles) == len(outcome.result_handles) == 2
            assert outcome.job_handle.job_id == 2 and outcome.result_handle.observation_id == 42
            assert outcome.job_handle.scope.endswith(".41")
            assert outcome.context_handle.context_id == outcome.result_handle.context_id
            assert outcome.context_handle.runtime_id == outcome.job_handle.runtime_id == "home"
            assert outcome.session_id == outcome.context_handle.context_id.split(".")[0]
            assert outcome.remote_release == "retained" and not outcome.cleanup_errors
        assert len({item.context_handle.context_id for item in batch.outcomes}) == 4
        assert "case0: succeeded" in batch._repr_html_()
        restored = ExperimentBatch.loads(batch.dumps())
        assert restored == batch and all(not outcome.jobs and not outcome.results for outcome in restored.outcomes)
    assert all(client.transport._http.is_closed for client in stack.clients) and not stack.releases


def test_real_thread_overlap_bounded_at_four_and_completion_order_does_not_reorder_summary(monkeypatch):
    barrier, gates = Barrier(4), [Event() for _ in range(4)]
    lock, active, peak, completed = Lock(), 0, 0, []
    stack = None
    def waiter(job, timeout):
        nonlocal active, peak
        index = int(stack.name_from_scope(job.scope.id)[4:])
        with lock:
            active += 1
            peak = max(peak, active)
        barrier.wait(timeout=5)
        if index != 3:
            assert gates[index].wait(timeout=5)
        with lock:
            completed.append(index)
            active -= 1
        if index:
            gates[index - 1].set()
    stack = Stack(monkeypatch, waiter)
    with ExperimentRunner(stack.factory, workers=4) as runner:
        batch = runner.run(experiments())
    assert peak == 4 and completed == [3, 2, 1, 0]
    assert batch.counts == {"succeeded": 4} and [item.name for item in batch.outcomes] == ["case0", "case1", "case2", "case3"]
    assert len(stack.submissions) == 4 and len({item.context_handle.context_id for item in batch.outcomes}) == 4


@pytest.mark.parametrize("failure,state,terminal", [(JobFailedError(1, "bad"), "job_failed", "failed"),
    (JobCancelledError(1, "interrupted"), "job_cancelled", "interrupted"),
    (JobUnavailableError(1, "expired"), "job_unavailable", None), (TransportError("credential-secret"), "wait_error", None)])
def test_one_failure_retains_siblings_and_handles_without_replay(monkeypatch, failure, state, terminal):
    stack = None
    def waiter(job, timeout):
        if stack.name_from_scope(job.scope.id) == "case1":
            raise failure
    stack = Stack(monkeypatch, waiter)
    with ExperimentRunner(stack.factory, workers=4) as runner:
        batch = runner.run(experiments())
    assert batch.counts == {"succeeded": 3, state: 1}
    outcome = batch.outcomes[1]
    assert outcome.state == state and outcome.terminal_outcome == terminal and outcome.job_handle is not None
    assert len(stack.submissions) == 4
    assert "credential-secret" not in batch.dumps()


def test_deadline_retains_job_and_context_without_remote_cleanup(monkeypatch):
    clock = Clock()
    def waiter(job, timeout):
        clock.advance(timeout + .01)
        raise WaitTimeout(job)
    stack = Stack(monkeypatch, waiter)
    with ExperimentRunner(stack.factory, _clock=clock) as runner:
        batch = runner.run(experiments(1, release="terminal"), timeout=.2)
    outcome = batch.outcomes[0]
    assert outcome.state == "wait_timed_out" and outcome.job_handle is not None
    assert outcome.context_handle is not None and outcome.terminal_outcome is None
    assert not stack.releases and len(stack.submissions) == 1


@pytest.mark.parametrize("remote,status,accepted", [(False, 200, True), (True, 200, True), (True, 200, False), (True, 503, True)])
def test_local_wait_cancel_is_distinct_from_remote_cancel_acceptance(monkeypatch, remote, status, accepted):
    cancel = Event()
    def waiter(job, timeout):
        cancel.set()
        raise WaitCancelled(job)
    stack = Stack(monkeypatch, waiter)
    stack.cancel_status, stack.cancel_accepted = status, accepted
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1), cancel=cancel, cancel_remote=remote)
    outcome = batch.outcomes[0]
    assert outcome.state == "wait_cancelled" and outcome.terminal_outcome is None
    assert outcome.cancellation_requested is remote
    assert outcome.cancellation_accepted is (accepted if remote and status == 200 else None)
    assert len([call for call in stack.calls if "/cancel/" in call.url.path]) == int(remote)
    assert outcome.job_handle is not None and not stack.releases


def test_cancel_before_start_and_deadline_before_submission_do_not_mutate(monkeypatch):
    stack, cancel = Stack(monkeypatch), Event()
    cancel.set()
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1), cancel=cancel)
    assert batch.outcomes[0].state == "not_submitted_cancelled" and not stack.clients
    clock = Clock()
    def factory():
        clock.advance(2)
        return stack.factory()
    with ExperimentRunner(factory, _clock=clock) as runner:
        batch = runner.run(experiments(1), timeout=1)
    assert batch.outcomes[0].state == "not_submitted_deadline" and not stack.submissions


@pytest.mark.parametrize("status,state", [(403, "submission_rejected"), (503, "submission_unknown")])
def test_ambiguous_submissions_never_retried(monkeypatch, status, state):
    stack = Stack(monkeypatch)
    stack.submit_status = status
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1, release="terminal"))
    assert batch.outcomes[0].state == state and not batch.outcomes[0].jobs
    assert len(stack.submissions) == 1 and not stack.releases


def test_remote_cleanup_is_explicit_owned_and_failures_do_not_hide_success(monkeypatch):
    stack = Stack(monkeypatch)
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(2, release="terminal"))
    assert all(item.remote_release == "confirmed" for item in batch.outcomes)
    assert len(stack.releases) == 4
    assert {scope.split(".")[0] for _, scope in stack.releases} == {item.session_id for item in batch.outcomes}
    stack = Stack(monkeypatch)
    stack.release_status = 503
    original = Client.close
    def close(client):
        original(client)
        raise OSError("credential-secret")
    monkeypatch.setattr(Client, "close", close)
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1, release="terminal"))
    outcome = batch.outcomes[0]
    assert outcome.state == "succeeded" and outcome.remote_release == "failed" and len(outcome.cleanup_errors) == 3
    assert outcome.cleanup_errors[-1] == "OSError" and "credential-secret" not in batch.dumps()
    assert len(stack.releases) == 2


def test_shared_or_borrowed_clients_cannot_gain_cleanup_ownership(monkeypatch):
    stack = Stack(monkeypatch)
    shared = stack.factory()
    with ExperimentRunner(lambda: shared) as runner:
        batch = runner.run(experiments())
    assert batch.counts == {"succeeded": 1, "setup_failed": 3}
    assert len(stack.submissions) == 1
    with ExperimentRunner(lambda: object()) as runner:
        assert runner.run(experiments(1)).outcomes[0].state == "setup_failed"


def test_processor_runs_while_open_and_private_output_is_not_serialized(monkeypatch):
    stack = Stack(monkeypatch)
    def processor(experiment, observation):
        assert not observation._context.client.transport._http.is_closed
        return {"token": "credential-secret", "verified": True}
    with ExperimentRunner(stack.factory, processor=processor) as runner:
        batch = runner.run(experiments(1))
    assert batch.outcomes[0].processed["verified"] is True and "credential-secret" not in batch.dumps()
    def broken_processor(experiment, observation):
        raise RuntimeError("credential-secret")
    with ExperimentRunner(stack.factory, processor=broken_processor) as runner:
        batch = runner.run(experiments(1))
    assert batch.outcomes[0].state == "processing_failed" and batch.outcomes[0].result_handle is not None


def test_interrupt_retains_known_job_in_explicit_batch_exception(monkeypatch):
    def waiter(job, timeout):
        raise KeyboardInterrupt()
    stack = Stack(monkeypatch, waiter)
    with ExperimentRunner(stack.factory) as runner, pytest.raises(BatchInterrupted) as interrupted:
        runner.run(experiments(1))
    outcome = interrupted.value.batch.outcomes[0]
    assert outcome.state == "wait_interrupted" and outcome.job_handle is not None
    assert all(client.transport._http.is_closed for client in stack.clients)


def test_close_during_wait_is_local_even_when_remote_cancellation_enabled(monkeypatch):
    runner = None
    def waiter(job, timeout):
        runner.close()
        raise WaitCancelled(job)
    stack = Stack(monkeypatch, waiter)
    runner = ExperimentRunner(stack.factory)
    batch = runner.run(experiments(1), cancel_remote=True)
    assert batch.outcomes[0].state == "wait_cancelled" and not batch.outcomes[0].cancellation_requested
    assert not stack.releases and not any("/cancel/" in call.url.path for call in stack.calls)
    with pytest.raises(InvalidRequestError):
        runner.run(experiments(1))


def test_request_policy_and_runner_input_validation(monkeypatch):
    for kwargs in ({"connection_factory": None}, {"connection_factory": lambda: None, "workers": True},
        {"connection_factory": lambda: None, "processor": False}, {"connection_factory": lambda: None, "max_experiments": 0}):
        with pytest.raises(InvalidRequestError):
            ExperimentRunner(**kwargs)
    for kwargs in ({"name": "invalid name"}, {"request": "untyped"}, {"context_policy": None},
        {"context_request": "untyped"}, {"variation": 4}):
        values = {"name": "case", "request": ObservationRequest("earth:Region"), "context_policy": OwnedContextPolicy(), **kwargs}
        with pytest.raises(InvalidRequestError):
            Experiment(**values)
    for kwargs in ({"release": "caller"}, {"options": {}}):
        with pytest.raises(InvalidRequestError):
            OwnedContextPolicy(**kwargs)
    stack = Stack(monkeypatch)
    with ExperimentRunner(stack.factory, max_experiments=1) as runner:
        for selected in ([], [None], experiments(2)):
            with pytest.raises(InvalidRequestError):
                runner.run(selected)
        for kwargs in ({"timeout": True}, {"timeout": 0}, {"timeout": float("inf")}, {"cancel_remote": None}, {"cancel": True}):
            with pytest.raises(InvalidRequestError):
                runner.run(experiments(1), **kwargs)
    assert not stack.clients


def test_manifest_bounds_unknown_versions_and_malformed_reports(monkeypatch):
    stack = Stack(monkeypatch)
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1))
    with pytest.raises(InvalidRequestError):
        batch.dumps(max_bytes=1)
    with pytest.raises(InvalidRequestError):
        ExperimentBatch.loads(batch.dumps(), max_bytes=1)
    data = json.loads(batch.dumps())
    for changes in ({"version": 2}, {"workers": 0}, {"outcomes": []}, {"outcomes": [None]},
        {"elapsed": -1}, {"counts": {}}, {"scope": "durable server continuation"}):
        with pytest.raises(InvalidRequestError):
            ExperimentBatch.loads(json.dumps({**data, **changes}))
    for changes in ({"state": "NEW"}, {"elapsed": -1}, {"cancellation_requested": 1}, {"cancellation_accepted": "yes"},
        {"cleanup_errors": ["arbitrary message"]}, {"error_type": 3}, {"job_handles": [data["outcomes"][0]["context_handle"]]},
        {"context_handle": data["outcomes"][0]["job_handles"][0]}, {"job_handles": data["outcomes"][0]["job_handles"] * 3},
        {"result_handles": None}, {"stage": "NEW"}, {"session_id": "foreign-session"},
        {"context_handle": None}, {"job_handles": [], "result_handles": []}):
        changed = {**data, "outcomes": [{**data["outcomes"][0], **changes}]}
        with pytest.raises(InvalidRequestError):
            ExperimentBatch.loads(json.dumps(changed))
    for invalid in ("{", "null", '{"version":1,"version":1}', "NaN"):
        with pytest.raises(InvalidRequestError):
            ExperimentBatch.loads(invalid)


@pytest.mark.parametrize("timeout", [10**400, -(10**400)], ids=["too-large", "too-small"])
def test_unrepresentable_timeout_rejects_before_creating_a_client(timeout):
    calls = []
    with ExperimentRunner(lambda: calls.append("factory")) as runner:
        with pytest.raises(InvalidRequestError, match="timeout"):
            runner.run(experiments(1), timeout=timeout)
    assert not calls


@pytest.mark.parametrize("value", [10**400, -(10**400)], ids=["too-large", "too-small"])
@pytest.mark.parametrize("field", ["batch_elapsed", "elapsed", "cleanup_elapsed"])
def test_manifest_large_integer_durations_raise_the_documented_input_error(monkeypatch, field, value):
    stack = Stack(monkeypatch)
    with ExperimentRunner(stack.factory) as runner:
        data = json.loads(runner.run(experiments(1)).dumps())
    if field == "batch_elapsed":
        data["elapsed"] = value
    else:
        data["outcomes"][0][field] = value
    with pytest.raises(InvalidRequestError, match="manifest"):
        ExperimentBatch.loads(json.dumps(data))


def test_missing_runtime_closed_client_and_secret_name_are_rejected_and_closed(monkeypatch):
    stack = Stack(monkeypatch)
    def missing_runtime():
        client = stack.factory()
        client.runtime_service_id = None
        return client
    with ExperimentRunner(missing_runtime) as runner:
        result = runner.run(experiments(1))
    assert result.outcomes[0].state == "setup_failed" and stack.clients[-1].transport._http.is_closed
    client = stack.factory()
    client.close()
    with ExperimentRunner(lambda: client) as runner:
        assert runner.run(experiments(1)).outcomes[0].state == "setup_failed"
    secret_name = Experiment("credential-secret", ObservationRequest("earth:Region"), OwnedContextPolicy())
    with ExperimentRunner(stack.factory) as runner:
        result = runner.run([secret_name])
    assert result.outcomes[0].state == "setup_failed" and "credential-secret" not in result.dumps()
    assert ExperimentBatch.loads(result.dumps()) == result


@pytest.mark.parametrize("kind", ["mapping", "urn", "binding"])
def test_wrong_result_identity_cannot_be_success(monkeypatch, kind):
    stack = None
    def waiter(job, timeout):
        if kind == "mapping":
            return {"not": "an observation"}
        payload = deepcopy(stack.payload)
        payload["urn"] = "foreign.c.42" if kind == "urn" else job.scope.id + ".42"
        result = job._decoder(payload)
        if kind == "binding":
            from klab_client import Context
            result._context = Context(job.client, {"id": "foreign.c", "serviceId": "home"})
        return result
    stack = Stack(monkeypatch, waiter)
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1))
    assert batch.outcomes[0].state == "result_invalid" and batch.outcomes[0].jobs


def test_setup_unknown_and_unconfirmed_release_preserve_truthful_states(monkeypatch):
    stack = Stack(monkeypatch)
    stack.setup_status = 503
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1, release="terminal"))
    assert batch.outcomes[0].state == "setup_unknown" and not stack.releases
    stack.setup_status, stack.release_accepted = 200, False
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1, release="terminal"))
    assert batch.outcomes[0].cleanup_errors == ("RemoteReleaseUnconfirmed", "RemoteReleaseUnconfirmed")
    assert batch.outcomes[0].state == "succeeded" and batch.outcomes[0].remote_release == "failed"


def test_manifest_rejects_cross_context_handles_and_duplicate_experiment_names(monkeypatch):
    stack = Stack(monkeypatch)
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(2))
        with pytest.raises(InvalidRequestError):
            runner.run([experiments(1)[0], experiments(1)[0]])
    data = json.loads(batch.dumps())
    data["outcomes"][0]["job_handles"] = data["outcomes"][1]["job_handles"]
    with pytest.raises(InvalidRequestError):
        ExperimentBatch.loads(json.dumps(data))
    data = json.loads(batch.dumps())
    data["outcomes"][1]["name"] = data["outcomes"][0]["name"]
    with pytest.raises(InvalidRequestError):
        ExperimentBatch.loads(json.dumps(data))


@pytest.mark.parametrize("failure", [RuntimeError("executor unavailable"), KeyboardInterrupt()])
def test_executor_start_failure_does_not_leave_runner_running(monkeypatch, failure):
    from klab_client import experiments as module
    def unavailable(**kwargs):
        raise failure
    monkeypatch.setattr(module, "ThreadPoolExecutor", unavailable)
    stack = Stack(monkeypatch)
    runner = ExperimentRunner(stack.factory)
    with pytest.raises(BatchInterrupted if isinstance(failure, KeyboardInterrupt) else RuntimeError):
        runner.run(experiments(1))
    assert not runner._running and not stack.clients


def test_connection_factory_ownership_including_unready_connection(monkeypatch):
    from klab_client import Connection
    from threading import RLock
    stack = Stack(monkeypatch)
    owners = []
    def factory():
        connection = object.__new__(Connection)
        connection._lock, connection._closed, connection._stale = RLock(), False, False
        connection._client, connection._pending, connection._selected = stack.factory(), {}, {}
        owners.append(connection)
        return connection
    with ExperimentRunner(factory) as runner:
        batch = runner.run(experiments(1))
    assert batch.outcomes[0].state == "succeeded" and owners[0]._closed
    def unready():
        owner = factory()
        owner._pending = {"runtime": "pending"}
        return owner
    with ExperimentRunner(unready) as runner:
        batch = runner.run(experiments(1))
    assert batch.outcomes[0].state == "setup_failed" and owners[-1]._closed
    def no_client():
        owner = object.__new__(Connection)
        owner._lock, owner._closed, owner._stale = RLock(), False, False
        owner._client, owner._pending, owner._selected = None, {}, {}
        owners.append(owner)
        return owner
    with ExperimentRunner(no_client) as runner:
        assert runner.run(experiments(1)).outcomes[0].state == "setup_failed"
    assert owners[-1]._closed


def test_runner_reuses_native_job_wait_without_starving_status_and_retrieve(monkeypatch):
    stack = Stack(monkeypatch)
    monkeypatch.setattr(Job, "result", NATIVE_RESULT)
    with ExperimentRunner(stack.factory) as runner:
        batch = runner.run(experiments(1), timeout=10)
    assert batch.outcomes[0].state == "succeeded"
    timed = [request for request in stack.calls if "/jobs/" in request.url.path]
    assert len(timed) == 2 and all(request.extensions["timeout"]["read"] > 1 for request in timed)


def test_native_job_local_cancel_checks_boundaries_and_retains_job(monkeypatch):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(200, json={"status": "WAITING"})
    with Client("https://runtime.invalid", http_transport=httpx.MockTransport(handler), runtime_service_id="home") as client:
        from klab_client import Context
        context = Context(client, {"id": "s.c", "serviceId": "home"})
        job = context.job(1)
        with pytest.raises(InvalidRequestError):
            NATIVE_RESULT(job, _cancelled=True)
        with pytest.raises(WaitCancelled) as failure:
            NATIVE_RESULT(job, _cancelled=lambda: True)
        assert failure.value.job is job and not calls
        monkeypatch.setattr("klab_client.client.time.sleep", lambda delay: None)
        with pytest.raises(WaitCancelled):
            NATIVE_RESULT(job, timeout=10, _cancelled=lambda: bool(calls))
        assert len(calls) == 1 and "/status/" in calls[0].url.path
