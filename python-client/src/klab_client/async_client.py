"""Native async observation workflows over the same checked Java contracts.

Objects and pure validators are shared with the sync client. All remote I/O is
awaited; no background loop, thread facade, automatic replay or remote disposal.
"""
import asyncio
import inspect
import math
import time
import uuid
from urllib.parse import quote

from ._async_transport import AsyncTransport
from .api.primitives import ContextInfo, KlabAsset, ResourceSet, ServiceCapabilities
from .api.services import _RemoteService, ResourcesServiceImpl, RuntimeServiceImpl
from .client import Client, Context, Job, Session, _TOKEN, scope_token
from .dto import (check_notifications, concept_from_wire, object_payload,
                  observable_from_wire, observation_from_wire, observation_to_wire,
                  required_text)
from .errors import (ConfigurationError, InvalidRequestError, MissingAssetError,
                     ProtocolError, SubmissionOutcomeUnknown, TransportError, UnsupportedOperationError, WaitTimeout)
from .requests import _prepare_observe
from .transport import Endpoint


def _require_async(client):
    if not inspect.iscoroutinefunction(getattr(getattr(client, "transport", None), "request", None)):
        raise ConfigurationError("Async scopes, jobs and services require a native async client")


class AsyncJob(Job):
    def __init__(self, client, *args, **kwargs):
        _require_async(client)
        super().__init__(client, *args, **kwargs)

    async def status(self, *, timeout=None, _deadline=None):
        self._check_binding()
        payload = await self.client.transport.request(self.service, "GET", f"/jobs/status/{self.id}",
            scope=scope_token(self.scope), service_id=self.client.runtime_service_id,
            timeout=timeout, deadline=_deadline)
        self._check_binding()
        return self._decode_status(payload)

    async def cancel(self):
        self._check_binding()
        accepted = await self.client.transport.request(self.service, "GET", f"/jobs/cancel/{self.id}",
            scope=scope_token(self.scope), service_id=self.client.runtime_service_id, ambiguous=True)
        try:
            self._check_binding()
        except ProtocolError:
            raise SubmissionOutcomeUnknown("Cancellation response binding changed; outcome unknown, do not replay") from None
        if type(accepted) is not bool:
            raise ProtocolError("Cancellation must return a boolean")
        self.cancellation_requested = self.cancellation_requested or accepted
        return accepted

    async def _retrieve_result(self, timeout, deadline):
        return await self.client.transport.request(self.service, "GET", f"/jobs/retrieve/{self.id}",
            scope=scope_token(self.scope), service_id=self.client.runtime_service_id,
            timeout=timeout, deadline=deadline)

    async def result(self, timeout=None):
        self._check_binding()
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

        try:
            while True:
                try:
                    status = await self.status(timeout=remaining(), _deadline=deadline)
                    if self._finished(status):
                        self._check_binding()
                        payload = await self._retrieve_result(remaining(), deadline)
                        self._check_binding()
                        result = self._decode_result(payload)
                        self._check_binding()
                        remaining()
                        return result
                except TransportError:
                    if deadline is not None and time.monotonic() >= deadline:
                        raise WaitTimeout(self) from None
                    raise
                delay = self.client.transport.poll_interval
                if deadline is not None:
                    delay = min(delay, max(0, deadline - time.monotonic()))
                await asyncio.sleep(delay)
        except asyncio.CancelledError as error:
            error.add_note(f"Local wait cancelled; remote Job {self.id} retained, not cancelled or resubmitted")
            raise

    wait = result


class AsyncSession(Session):
    def __init__(self, client, id):
        _require_async(client)
        super().__init__(client, id)

    async def create_context(self, *, configuration=None, name="Python context", options=None):
        payload = await self.client.transport.request("runtime", "POST", "/createContext", scope=self.id,
            json=self._context_request(configuration, name, options), ambiguous=True)
        return self.client._context(payload, owned=True)

    async def release(self):
        return await self.client.runtime.release_session(self)


