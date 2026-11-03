"""Process-local anti-rebinding checks, not Job ownership/incarnation proof."""
from dataclasses import dataclass, field

from .errors import InvalidRequestError, ProtocolError


def _endpoint(transport, service):
    value = transport.endpoints.get(service)
    return None if value is None else (value.url, value.credential)


def _scope_identity(scope):
    client = getattr(scope, "client", None)
    if client is None:
        return None
    configuration = getattr(scope, "configuration", {})
    if not isinstance(configuration, dict):
        raise ProtocolError("Job scope configuration identity is malformed")
    return (_endpoint(client.transport, "runtime"), client.runtime_service_id,
            configuration.get("serviceId"), configuration.get("id"))


@dataclass(frozen=True)
class JobBinding:
    client: object = field(repr=False)
    transport: object = field(repr=False)
    job_id: int
    service: str
    scope: str | None
    scope_client: object = field(repr=False)
    scope_identity: tuple | None = field(repr=False)
    runtime_id: str | None
    home: tuple | None = field(repr=False)
    peer: tuple | None = field(repr=False)
    known_peer_id: str | None
    known_home_id: str | None
    decoder: object = field(repr=False)
    result_kind: str | None

    @classmethod
    def capture(cls, job, token):
        client, transport = job.client, job.client.transport
        return cls(client, transport, job.id, job.service, token, getattr(job.scope, "client", None),
            _scope_identity(job.scope), client.runtime_service_id,
            _endpoint(transport, "runtime"), _endpoint(transport, job.service),
            client._service_id_cache.get(job.service), client._service_id_cache.get("runtime"),
            job._decoder, job.result_kind)

    def validate(self, job, tokenize):
        message = "Job binding changed; do not poll, retrieve, cancel or save it under a different identity"
        if job.client is not self.client or self.client.transport is not self.transport:
            raise ProtocolError(message)
        try:
            token = tokenize(job.scope)
        except InvalidRequestError:
            raise ProtocolError(message) from None
        if (job.id != self.job_id or type(job.id) is not int or job.service != self.service
                or token != self.scope or getattr(job.scope, "client", None) is not self.scope_client
                or _scope_identity(job.scope) != self.scope_identity
                or self.client.runtime_service_id != self.runtime_id
                or _endpoint(self.transport, "runtime") != self.home
                or _endpoint(self.transport, self.service) != self.peer
                or self.known_peer_id is not None and self.client._service_id_cache.get(self.service) != self.known_peer_id
                or self.known_home_id is not None and self.client._service_id_cache.get("runtime") != self.known_home_id
                or job._decoder is not self.decoder or job.result_kind != self.result_kind):
            raise ProtocolError(message)
