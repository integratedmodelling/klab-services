"""Synchronous bounded independent experiments over existing Connection/Client/Job APIs."""
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field, replace
from itertools import islice
import json
import math
import re
from threading import Event, Lock
import time

from ._inspection import bounds, html_summary
from .api.runtime import ObservationImpl
from .client import Client
from .connection import Connection
from .errors import (AuthenticationError, AuthorizationError, ConfigurationError, InvalidRequestError,
    JobCancelledError, JobFailedError, JobUnavailableError, KlabError, ProtocolError,
    SubmissionOutcomeUnknown, WaitTimeout, WaitCancelled)
from .requests import ContextOptions, ObservationRequest
from .handles import Handle, _invalid_constant, _unique

STATES = {"queued", "running", "succeeded", "job_unavailable", "job_cancelled", "job_failed", "setup_unknown",
    "setup_failed", "submission_unknown", "submission_rejected", "result_invalid", "processing_failed", "wait_error",
    "wait_cancelled", "wait_timed_out", "not_submitted_cancelled", "not_submitted_deadline", "wait_interrupted"}


@dataclass(frozen=True)
class OwnedContextPolicy:
    """Always create an independent owned session/context. Remote disposal is opt-in."""
    options: ContextOptions = field(default_factory=ContextOptions)
    release: str = "retain"

    def __post_init__(self):
        if not isinstance(self.options, ContextOptions) or self.release not in ("retain", "terminal"):
            raise InvalidRequestError("Use ContextOptions and owned release policy retain or terminal")


@dataclass(frozen=True)
class Experiment:
    name: str
    request: ObservationRequest
    context_policy: OwnedContextPolicy
    context_request: ObservationRequest | None = None
    variation: str | None = None

    def __post_init__(self):
        if (not isinstance(self.name, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]{0,79}", self.name)
                or not isinstance(self.request, ObservationRequest) or not isinstance(self.context_policy, OwnedContextPolicy)
                or self.context_request is not None and not isinstance(self.context_request, ObservationRequest)
                or self.variation is not None and (not isinstance(self.variation, str) or len(self.variation) > 128)):
            raise InvalidRequestError("Use a short unique experiment name, typed request and explicit owned context policy")


@dataclass(frozen=True)
class ExperimentOutcome:
    name: str
    state: str = "queued"
    stage: str = "setup"
    session_id: str | None = None
    context_handle: object = None
    job_handles: tuple = ()
    result_handles: tuple = ()
    cancellation_requested: bool = False
    cancellation_accepted: bool | None = None
    terminal_outcome: str | None = None
    error_type: str | None = None
    cleanup_errors: tuple[str, ...] = ()
    remote_release: str = "retained"
    elapsed: float = 0
    cleanup_elapsed: float = 0
    jobs: tuple = field(default=(), repr=False, compare=False)
    results: tuple = field(default=(), repr=False, compare=False)
    processed: object = field(default=None, repr=False, compare=False)

    @property
    def job_handle(self):
        return self.job_handles[-1] if self.job_handles else None

    @property
    def result_handle(self):
        return self.result_handles[-1] if self.result_handles else None

    def to_dict(self):
        # No factory/provider/client, requests, raw metadata, exception messages or processor output.
        return {"name": self.name, "state": self.state, "stage": self.stage, "session_id": self.session_id,
            "context_handle": self.context_handle.to_dict() if self.context_handle else None,
            "job_handles": [handle.to_dict() for handle in self.job_handles],
            "result_handles": [handle.to_dict() for handle in self.result_handles],
            "cancellation_requested": self.cancellation_requested, "cancellation_accepted": self.cancellation_accepted,
            "terminal_outcome": self.terminal_outcome, "error_type": self.error_type,
            "cleanup_errors": list(self.cleanup_errors), "remote_release": self.remote_release,
            "elapsed": self.elapsed, "cleanup_elapsed": self.cleanup_elapsed}