class AsyncContext(Context):
    def __init__(self, client, configuration, *, owned=False, token=None):
        _require_async(client)
        super().__init__(client, configuration, owned=owned, token=token)

    def within(self, observation):
        view = super().within(observation)
        return AsyncContext(self.client, view.configuration, owned=view.owned, token=scope_token(view))

    async def submit(self, observation, *, resolution_constraints=()):
        return await self.client._submit(observation, self, resolution_constraints)

    async def observe(self, request, *, options=None):
        request, token, constraints = _prepare_observe(self, request, options)
        resolved = (await self.client.reasoner.resolve_observable(request.observable)
                    if request._observable_wire is None else None)
        selected = self if token == scope_token(self) else AsyncContext(
            self.client, self.configuration, owned=self.owned, token=token)
        return await selected.submit(request.to_wire(resolved), resolution_constraints=[item.to_wire() for item in constraints])

    def job(self, id):
        return AsyncJob(self.client, id, self, decoder=lambda p: observation_from_wire(p, self), result_kind="observation")

    async def release(self):
        return await self.client.runtime.release_context(self)

    async def fetch_data(self, observation, offsets, *, curve="UNSPECIFIED", slice=None, semantics=None):
        offsets, slice, semantics, origin = self._read_spec(observation, offsets, curve, slice, semantics)
        texts = []
        for offset in offsets:
            texts.append(await self.client.transport.request("runtime", "POST", "/api/v1/observation/value",
                scope=scope_token(self), json={"sourceId": observation.id, "slice": slice, "curve": curve,
                "geometry": None, "semantics": semantics, "offset": offset, "rate": None}, response="text"))
        return self._decode_read(observation, offsets, curve, slice, semantics, tuple(texts), origin)


class _AsyncService(_RemoteService):
    def __init__(self, client):
        _require_async(client)
        super().__init__(client)

    async def _request(self, method, route, scope=None, **kwargs):
        token = self._bound_scope_token(scope)
        return await self.client.transport.request(self.service, method, route,
            scope=token, service_id=self.client.runtime_service_id, **kwargs)

    async def capabilities(self, scope=None, *, max_response_bytes=None):
        payload = self._checked(await self._request("GET", "/public/capabilities", scope,
                               max_response_bytes=max_response_bytes), "capabilities")
        identity = required_text(payload, "serviceId")
        self.client._service_id_cache[self.service] = identity
        return ServiceCapabilities(identity, payload)


class AsyncReasoner(_AsyncService):
    service = "reasoner"

    async def resolve_concept(self, definition, scope=None):
        return concept_from_wire(self._checked(await self._request("POST", "/api/v1/resolve/concept",
                                  scope, text=definition), "concept"))

    async def resolve_observable(self, definition, scope=None):
        payload = self._checked(await self._request("POST", "/api/v1/resolve/observable", scope, text=definition), "observable")
        check_notifications(payload.get("semantics", {}), self.client.transport.redact)
        observable = observable_from_wire(payload)
        if (payload.get("artifactType") == "VOID" or observable.semantics.urn == "owl:Nothing"
                or "NOTHING" in payload["semantics"].get("type", [])):
            raise MissingAssetError("Semantic definition did not resolve in the configured worldview; inspect the loaded projects")
        return observable


