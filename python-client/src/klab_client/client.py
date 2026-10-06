"""Scientist-facing synchronous scope and job workflow."""
from __future__ import annotations

import math
import os
import re
import time
import uuid
from dataclasses import dataclass
from typing import Any

from .api.runtime import ObservationImpl
from .api.scopes import ContextScopeImpl, SessionScopeImpl, UserScopeImpl
from .dto import (check_notifications, configuration_to_wire, decode_cell, object_payload,
                  observation_from_wire, observation_to_wire, required_text)
from .errors import (ConfigurationError, InvalidRequestError, JobCancelledError, JobFailedError,
                     JobUnavailableError, ProtocolError, TransportError,
                     UnsupportedOperationError, WaitTimeout)
from .transport import Endpoint, Transport

_TOKEN = re.compile(r"^[^.\s#]+(?:\.[^.\s#]+)?(?:\.[1-9][0-9]*)*(?:#[1-9][0-9]*)?$")


def scope_token(scope) -> str | None:
    if scope is None:
        return None
    if hasattr(scope, "get_context_id"):
        token = scope.get_context_id()
    elif hasattr(scope, "get_session_id"):
        token = scope.get_session_id()
    elif isinstance(scope, str):
        token = scope
    elif isinstance(scope, UserScopeImpl):
        return None
    else:
        token = getattr(scope, "id", None)
    if not isinstance(token, str) or not _TOKEN.fullmatch(token):
        raise InvalidRequestError("Scope must follow session.context[.observationId...][#observerId] grammar")
    return token


@dataclass(frozen=True)
class JobStatus:
    status: str
    stack_trace: str | None = None


@dataclass(frozen=True)
class ScientificData:
    """Explicit indexed cells; no guessed array dimensions or coordinates."""
    observation_id: int
    observable: str
    units: str | None
    geometry: Any
    offsets: tuple[int, ...]
    curve: str
    slice: dict
    values: tuple[Any, ...]
    text: tuple[str, ...]
    source_observable: str | None = None
    source_units: str | None = None


class Job:
    def __init__(self, client, id: int, scope, *, service="runtime", decoder=None):
        if type(id) is not int or id <= 0:
            raise ProtocolError("Submission must return a positive integer job ID")
        self.client, self.id, self.scope, self.service = client, id, scope, service
        self._decoder = decoder
        self.cancellation_requested = False

    def __repr__(self):
        return f"Job(id={self.id}, service={self.service!r}, scope={scope_token(self.scope)!r})"

    def status(self, *, timeout=None, _deadline=None) -> JobStatus:
        payload = object_payload(self.client.transport.request(
            self.service, "GET", f"/jobs/status/{self.id}", scope=scope_token(self.scope),
            service_id=self.client.runtime_service_id, timeout=timeout, deadline=_deadline), "job status")
        state = payload.get("status")
        if state not in {"WAITING", "STARTED", "CHANGED", "FINISHED", "ABORTED", "INTERRUPTED", "EMPTY"}:
            raise ProtocolError(f"Job {self.id} returned an unknown/null status")
        trace = payload.get("stackTrace")
        if trace is not None and not isinstance(trace, str):
            raise ProtocolError("Job stackTrace must be text or null")
        return JobStatus(state, self.client.transport.redact(trace) if trace else None)

    def cancel(self) -> bool:
        accepted = self.client.transport.request(self.service, "GET", f"/jobs/cancel/{self.id}",
                                                 scope=scope_token(self.scope),
                                                 service_id=self.client.runtime_service_id, ambiguous=True)
        if type(accepted) is not bool:
            raise ProtocolError("Cancellation must return a boolean")
        self.cancellation_requested = self.cancellation_requested or accepted
        return accepted

    def result(self, timeout: float | None = None):
        if timeout is not None and (not math.isfinite(timeout) or timeout < 0):
            raise InvalidRequestError("Wait timeout must be finite and nonnegative")
        deadline = time.monotonic() + timeout if timeout is not None else None

        def remaining():
            if deadline is None:
                return None
            value = deadline - time.monotonic()
            if value <= 0:
                raise WaitTimeout(self)
            return min(value, self.client.transport.timeout)

        while True:
            try:
                status = self.status(timeout=remaining(), _deadline=deadline)
                if status.status == "FINISHED":
                    payload = self.client.transport.request(
                        self.service, "GET", f"/jobs/retrieve/{self.id}",
                        scope=scope_token(self.scope), service_id=self.client.runtime_service_id,
                        timeout=remaining(), deadline=deadline)
                    payload = object_payload(payload, "job result")
                    try:
                        check_notifications(payload, self.client.transport.redact)
                    except Exception as error:
                        from .errors import ServerError
                        if isinstance(error, ServerError):
                            raise JobFailedError(self.id, str(error)) from None
                        raise
                    if payload.get("empty"):
                        raise JobFailedError(self.id, "Server returned an empty scientific result")
                    result = self._decoder(payload) if self._decoder else payload
                    remaining()
                    return result
                if status.status == "ABORTED":
                    raise JobFailedError(self.id, status.stack_trace or "Server computation failed")
                if status.status == "INTERRUPTED":
                    raise JobCancelledError(self.id, "Server confirmed interruption")
                if status.status == "EMPTY":
                    raise JobUnavailableError(self.id, "Job unavailable or expired; this is not confirmed cancellation")
            except TransportError:
                if deadline is not None and time.monotonic() >= deadline:
                    raise WaitTimeout(self) from None
                raise
            delay = self.client.transport.poll_interval
            if deadline is not None:
                delay = min(delay, max(0, deadline - time.monotonic()))
            time.sleep(delay)

    wait = result