@dataclass(frozen=True)
class ExperimentBatch:
    outcomes: tuple[ExperimentOutcome, ...]
    elapsed: float
    workers: int
    version: int = 1

    @property
    def counts(self):
        counts = {}
        for outcome in self.outcomes:
            counts[outcome.state] = counts.get(outcome.state, 0) + 1
        return counts

    def summary(self):
        return "\n".join(f"{outcome.name}: {outcome.state} ({outcome.stage})" for outcome in self.outcomes)

    def _repr_html_(self):
        return html_summary(self.summary())

    def dumps(self, *, max_bytes=1024 * 1024):
        bounds(max_bytes, 16 * 1024 * 1024, "Batch manifest byte budget")
        payload = {"version": self.version, "scope": "local batch manifest; not durable Job continuation",
            "elapsed": self.elapsed, "workers": self.workers, "counts": self.counts,
            "outcomes": [outcome.to_dict() for outcome in self.outcomes]}
        encoded = json.dumps(payload, sort_keys=True, allow_nan=False, separators=(",", ":"))
        if len(encoded.encode("utf-8")) > max_bytes:
            raise InvalidRequestError("Batch manifest exceeds the byte budget")
        self.loads(encoded, max_bytes=max_bytes)
        return encoded

    @classmethod
    def loads(cls, encoded, *, max_bytes=1024 * 1024):
        """Read an inspection manifest only; no network, ownership, resubmission or Job continuation."""
        bounds(max_bytes, 16 * 1024 * 1024, "Batch manifest byte budget")
        if not isinstance(encoded, str) or len(encoded.encode("utf-8")) > max_bytes:
            raise InvalidRequestError("Manifest must be bounded UTF-8 JSON text")
        try:
            data = json.loads(encoded, object_pairs_hook=_unique, parse_constant=_invalid_constant)
            if (not isinstance(data, dict) or data.get("version") != 1 or type(data["version"]) is not int
                    or set(data) != {"version", "scope", "elapsed", "workers", "counts", "outcomes"}
                    or data["scope"] != "local batch manifest; not durable Job continuation"):
                raise ValueError()
            bounds(data["workers"], 16, "Worker limit")
            if not isinstance(data["outcomes"], list) or not 1 <= len(data["outcomes"]) <= 256:
                raise ValueError()
            records = []
            fields = set(ExperimentOutcome("schema").to_dict())
            for item in data["outcomes"]:
                if (not isinstance(item, dict) or set(item) != fields or item["state"] not in STATES
                        or not isinstance(item["name"], str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]{0,79}", item["name"])
                        or type(item["cancellation_requested"]) is not bool
                        or item["cancellation_accepted"] is not None and type(item["cancellation_accepted"]) is not bool
                        or not isinstance(item["cleanup_errors"], list)
                        or len(item["cleanup_errors"]) > 3 or any(not isinstance(value, str) or not value.isidentifier() for value in item["cleanup_errors"])):
                    raise ValueError()
                for name in ("error_type", "session_id", "terminal_outcome", "stage", "remote_release"):
                    if item[name] is not None and (not isinstance(item[name], str) or len(item[name]) > 256):
                        raise ValueError()
                if (item["stage"] not in ("setup", "context_observation", "experiment", "processing")
                        or item["terminal_outcome"] not in (None, "job_result_returned", "failed", "interrupted")
                        or item["remote_release"] not in ("retained", "requested", "failed", "confirmed")
                        or item["error_type"] is not None and not item["error_type"].isidentifier()
                        or item["session_id"] is not None and not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", item["session_id"])):
                    raise ValueError()
                for name in ("elapsed", "cleanup_elapsed"):
                    if type(item[name]) not in (int, float) or not math.isfinite(item[name]) or item[name] < 0:
                        raise ValueError()
                parsed = {**item, "cleanup_errors": tuple(item["cleanup_errors"])}
                parsed["context_handle"] = Handle.loads(json.dumps(item["context_handle"])) if item["context_handle"] is not None else None
                for name, kind in (("job_handles", "job"), ("result_handles", "observation")):
                    if not isinstance(item[name], list) or len(item[name]) > 2:
                        raise ValueError()
                    parsed[name] = tuple(Handle.loads(json.dumps(value)) for value in item[name])
                    if any(handle.kind != kind for handle in parsed[name]):
                        raise ValueError()
                if parsed["context_handle"] is not None and parsed["context_handle"].kind != "context":
                    raise ValueError()
                origin = parsed["context_handle"]
                if origin is not None and any((handle.context_id, handle.runtime_id, handle.runtime_url) !=
                        (origin.context_id, origin.runtime_id, origin.runtime_url) for handle in (*parsed["job_handles"], *parsed["result_handles"])):
                    raise ValueError()
                if (origin is not None and item["session_id"] != origin.context_id.split(".")[0]
                        or origin is None and (parsed["job_handles"] or parsed["result_handles"])
                        or item["state"] == "succeeded" and (not parsed["job_handles"] or not parsed["result_handles"]
                            or item["terminal_outcome"] != "job_result_returned")):
                    raise ValueError()
                records.append(ExperimentOutcome(**parsed))
            if (type(data["elapsed"]) not in (int, float) or not math.isfinite(data["elapsed"]) or data["elapsed"] < 0
                    or len({item.name for item in records}) != len(records)):
                raise ValueError()
            result = cls(tuple(records), data["elapsed"], data["workers"])
            if (not isinstance(data["counts"], dict) or any(type(value) is not int or value <= 0 for value in data["counts"].values())
                    or result.counts != data["counts"]):
                raise ValueError()
            return result
        except (ValueError, TypeError, KeyError, OverflowError, KlabError):
            raise InvalidRequestError("Invalid or unsupported experiment manifest; no remote work was restored") from None


class BatchInterrupted(KeyboardInterrupt):
    """Local interruption; retained work is accessible through batch, never automatically replayed."""
    def __init__(self, batch):
        self.batch = batch
        super().__init__("Experiment wait interrupted; inspect retained batch handles")


class _Stopped(Exception):
    def __init__(self, state):
        self.state = state


class ExperimentRunner:
    """Factory creates a new owned Connection (preferred) or Client for each experiment.

    Secrets are acquired inside that factory through existing providers, never
    serialized into worker arguments. A processor may validate/export/analyze each
    result while its client is open. Closing this runner only stops local waits.
    """

    def __init__(self, connection_factory, *, workers=1, max_experiments=64, processor=None, _clock=time.monotonic):
        if not callable(connection_factory) or processor is not None and not callable(processor):
            raise InvalidRequestError("Supply a connection factory and an optional result processor")
        self.workers = bounds(workers, 16, "Worker limit")
        self.max_experiments = bounds(max_experiments, 256, "Experiment count budget")
        self.factory, self.processor, self._clock = connection_factory, processor, _clock
        self._lock, self._stop = Lock(), Event()
        self._closed = self._running = False
        self._outcomes, self._claimed = (), []
        self._started = None

    @property
    def outcomes(self):
        with self._lock:
            return self._outcomes

    def _update(self, index, **changes):
        with self._lock:
            records = list(self._outcomes)
            records[index] = replace(records[index], **changes)
            self._outcomes = tuple(records)
            return records[index]

    def _snapshot(self):
        return ExperimentBatch(self.outcomes, self._clock() - self._started, self.workers)

    def _claim(self, owner):
        if not isinstance(owner, (Connection, Client)):
            raise ConfigurationError("Factory must return a newly owned Connection or Client")
        # Check ownership before invoking Connection.client's readiness guard so
        # a newly returned, non-ready Connection is still closed by its worker.
        client = owner._client if isinstance(owner, Connection) else owner
        resources = ((owner,) if client is None else
                     (owner, client, client.transport, client.transport._http, client.transport._http._transport))
        with self._lock:
            if any(resource is claimed for resource in resources for claimed in self._claimed):
                raise ConfigurationError("Workers must not share Connection, Client or HTTP transport ownership")
            self._claimed.extend(resources)
        return client

    def _checkpoint(self, deadline, cancel, *, submitted=False):
        if self._stop.is_set() or cancel is not None and cancel.is_set():
            raise _Stopped("wait_cancelled" if submitted else "not_submitted_cancelled")
        remaining = deadline - self._clock()
        if remaining <= 0:
            raise _Stopped("wait_timed_out" if submitted else "not_submitted_deadline")
        return remaining

    def _wait(self, index, job, deadline, cancel, cancel_remote):
        try:
            remaining = self._checkpoint(deadline, cancel, submitted=True)
            # One existing Job wait, not repeatedly shortened timeouts that can
            # starve status/retrieval on a slower network. Cancellation is checked
            # at existing request boundaries; in-flight HTTP retains its timeout.
            return job.result(timeout=remaining, _cancelled=lambda: self._stop.is_set() or cancel is not None and cancel.is_set())
        except WaitTimeout:
            raise _Stopped("wait_timed_out") from None
        except (WaitCancelled, _Stopped) as interrupted:
            state = interrupted.state if isinstance(interrupted, _Stopped) else "wait_cancelled"
            if state == "wait_cancelled" and cancel_remote and not self._stop.is_set():
                self._update(index, cancellation_requested=True)
                try:
                    accepted = job.cancel()
                    self._update(index, cancellation_accepted=accepted)
                except KlabError as error:
                    self._update(index, error_type=type(error).__name__)
            raise _Stopped(state) from None

    def _one(self, index, experiment, timeout, cancel, cancel_remote):
        started = self._clock()
        deadline = started + timeout
        owner = client = session = context = current_job = None
        terminal = False
        self._update(index, state="running")
        try:
            self._checkpoint(deadline, cancel)
            candidate = self.factory()
            client = self._claim(candidate)
            owner = candidate  # Only a successful claim confers local close ownership.
            client = owner.client if isinstance(owner, Connection) else client
            if client.transport._http.is_closed or client._known_runtime_identity() is None:
                raise ConfigurationError("Factory must return a fresh open client with a configured home Runtime identity")
            if client.transport.redact(experiment.name) != experiment.name:
                self._update(index, name="redacted-experiment-" + str(index))
                raise ConfigurationError("Experiment identity contains configured credential material")
            self._checkpoint(deadline, cancel)
            session = client.create_session(name="experiment-" + experiment.name)
            self._update(index, session_id=session.id)
            self._checkpoint(deadline, cancel)
            context = session.create_context(options=experiment.context_policy.options)
            self._update(index, context_handle=context.to_handle())
            steps = (("context_observation", experiment.context_request), ("experiment", experiment.request))
            for stage, request in steps:
                if request is None:
                    continue
                self._checkpoint(deadline, cancel)
                self._update(index, stage=stage, terminal_outcome=None)
                terminal = False
                current_job = None
                job = context.observe(request)
                current_job = job
                previous = self.outcomes[index]
                self._update(index, jobs=previous.jobs + (job,))
                self._update(index, job_handles=previous.job_handles + (job.to_handle(),))
                result = self._wait(index, job, deadline, cancel, cancel_remote)
                terminal = True
                self._update(index, terminal_outcome="job_result_returned")
                previous = self.outcomes[index]
                self._update(index, results=previous.results + (result,))
                if not isinstance(result, ObservationImpl):
                    raise ProtocolError("Experiment Job must return a bound observation")
                if not result.urn.startswith((context.id + ".", context.id + ":")):
                    raise ProtocolError("Experiment result URN belongs to another context")
                handle = result.to_handle()
                expected = self.outcomes[index].context_handle
                if (handle.context_id, handle.runtime_id, handle.runtime_url) != (expected.context_id, expected.runtime_id, expected.runtime_url):
                    raise ProtocolError("Experiment result belongs to another context/Runtime")
                self._update(index, result_handles=previous.result_handles + (handle,))
                if stage == "context_observation":
                    context = context.within(result)
            self._update(index, stage="processing")
            self._checkpoint(deadline, cancel, submitted=True)
            if self.processor is not None:
                self._update(index, processed=self.processor(experiment, result))
            self._checkpoint(deadline, cancel, submitted=True)
            self._update(index, state="succeeded", terminal_outcome="job_result_returned")
        except _Stopped as stopped:
            self._update(index, state=stopped.state)
        except JobUnavailableError as error:
            self._update(index, state="job_unavailable", error_type=type(error).__name__)
        except JobCancelledError as error:
            terminal = True
            self._update(index, state="job_cancelled", terminal_outcome="interrupted", error_type=type(error).__name__)
        except JobFailedError as error:
            terminal = True
            self._update(index, state="job_failed", terminal_outcome="failed", error_type=type(error).__name__)
        except SubmissionOutcomeUnknown as error:
            state = "submission_unknown" if self.outcomes[index].stage != "setup" else "setup_unknown"
            self._update(index, state=state, error_type=type(error).__name__)
        except Exception as error:
            record = self.outcomes[index]
            if record.stage == "setup":
                state = "setup_failed"
            elif record.stage == "processing":
                state = "processing_failed"
            elif terminal:
                state = "result_invalid"
            elif current_job is not None:
                state = "wait_error"
            else:
                state = "submission_unknown"
            if current_job is None and record.stage != "setup" and isinstance(error, (AuthenticationError, AuthorizationError, InvalidRequestError)) and getattr(error, "status_code", None) in (400, 401, 403, 404, 422):
                state = "submission_rejected"
            self._update(index, state=state, error_type=type(error).__name__)
        except BaseException:
            self._update(index, state="wait_interrupted")
            raise
        finally:
            self._update(index, elapsed=self._clock() - started)
            cleanup_started = self._clock()
            errors = []
            if terminal and experiment.context_policy.release == "terminal" and self.outcomes[index].state in {
                    "succeeded", "job_failed", "job_cancelled", "processing_failed", "result_invalid"}:
                self._update(index, remote_release="requested")
                for owned in (context, session):
                    try:
                        if not owned.release():
                            errors.append("RemoteReleaseUnconfirmed")
                    except Exception as error:
                        errors.append(type(error).__name__)
                self._update(index, remote_release="failed" if errors else "confirmed")
            if owner is not None:
                try:
                    owner.close()
                except Exception as error:
                    errors.append(type(error).__name__)
            self._update(index, cleanup_errors=tuple(errors), cleanup_elapsed=self._clock() - cleanup_started)

    def run(self, experiments, *, timeout=120, cancel=None, cancel_remote=False):
        try:
            valid_timeout = type(timeout) in (int, float) and math.isfinite(timeout) and timeout > 0
        except OverflowError:
            valid_timeout = False
        if not valid_timeout or type(cancel_remote) is not bool:
            raise InvalidRequestError("Experiment timeout must be finite/positive; cancellation policy must be boolean")
        if cancel is not None and not isinstance(cancel, Event):
            raise InvalidRequestError("Use a threading.Event for caller cancellation")
        selected = tuple(islice(experiments, self.max_experiments + 1))
        if (not selected or len(selected) > self.max_experiments or any(not isinstance(item, Experiment) for item in selected)
                or len({item.name for item in selected}) != len(selected)):
            raise InvalidRequestError("Provide bounded typed experiments with unique names")
        with self._lock:
            if self._closed or self._running:
                raise InvalidRequestError("Runner is closed or already running")
            self._running = True
            self._stop.clear()
            self._outcomes = tuple(ExperimentOutcome(item.name) for item in selected)
            self._started = self._clock()
        executor = None
        try:
            executor = ThreadPoolExecutor(max_workers=self.workers, thread_name_prefix="klab-experiment")
            futures = [executor.submit(self._one, index, item, timeout, cancel, cancel_remote) for index, item in enumerate(selected)]
            for future in futures:
                future.result()
        except KeyboardInterrupt:
            self._stop.set()
            if executor is not None:
                executor.shutdown(wait=True)
            raise BatchInterrupted(self._snapshot()) from None
        except BaseException:
            self._stop.set()
            raise
        finally:
            if executor is not None:
                executor.shutdown(wait=True)
            with self._lock:
                self._running = False
                self._claimed.clear()
        return self._snapshot()

    def close(self):
        """Stop local waits only. Workers close their own connections at the next boundary."""
        with self._lock:
            self._closed = True
            self._stop.set()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.close()