class AsyncResources(_AsyncService):
    service = "resources"
    _knowledge_class = staticmethod(ResourcesServiceImpl._knowledge_class)

    async def retrieve(self, urn, asset_class="RESOURCE", scope=None, *, max_response_bytes=None):
        payload = await self._request("GET", f"/api/v1/retrieve/{ResourcesServiceImpl._retrieval_knowledge_class(asset_class)}/{quote(urn, safe='')}",
                                      scope, max_response_bytes=max_response_bytes)
        if payload is None:
            raise MissingAssetError("Resource service returned no asset")
        payload = self._checked(payload, "asset")
        return KlabAsset(required_text(payload, "urn"), payload)

    async def list(self, knowledge_class="RESOURCE", scope=None, *, max_response_bytes=None):
        payload = await self._request("GET", f"/api/v1/list/{self._knowledge_class(knowledge_class)}", scope,
                                      max_response_bytes=max_response_bytes)
        if not isinstance(payload, list):
            raise ProtocolError("Asset list must be a JSON array")
        return [KlabAsset(required_text(self._checked(p, "asset"), "urn"), p) for p in payload]

    async def resolve(self, urn, scope=None, *, knowledge_class="RESOURCE", max_response_bytes=None):
        payload = self._checked(await self._request("GET", f"/api/v1/resolve/{self._knowledge_class(knowledge_class)}/{quote(urn, safe='')}",
                                scope, max_response_bytes=max_response_bytes), "resource set")
        results = payload.get("results")
        if not isinstance(results, list):
            raise ProtocolError("ResourceSet results must be a list")
        return ResourceSet([required_text(object_payload(p, "resource descriptor"), "resourceUrn") for p in results], payload)

    async def contextualize(self, resource, observation, geometry, scope, *, start_time=0, end_time=0, input_data=None):
        from .provider_data import _async_contextualize
        return await _async_contextualize(self, resource, observation, geometry, scope,
            start_time=start_time, end_time=end_time, input_data=input_data)


class AsyncRuntime(_AsyncService, RuntimeServiceImpl):
    service = "runtime"

    async def submit(self, observation, scope):
        return await self.client._submit(observation, scope)

    async def get_context_info(self, scope=None):
        payload = await self._request("GET", "/api/v1/contexts", scope)
        if not isinstance(payload, list):
            raise ProtocolError("Context info must be a JSON array")
        result = []
        for item in payload:
            item = self._checked(item, "context info")
            id = required_text(object_payload(item.get("configuration"), "configuration"), "id")
            result.append(ContextInfo(id.split(".")[0], [id], item))
        return result

    async def connect_context(self, configuration, user_scope=None):
        return self.client._context(await self._request("POST", "/api/v1/connect", user_scope,
                                   json=self.client._scope_request(configuration), ambiguous=True))

    async def _release(self, route, scope):
        result = await self._request("GET", route, scope, ambiguous=True)
        if type(result) is not bool:
            raise ProtocolError("Release must return a boolean")
        return result

    async def release_session(self, scope):
        return await RuntimeServiceImpl.release_session(self, scope)

    async def release_context(self, scope):
        return await RuntimeServiceImpl.release_context(self, scope)

    async def query_knowledge_graph(self, query, scope):
        if not isinstance(query, dict):
            raise UnsupportedOperationError("Knowledge graph queries require a structured wire DTO, not query text")
        payload = await self._request("POST", "/api/v1/query", scope, json=query)
        if not isinstance(payload, list):
            raise ProtocolError("Knowledge graph query must return an array")
        return [self._checked(p, "graph asset") for p in payload]


class AsyncResolver(_AsyncService):
    service = "resolver"

    async def resolve(self, observation, context_scope):
        return await self.client._submit(observation, context_scope, service="resolver",
            decoder=lambda p: object_payload(p, "dataflow"), result_kind="dataflow")

    async def encode_dataflow(self, dataflow):
        raise UnsupportedOperationError("Dataflow encoding has no implemented Java client contract")

    async def submit_resource(self, observation, context_scope):
        return self._checked(await self._request("POST", "/api/v1/resource", context_scope,
                             json=observation_to_wire(observation), ambiguous=True), "resource")

    async def get_submitted_resources(self, scope):
        payload = await self._request("GET", "/api/v1/resources", scope)
        if not isinstance(payload, list):
            raise ProtocolError("Submitted resources must be an array")
        return [self._checked(p, "resource") for p in payload]


