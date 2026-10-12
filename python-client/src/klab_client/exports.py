"""Advertised native observation exports; bounded client memory and atomic publication."""
from dataclasses import dataclass
from email.message import Message
import os
from pathlib import Path
import re
import tempfile
from urllib.parse import quote

from .api.runtime import ObservationImpl
from .client import Client, Context, scope_token
from .errors import InvalidRequestError, KlabError, ProtocolError, UnsupportedOperationError

CHUNK_BYTES = 65536
_MEDIA = re.compile(r"^[A-Za-z0-9!#$%&'+.^_`|~-]+/[A-Za-z0-9!#$%&'+.^_`|~-]+$")


class ExportIOError(KlabError):
    """Local file publication failed; inspect the explicitly requested destination."""


@dataclass(frozen=True)
class ExportFormat:
    schema_id: str
    media_type: str
    extensions: tuple[str, ...]
    description: str | None = None


@dataclass(frozen=True)
class ExportResult:
    path: Path
    bytes_written: int
    media_type: str
    schema_id: str


def _media(value, error):
    if (not isinstance(value, str) or not value.isascii() or any(ord(c) < 32 or ord(c) == 127 for c in value)
            or not _MEDIA.fullmatch(value.split(";", 1)[0].strip())):
        raise error("Export requires one explicit valid media type")
    message = Message()
    message["content-type"] = value
    parameters = message.get_params()[1:]
    if any(not key or val is None for key, val in parameters) or len({key for key, val in parameters}) != len(parameters):
        raise error("Invalid or duplicate media-type parameters")
    return message.get_content_type(), dict(parameters)


def _context(client, observation):
    if not isinstance(client, Client) or not isinstance(observation, ObservationImpl):
        raise InvalidRequestError("Export requires a Client and bound ObservationImpl")
    context = observation._context
    if (not isinstance(context, Context) or type(observation.id) is not int
            or not 0 < observation.id <= 2**63 - 1):
        raise InvalidRequestError("Export requires a bound committed native observation")
    if observation.metadata.get("im:storage-binding-source"):
        raise UnsupportedOperationError("Detached mediated views require an explicit server export contract")
    client._validate_context_binding(context)
    return context


def export_formats(client, observation):
    """Inspect configured Runtime capabilities; availability is not asset applicability."""
    context = _context(client, observation)
    capabilities = client.runtime.capabilities()
    client._known_runtime_identity(context.configuration)
    schemata = capabilities.raw.get("exportSchemata", {})
    if not isinstance(schemata, dict):
        raise ProtocolError("Runtime exporter schemas must be a mapping")
    formats = {}
    for entries in schemata.values():
        if not isinstance(entries, list):
            raise ProtocolError("Runtime exporter schema entries must be lists")
        for schema in entries:
            if not isinstance(schema, dict):
                raise ProtocolError("Runtime exporter schema must be an object")
            if schema.get("knowledgeClass") != "OBSERVATION":
                continue
            identity, media, extensions = schema.get("schemaId"), schema.get("mediaTypes"), schema.get("extensions", [])
            description = schema.get("description")
            if (not isinstance(identity, str) or not identity.strip() or not isinstance(media, list)
                    or not isinstance(extensions, list) or any(not isinstance(v, str) for v in extensions)
                    or description is not None and not isinstance(description, str)):
                raise ProtocolError("Malformed advertised observation exporter")
            for representation in media:
                _media(representation, ProtocolError)
                formats[(identity, representation)] = ExportFormat(identity, representation, tuple(sorted(extensions)), description)
    return tuple(formats[key] for key in sorted(formats))


def _length(response):
    value = response.headers.get("content-length")
    if value is None:
        return None
    if not value.isascii() or not value.isdecimal() or len(value) > 19:
        raise ProtocolError("Invalid export Content-Length")
    return int(value)


def export_to_file(client, observation, path, *, media_type, overwrite=False, max_bytes=None):
    """Download exactly one advertised representation; never recreate or release remote work."""
    expected_type, expected_parameters = _media(media_type, InvalidRequestError)
    if type(overwrite) is not bool or (max_bytes is not None and (type(max_bytes) is not int or max_bytes <= 0)):
        raise InvalidRequestError("Export requires boolean overwrite and an optional positive byte limit")
    try:
        destination = Path(path)
        if not destination.parent.is_dir() or (not overwrite and os.path.lexists(destination)):
            raise ExportIOError("Export destination parent must exist and default publication cannot overwrite")
    except (TypeError, ValueError, OSError):
        raise ExportIOError("Invalid export destination") from None
    formats = [item for item in export_formats(client, observation) if item.media_type == media_type]
    if len(formats) != 1:
        raise UnsupportedOperationError("Requested representation is not uniquely advertised by this Runtime")
    context = _context(client, observation)
    urn = quote(context.id + f".{observation.id}", safe="")
    temporary = None
    written = 0
    try:
        descriptor, name = tempfile.mkstemp(prefix=".klab-export-", dir=destination.parent)
        temporary = Path(name)
        try:
            with client.transport.stream("runtime", "/export/OBSERVATION/" + urn, media_type=media_type,
                    scope=scope_token(context), service_id=client._known_runtime_identity(context.configuration)) as response:
                actual_type, actual_parameters = _media(response.headers.get("content-type"), ProtocolError)
                if actual_type != expected_type or any(actual_parameters.get(k) != v for k, v in expected_parameters.items()):
                    raise ProtocolError("Export response representation differs from the request")
                if response.status_code != 200 or response.headers.get("content-encoding", "identity").lower() != "identity":
                    raise ProtocolError("Export requires a complete identity-encoded response")
                length = _length(response)
                if max_bytes is not None and length is not None and length > max_bytes:
                    raise ProtocolError("Export exceeds the requested byte limit")
                for chunk in response.iter_raw(chunk_size=CHUNK_BYTES):
                    written += len(chunk)
                    if max_bytes is not None and written > max_bytes:
                        raise ProtocolError("Export exceeds the requested byte limit")
                    view = memoryview(chunk)
                    while view:
                        count = os.write(descriptor, view)
                        if count <= 0:
                            raise OSError("Short export write")
                        view = view[count:]
                if not written or length is not None and length != written:
                    raise ProtocolError("Export is empty or differs from its declared byte count")
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        if overwrite:
            os.replace(temporary, destination)
            temporary = None
        else:
            os.link(temporary, destination)
    except OSError:
        raise ExportIOError("Export write/publication failed; inspect the requested destination") from None
    finally:
        if temporary is not None:
            try:
                temporary.unlink(missing_ok=True)
            except OSError:
                raise ExportIOError("Export temporary cleanup failed; inspect the requested destination") from None
    return ExportResult(destination, written, media_type, formats[0].schema_id)
