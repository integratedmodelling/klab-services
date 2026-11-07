"""Source-defined binary Resources contextualization, not a worker lease protocol.

Raw DataRequest/Instance Avro datum and binary Job retrieval. Synthetic codec
acceptance does not establish native provider/scientific execution compatibility.
"""
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import Mapping, Any

from ._inspection import _reject_credential_leaves
from ._provider_avro import _encode, _decode, _INSTANCE, _REQUEST, MAX_BYTES
from .client import Job, Context, scope_token
from .async_client import AsyncJob
from .dto import check_notifications
from .errors import ConfigurationError, InvalidRequestError, JobFailedError, ProtocolError, ServerError, SubmissionOutcomeUnknown


def _freeze(value):
    if isinstance(value, dict): return MappingProxyType({key: _freeze(item) for key, item in value.items()})
    if isinstance(value, list): return tuple(_freeze(item) for item in value)
    return value


@dataclass(frozen=True)
class ProviderData:
    """Immutable exact bounded binary Instance, not an indexed ScientificData grid.

    `fields` retains native channel names, typed values, metadata and nested
    instances. IEEE NaN/Infinity are preserved as raw provider values, not assigned
    guessed nodata meanings. Neither dimensions nor units/coordinates are inferred.
    """
    payload: bytes = field(repr=False)
    fields: Mapping[str, Any] = field(init=False, repr=False)

    def __post_init__(self):
        object.__setattr__(self, "fields", _freeze(_decode(_INSTANCE, self.payload)))

    @classmethod
    def from_fields(cls, fields):
        """Encode exact native Instance fields, with no inferred defaults or axes."""
        return cls(_encode(_INSTANCE, fields))


def _result(raw, job):
    value = _decode(_INSTANCE, raw)
    try:
        check_notifications(value, job.client.transport.redact)
    except ServerError as error:
        raise JobFailedError(job.id, str(error)) from None
    try:
        _reject_credential_leaves(value, job.client.transport.redact, "Provider payload contains credential material")
    except InvalidRequestError:
        raise ProtocolError("Provider payload contains credential material") from None
    return ProviderData(raw)


class ProviderDataJob(Job):
    def _retrieve_result(self, timeout, deadline):
        return self.client.transport.request(self.service, "GET", f"/jobs/retrieveData/{self.id}",
            scope=scope_token(self.scope), service_id=self.client.runtime_service_id,
            timeout=timeout, deadline=deadline, response="avro", max_response_bytes=MAX_BYTES)

    def _decode_result(self, payload):
        return _result(payload, self)


class AsyncProviderDataJob(AsyncJob):
    async def _retrieve_result(self, timeout, deadline):
        return await self.client.transport.request(self.service, "GET", f"/jobs/retrieveData/{self.id}",
            scope=scope_token(self.scope), service_id=self.client.runtime_service_id,
            timeout=timeout, deadline=deadline, response="avro", max_response_bytes=MAX_BYTES)

    def _decode_result(self, payload):
        return _result(payload, self)


def _request(service, resource, observation, geometry, scope, start_time, end_time, input_data):
    # Preserve the existing facade's client/scope binding refusal before HTTP.
    token = service._bound_scope_token(scope)
    if token is None:
        raise InvalidRequestError("Provider computation requires an explicit authorized scope")
    if isinstance(scope, Context):
        service.client._validate_context_binding(scope)
    urn = getattr(resource, "urn", None)
    if not isinstance(urn, str) or not urn or len(urn) > 4096:
        raise InvalidRequestError("Contextualization requires an explicit resource asset URN")
    if type(start_time) is not int or type(end_time) is not int or end_time < start_time:
        raise InvalidRequestError("Provider event milliseconds must be exact ordered integers")
    if input_data is not None and not isinstance(input_data, ProviderData):
        raise InvalidRequestError("Provider input must be an explicit bounded ProviderData or None")
    try:
        observable = observation.get_observable().urn
        encoded_geometry = geometry.encode()
    except (AttributeError, TypeError):
        raise InvalidRequestError("Contextualization requires an Observation and encodable Geometry") from None
    if not isinstance(observable, str) or not observable or not isinstance(encoded_geometry, str) or not encoded_geometry:
        raise InvalidRequestError("Provider observable and geometry encodings must be nonblank text")
    data = dict(observable=observable, geometry=encoded_geometry, startTime=start_time, endTime=end_time,
        resourceUrns=[urn], inputData=_decode(_INSTANCE, input_data.payload) if input_data is not None else None)
    _reject_credential_leaves(data, service.client.transport.redact, "Provider request contains credential material")
    return token, _encode(_REQUEST, data)


def _origin(service, scope):
    client = service.client
    return (client, client.transport.endpoint("resources").url, client.transport.endpoint("runtime").url,
        client.runtime_service_id, scope_token(scope))


def _submitted(service, identity, scope, job_type, origin):
    try:
        if _origin(service, scope) != origin:
            raise ProtocolError("Provider submission origin changed")
        if isinstance(scope, Context): service.client._validate_context_binding(scope)
        if type(identity) is not int or identity <= 0:
            raise ProtocolError("Provider submission requires a positive integer Job id")
        return job_type(service.client, identity, scope, service="resources", result_kind="provider-data")
    except (ConfigurationError, ProtocolError, InvalidRequestError):
        raise SubmissionOutcomeUnknown("Provider submission acknowledgement is invalid; inspect state before retrying") from None


def _contextualize(service, resource, observation, geometry, scope, *, start_time=0, end_time=0, input_data=None):
    token, binary = _request(service, resource, observation, geometry, scope, start_time, end_time, input_data)
    origin = _origin(service, scope)
    try:
        identity = service.client.transport.request("resources", "POST", "/contextualize", scope=token,
            service_id=service.client.runtime_service_id, binary=binary, ambiguous=True, max_response_bytes=4096)
    except ProtocolError:
        raise SubmissionOutcomeUnknown("Provider acknowledgement could not be decoded; inspect state before retrying") from None
    return _submitted(service, identity, scope, ProviderDataJob, origin)


async def _async_contextualize(service, resource, observation, geometry, scope, *, start_time=0, end_time=0, input_data=None):
    token, binary = _request(service, resource, observation, geometry, scope, start_time, end_time, input_data)
    origin = _origin(service, scope)
    try:
        identity = await service.client.transport.request("resources", "POST", "/contextualize", scope=token,
            service_id=service.client.runtime_service_id, binary=binary, ambiguous=True, max_response_bytes=4096)
    except ProtocolError:
        raise SubmissionOutcomeUnknown("Provider acknowledgement could not be decoded; inspect state before retrying") from None
    return _submitted(service, identity, scope, AsyncProviderDataJob, origin)