class AsyncClient:
    _context_type = AsyncContext
    _scope_request = Client._scope_request
    _context = Client._context
    _known_runtime_identity = Client._known_runtime_identity
    _validate_context_binding = Client._validate_context_binding
    _submission = Client._submission

    @classmethod
    def from_env(cls):
        return Client.from_env.__func__(cls)

    def __init__(self, runtime_url, credential=None, *, reasoner=None, resources=None, resolver=None,
                 agent_name=None, runtime_service_id=None, service_ids=(), timeout=30,
                 poll_interval=.5, verify=True, http_transport=None, max_concurrency=8):
        endpoints = {"runtime": Endpoint(runtime_url, credential)}
        endpoints.update({k: v for k, v in {"reasoner": reasoner, "resources": resources, "resolver": resolver}.items() if v})
        self.transport = AsyncTransport(endpoints, timeout=timeout, poll_interval=poll_interval, verify=verify,
                                        http_transport=http_transport, max_concurrency=max_concurrency)
        self.agent_name, self.runtime_service_id = agent_name, runtime_service_id
        self.service_ids, self._service_id_cache = tuple(service_ids), {}
        self.runtime, self.reasoner = AsyncRuntime(self), AsyncReasoner(self)
        self.resources, self.resolver = AsyncResources(self), AsyncResolver(self)

    async def initialize_user_scope(self, *, email_address=None, local_federation=False):
        services = []
        for name in self.transport.endpoints:
            capability = await getattr(self, name).capabilities()
            status = object_payload(await self.transport.request(name, "GET", "/public/status"), "service status")
            services.append({"id": capability.service_id, "type": name.upper(),
                             "url": self.transport.endpoint(name).url, "status": status})
        runtime_id = next(item["id"] for item in services if item["type"] == "RUNTIME")
        if self.runtime_service_id is not None and self.runtime_service_id != runtime_id:
            raise ConfigurationError("Configured home runtime ID differs from its actual capabilities")
        for name in self.transport.endpoints:
            accepted = await self.transport.request(name, "POST", "/notifyUserScope", ambiguous=True,
                json={"services": services, "emailAddress": email_address, "localFederation": local_federation})
            if accepted is not True:
                raise ProtocolError(f"User scope advertisement not accepted by {name}")
        self.service_ids = tuple(item["id"] for item in services)
        self.runtime_service_id = runtime_id
        return tuple({"type": item["type"], "id": item["id"], "operational": item["status"].get("operational")} for item in services)

    async def create_session(self, *, name="Python session"):
        id = await self.transport.request("runtime", "POST", "/createSession",
            json=self._scope_request({"id": uuid.uuid4().hex, "name": name}), response="text", ambiguous=True)
        if not id or "." in id or not _TOKEN.fullmatch(id):
            raise ProtocolError("createSession returned an invalid session ID")
        return AsyncSession(self, id)

    async def attach_context(self, id, *, configuration=None):
        if not isinstance(id, str) or len(id.split(".")) != 2 or not _TOKEN.fullmatch(id):
            raise InvalidRequestError("Attach requires a root session.context ID")
        return self._context(await self.transport.request("runtime", "POST", "/api/v1/connect",
            json=self._scope_request({**(configuration or {}), "id": id}), ambiguous=True))

    async def _submit(self, observation, scope, constraints=(), *, service="runtime", decoder=None, result_kind=None):
        context, envelope = self._submission(observation, scope, constraints, service)
        id = await self.transport.request(service, "POST", "/api/v1/submit" if service == "runtime" else "/api/v1/resolve",
            json=envelope, scope=scope_token(scope), service_id=self.runtime_service_id, ambiguous=True)
        return AsyncJob(self, id, context, service=service,
            decoder=decoder or (lambda p: observation_from_wire(p, context)),
            result_kind="observation" if decoder is None else result_kind)

    async def restore_handle(self, reference, *, allow_relocation=False):
        from .handles import (Handle, HandleFormatError, HandleIdentityError,
                              _decode_observation, _no_credentials, _selection, _url)
        from .errors import JobUnavailableError

        if not isinstance(reference, Handle) or type(allow_relocation) is not bool:
            raise HandleFormatError("Restore requires a validated Handle and boolean relocation policy")
        _no_credentials(reference, self)
        if _url(self.transport.endpoint("runtime").url) != reference.runtime_url and not allow_relocation:
            raise HandleIdentityError("Configured Runtime origin/base path differs from the saved reference")
        if (reference.kind == "job" and not allow_relocation
                and _url(self.transport.endpoint(reference.service).url) != reference.service_url):
            raise HandleIdentityError("Configured job-service origin/base path differs from the saved reference")
        runtime_id = (await self.runtime.capabilities()).service_id
        if runtime_id != reference.runtime_id or self.runtime_service_id not in (None, runtime_id):
            raise HandleIdentityError("Authenticated/configured home Runtime identity differs from the saved reference")
        if reference.kind == "job" and reference.service == "resolver":
            if (await self.resolver.capabilities()).service_id != reference.service_id:
                raise HandleIdentityError("Executing peer identity differs from the saved reference")
        self.runtime_service_id = runtime_id
        root = await self.attach_context(reference.context_id)
        if root.id != reference.context_id:
            raise ProtocolError("Attach returned a different root context")
        if root.configuration.get("serviceId") not in (None, runtime_id):
            raise HandleIdentityError("Attached context belongs to another Runtime")

        async def lookup(observation_id):
            rows = await self.runtime.query_knowledge_graph(
                {"type": "QUERY", "resultType": "OBSERVATION", "id": observation_id, "limit": 2}, root)
            if not rows:
                raise MissingAssetError("Saved observation/selection is absent or inaccessible in this context")
            if len(rows) != 1:
                raise ProtocolError("Observation lookup returned a nonunique asset")
            return _decode_observation(root, rows[0], observation_id)

        focus, observer = _selection(reference.scope, reference.context_id)
        for observation_id in focus:
            await lookup(observation_id)
        if observer is not None:
            selected = await lookup(observer)
            if "AGENT" not in selected.observable.semantics.raw.get("type", []):
                raise HandleIdentityError("Saved observer selection is not a persisted agent")
        context = AsyncContext(self, root.configuration, token=reference.scope, owned=False)
        if reference.kind == "context":
            return context
        if reference.kind == "observation":
            observation = await lookup(reference.observation_id)
            observation._context = context
            return observation
        decoder = None
        if reference.result_kind == "observation":
            decoder = lambda payload: _decode_observation(context, payload)
        elif reference.result_kind == "dataflow":
            decoder = lambda payload: object_payload(payload, "dataflow")
        job = AsyncJob(self, reference.job_id, context, service=reference.service,
            decoder=decoder, result_kind=reference.result_kind)
        if (await job.status()).status == "EMPTY":
            raise JobUnavailableError(job.id, "Saved job is unavailable or expired; no work was recreated")
        return job

    async def export_formats(self, observation):
        from ._async_exports import export_formats
        return await export_formats(self, observation)

    async def export_to_file(self, observation, path, *, media_type, overwrite=False, max_bytes=None, slice=None):
        from ._async_exports import export_to_file
        return await export_to_file(self, observation, path, media_type=media_type,
            overwrite=overwrite, max_bytes=max_bytes, slice=slice)

    async def aclose(self):
        await self.transport.aclose()

    close = aclose

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_value, traceback):
        await self.transport.__aexit__(exc_type, exc_value, traceback)

    def __enter__(self):
        raise InvalidRequestError("Use async with AsyncClient, not a synchronous context manager")

    def __exit__(self, *args):
        raise InvalidRequestError("Use async with AsyncClient, not a synchronous context manager")