class Session(SessionScopeImpl):
    def __init__(self, client, id):
        super().__init__(user_id=client.agent_name or "", session_id=id)
        self.client = client

    @property
    def id(self):
        return self.session_id

    def create_context(self, *, configuration: dict | None = None, name="Python context"):
        payload = self.client.transport.request(
            "runtime", "POST", "/createContext", scope=self.id,
            json=self.client._scope_request({"name": name, "persistence": "ONE_OFF", **(configuration or {})}),
            ambiguous=True)
        return self.client._context(payload, owned=True)

    def release(self):
        return self.client.runtime.release_session(self)


class Context(ContextScopeImpl):
    def __init__(self, client, configuration, *, owned=False, token=None):
        id = required_text(configuration, "id")
        if len(id.split(".")) != 2 or not _TOKEN.fullmatch(id):
            raise ProtocolError("Configuration ID must be session.context")
        super().__init__(user_id=client.agent_name or "", session_id=id.split(".")[0], context_id=token or id)
        self.client, self.configuration, self.owned = client, configuration, owned

    @property
    def id(self):
        return self.configuration["id"]

    def within(self, observation):
        id = observation.id if isinstance(observation, ObservationImpl) else observation
        if type(id) is not int or id <= 0:
            raise InvalidRequestError("Focus requires a positive committed observation ID")
        if isinstance(observation, ObservationImpl) and observation._context is not None and observation._context.id != self.id:
            raise InvalidRequestError("Focused observation belongs to another context")
        base, marker, observer = scope_token(self).partition("#")
        token = base + f".{id}" + (marker + observer if marker else "")
        return Context(self.client, self.configuration, owned=self.owned, token=token)

    def submit(self, observation, *, resolution_constraints=()):
        return self.client._submit(observation, self, resolution_constraints)

    def job(self, id):
        """Resume a saved job in its original context, including focus path."""
        return Job(self.client, id, self, decoder=lambda p: observation_from_wire(p, self))

    def release(self):
        return self.client.runtime.release_context(self)

    def fetch_data(self, observation, offsets, *, curve, slice, semantics):
        bound = observation._context
        if bound is None or bound.id != self.id or (
                bound.client.transport.endpoint("runtime").url.rstrip("/")
                != self.client.transport.endpoint("runtime").url.rstrip("/")):
            raise InvalidRequestError("Scientific reads require an observation bound to this runtime and context")
        offsets = tuple(offsets)
        if not offsets or len(offsets) > 256 or any(type(i) is not int or i < 0 for i in offsets):
            raise InvalidRequestError("Read 1–256 explicit nonnegative cell offsets per call")
        curves = {"UNSPECIFIED", "D1_LINEAR", "D2_XY", "D2_YX", "D2_XInvY", "D3_XYZ", "D3_ZYX", "D2_HILBERT", "D3_HILBERT"}
        if curve not in curves:
            raise InvalidRequestError("Unknown fill curve")
        source_id = observation.id
        if source_id == 0:
            raise UnsupportedOperationError("Query-result geometry/consumer bindings need an explicit StorageScan.Point; fetch_data supports committed native observations")
        if observation.metadata.get("im:storage-binding-source"):
            raise UnsupportedOperationError("Detached consumer bindings require an explicit StorageScan.Point")
        if type(source_id) is not int or source_id <= 0:
            raise ProtocolError("Storage reads require a positive source ID")
        if slice is None:
            if observation.geometry:
                for dimension in observation.geometry.raw.get("dimensions", []):
                    if dimension.get("type") == "TIME" and math.prod(dimension.get("shape") or [1]) > 1:
                        raise InvalidRequestError("Temporal observations require an explicit StorageScan.Slice")
            slice = {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0}
        if (not isinstance(slice, dict) or slice.get("type") not in {
                "INITIALIZATION", "TEMPORAL_TRANSITION", "EVENT"}
                or not isinstance(slice.get("key"), str)
                or not slice["key"] or any(type(slice.get(k)) is not int for k in ("start", "end"))):
            raise InvalidRequestError("Slice requires type, nonblank key, integer start/end")
        if semantics is not None and (not isinstance(semantics, dict) or any(
                not isinstance(semantics.get(k), str) for k in
                ("observable", "unit", "range", "currency", "contextualDimensions"))):
            raise InvalidRequestError("Semantics requires explicit observable/unit/range/currency/contextualDimensions strings")
        texts = tuple(self.client.transport.request(
            "runtime", "POST", "/api/v1/observation/value", scope=scope_token(self),
            json={"sourceId": source_id, "slice": slice, "curve": curve, "geometry": None,
                  "semantics": semantics, "offset": offset, "rate": None}, response="text") for offset in offsets)
        artifact_type = observation.observable.raw.get("artifactType")
        values = tuple(decode_cell(text, artifact_type) for text in texts)
        return ScientificData(source_id, semantics["observable"] if semantics else observation.observable.raw["urn"],
                               semantics.get("unit") if semantics else observation.units,
                              observation.geometry, offsets, curve, slice, values, texts,
                              observation.observable.raw["urn"], observation.units)


class Client:
    def __init__(self, runtime_url: str, credential: str | None = None, *,
                 reasoner: Endpoint | None = None, resources: Endpoint | None = None,
                 resolver: Endpoint | None = None, agent_name: str | None = None,
                 runtime_service_id: str | None = None, service_ids=(), timeout=30,
                 poll_interval=.5, verify=True, http_transport=None):
        from .api.services import ReasonerImpl, ResourcesServiceImpl, RuntimeServiceImpl, ResolverImpl
        endpoints = {"runtime": Endpoint(runtime_url, credential)}
        endpoints.update({k: v for k, v in {"reasoner": reasoner, "resources": resources, "resolver": resolver}.items() if v})
        self.transport = Transport(endpoints, timeout=timeout, poll_interval=poll_interval,
                                   verify=verify, http_transport=http_transport)
        self.agent_name, self.runtime_service_id = agent_name, runtime_service_id
        self.service_ids = tuple(service_ids)
        self.runtime, self.reasoner = RuntimeServiceImpl(self), ReasonerImpl(self)
        self.resources, self.resolver = ResourcesServiceImpl(self), ResolverImpl(self)

    @classmethod
    def from_env(cls):
        runtime = os.environ.get("KLAB_RUNTIME_URL")
        if not runtime:
            raise ConfigurationError("Set KLAB_RUNTIME_URL to the configured deployment")
        peers = {service: Endpoint(os.environ[f"KLAB_{service.upper()}_URL"],
                                  os.environ.get(f"KLAB_{service.upper()}_TOKEN"))
                 for service in ("reasoner", "resources", "resolver")
                 if os.environ.get(f"KLAB_{service.upper()}_URL")}
        try:
            timeout = float(os.environ.get("KLAB_TIMEOUT", "30"))
            poll_interval = float(os.environ.get("KLAB_POLL_INTERVAL", ".5"))
        except ValueError:
            raise ConfigurationError("KLAB_TIMEOUT and KLAB_POLL_INTERVAL must be numbers") from None
        return cls(runtime, os.environ.get("KLAB_RUNTIME_TOKEN"), **peers,
                   agent_name=os.environ.get("KLAB_AGENT_NAME"),
                   runtime_service_id=os.environ.get("KLAB_RUNTIME_SERVICE_ID"),
                   service_ids=tuple(filter(None, os.environ.get("KLAB_SERVICE_IDS", "").split(","))),
                   timeout=timeout, poll_interval=poll_interval,
                   verify=os.environ.get("KLAB_CA_BUNDLE") or True)

    def _scope_request(self, configuration):
        return {"configuration": configuration_to_wire(configuration), "serviceIds": list(self.service_ids)}

    def create_session(self, *, name="Python session"):
        requested_id = uuid.uuid4().hex
        id = self.transport.request("runtime", "POST", "/createSession",
                                    json=self._scope_request({"id": requested_id, "name": name}), response="text", ambiguous=True)
        if not id or "." in id or not _TOKEN.fullmatch(id):
            raise ProtocolError("createSession returned an invalid session ID")
        return Session(self, id)

    def _context(self, payload, *, owned=False):
        payload = object_payload(payload, "context configuration")
        check_notifications(payload, self.transport.redact)
        if payload.get("empty"):
            raise ProtocolError("Server returned an empty context configuration")
        return Context(self, payload, owned=owned)

    def attach_context(self, id: str, *, configuration=None):
        if not isinstance(id, str) or len(id.split(".")) != 2 or not _TOKEN.fullmatch(id):
            raise InvalidRequestError("Attach requires a root session.context ID")
        return self._context(self.transport.request("runtime", "POST", "/api/v1/connect",
                             json=self._scope_request({**(configuration or {}), "id": id}), ambiguous=True))

    def _submit(self, observation, scope, constraints=(), *, service="runtime", decoder=None):
        if service == "runtime" and not self.agent_name:
            raise ConfigurationError("Set agent_name/KLAB_AGENT_NAME to the authorized user's existing provenance agent name")
        envelope = {"observation": observation_to_wire(observation),
                    "agentName": self.agent_name, "resolutionConstraints": list(constraints)}
        id = self.transport.request(service, "POST", "/api/v1/submit" if service == "runtime" else "/api/v1/resolve",
                                    json=envelope, scope=scope_token(scope), service_id=self.runtime_service_id,
                                    ambiguous=True)
        token = scope_token(scope)
        context = scope if isinstance(scope, Context) else Context(
            self, {"id": ".".join(token.split("#")[0].split(".")[:2])}, token=token)
        return Job(self, id, scope, service=service,
                   decoder=decoder or (lambda p: observation_from_wire(p, context)))

    def close(self):
        """Close local connections only. Remote scopes require explicit release."""
        self.transport.close()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()
