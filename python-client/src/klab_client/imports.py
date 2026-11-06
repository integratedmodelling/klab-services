"""Explicit file imports using advertised Resources schemas, never component installation.

Upload acknowledgement is a Job, not scientific computation or immutable provenance.
No retry, implicit source annotation, overwrite promise, remote rollback or cleanup.
"""
from contextlib import contextmanager
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import re
import stat
from urllib.parse import quote

from ._inspection import bounds, text
from .client import Client, Job, scope_token
from .errors import InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown, UnsupportedOperationError
from .exports import _media


@dataclass(frozen=True)
class ImportFormat:
    schema_id: str
    media_types: tuple[str, ...]
    description: str | None


@dataclass(frozen=True)
class ImportSubmission:
    job: object
    service_url: str
    service_id: str
    schema_id: str
    source_sha256: str
    source_bytes: int
    suggested_urn: str
    limit: str = "Upload receipt only; inspect the Job and returned ResourceSet before claiming resource admission"


class _Upload:
    """Hash/count the actual file bytes consumed by HTTPX, with bounded read memory."""
    def __init__(self, stream, size):
        self.stream, self.size, self.count = stream, size, 0
        self.sha = hashlib.sha256()

    def fileno(self):
        return self.stream.fileno()

    def seek(self, offset, whence=0):
        if offset != 0 or whence != 0 or self.count:
            raise OSError("Upload cannot be replayed")
        return self.stream.seek(0)

    def read(self, size=-1):
        chunk = self.stream.read(min(65536, size if size >= 0 else 65536))
        self.count += len(chunk)
        if self.count > self.size:
            raise OSError("Upload source grew after preflight")
        self.sha.update(chunk)
        return chunk


class ResourceImporter:
    """Synchronous RESOURCE/STREAM uploads to the configured Resources service only."""
    def __init__(self, client, scope=None, *, max_source_bytes=64 * 1024 * 1024,
                 max_response_bytes=2 * 1024 * 1024):
        if not isinstance(client, Client):
            raise InvalidRequestError("Resource imports require a synchronous Client")
        self._configure(client, scope, max_source_bytes, max_response_bytes)

    def _configure(self, client, scope, max_source_bytes, max_response_bytes):
        self.client, self.scope = client, scope
        scope_token(scope)
        self.max_source_bytes = bounds(max_source_bytes, 1024 * 1024 * 1024, "Import source byte budget")
        self.max_response_bytes = bounds(max_response_bytes, 16 * 1024 * 1024, "Import response byte budget")
        self._binding = None

    def _formats(self, capability):
        binding = (self.client.transport.endpoint("resources").url, capability.service_id)
        if self._binding is not None and self._binding != binding:
            raise ProtocolError("Resources import identity changed; explicitly reconnect")
        self._binding = binding
        schemas = capability.raw.get("importSchemata", {})
        if not isinstance(schemas, dict):
            raise ProtocolError("Import schemas must be a mapping")
        result = []
        for rows in schemas.values():
            if not isinstance(rows, list):
                raise ProtocolError("Import schema entries must be arrays")
            for row in rows:
                if not isinstance(row, dict):
                    raise ProtocolError("Import schema must be an object")
                if row.get("knowledgeClass") != "RESOURCE" or row.get("type") != "STREAM":
                    continue
                schema, media, description = row.get("schemaId"), row.get("mediaTypes"), row.get("description")
                if (not isinstance(schema, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,256}", schema)
                        or not isinstance(media, list) or not media
                        or description is not None and not isinstance(description, str)):
                    raise ProtocolError("Malformed advertised resource importer")
                for value in media:
                    _media(value, ProtocolError)
                if any(self.client.transport.redact(value) != value for value in (schema, *media)):
                    raise ProtocolError("Import schema identity contains credential material")
                result.append(ImportFormat(schema, tuple(media), self.client.transport.redact(description) if description else description))
                if len(result) > 256:
                    raise ProtocolError("Import schema item budget exceeded")
        return tuple(result)

    def formats(self):
        """Discover file import schemas, not evidence a particular source is admissible."""
        return self._formats(self.client.resources.capabilities(self.scope, max_response_bytes=self.max_response_bytes))

    def _request(self, urn, schema_id, media_type, allow_write):
        if allow_write is not True:
            raise InvalidRequestError("Resource import requires explicit allow_write=True")
        for value, label in ((urn, "Suggested resource URN"), (schema_id, "Import schema")):
            text(value, label, 512)
            if self.client.transport.redact(value) != value:
                raise InvalidRequestError("Import identity cannot contain credential material")
        _media(media_type, InvalidRequestError)
        return "/import/" + quote(schema_id, safe="") + "/" + quote(urn, safe="")

    def _select(self, formats, schema_id, media_type):
        matches = [x for x in formats if x.schema_id == schema_id and media_type in x.media_types]
        if len(matches) != 1:
            raise UnsupportedOperationError("Choose one uniquely advertised RESOURCE/STREAM schema and media type")

    @contextmanager
    def _file(self, path):
        failure = None
        try:
            stream = Path(path).open("rb")
        except (OSError, TypeError, ValueError):
            raise InvalidRequestError("Cannot open the explicitly selected import file") from None
        try:
            metadata = os.fstat(stream.fileno())
            if not stat.S_ISREG(metadata.st_mode) or not 0 < metadata.st_size <= self.max_source_bytes:
                raise InvalidRequestError("Import requires a nonempty regular file within the source byte budget")
            suffix = Path(path).suffix
            filename = "upload" + (suffix if re.fullmatch(r"\.[A-Za-z0-9]{1,12}", suffix) else ".bin")
            yield filename, _Upload(stream, metadata.st_size)
        except BaseException as error:
            failure = error
            raise
        finally:
            try:
                stream.close()
            except OSError:
                if failure is not None:
                    failure.add_note("Import source cleanup failed")
                else:
                    raise SubmissionOutcomeUnknown("Import source cleanup failed; inspect remote state before retrying") from None

    def _finish(self, identity, upload, schema_id, urn, job_type):
        if type(identity) is not int or not 1 <= identity <= 2**63 - 1:
            raise ProtocolError("Import requires a positive Java-long Job acknowledgement")
        url, service_id = self._binding
        if (upload.count != upload.size or self.client.transport.endpoint("resources").url != url
                or self.client._service_id_cache.get("resources") != service_id):
            raise ProtocolError("Import bytes or service binding changed during upload")
        job = job_type(self.client, identity, self.scope, service="resources")
        return ImportSubmission(job, url, service_id, schema_id, upload.sha.hexdigest(), upload.count, urn)

    def upload(self, path, *, urn, schema_id, media_type, allow_write=False):
        """Upload once; preserve returned Resources Job for explicit result/cancel calls."""
        route = self._request(urn, schema_id, media_type, allow_write)
        self._select(self.formats(), schema_id, media_type)
        with self._file(path) as (filename, upload):
            try:
                identity = self.client.transport.request("resources", "POST", route, scope=scope_token(self.scope),
                    files={"file": (filename, upload, media_type)}, ambiguous=True,
                    max_response_bytes=self.max_response_bytes)
                return self._finish(identity, upload, schema_id, urn, Job)
            except (ProtocolError, OSError):
                raise SubmissionOutcomeUnknown("Import acknowledgement incomplete; inspect remote state, do not replay or assume rollback") from None


class AsyncResourceImporter(ResourceImporter):
    """Native awaited remote I/O, sharing upload validation and receipt semantics."""
    def __init__(self, client, scope=None, *, max_source_bytes=64 * 1024 * 1024,
                 max_response_bytes=2 * 1024 * 1024):
        from .async_client import AsyncClient
        if not isinstance(client, AsyncClient):
            raise InvalidRequestError("Async resource imports require AsyncClient")
        self._configure(client, scope, max_source_bytes, max_response_bytes)

    async def formats(self):
        return self._formats(await self.client.resources.capabilities(self.scope, max_response_bytes=self.max_response_bytes))

    async def upload(self, path, *, urn, schema_id, media_type, allow_write=False):
        from .async_client import AsyncJob
        route = self._request(urn, schema_id, media_type, allow_write)
        self._select(await self.formats(), schema_id, media_type)
        with self._file(path) as (filename, upload):
            try:
                identity = await self.client.transport.request("resources", "POST", route, scope=scope_token(self.scope),
                    files={"file": (filename, upload, media_type)}, ambiguous=True,
                    max_response_bytes=self.max_response_bytes)
                return self._finish(identity, upload, schema_id, urn, AsyncJob)
            except (ProtocolError, OSError):
                raise SubmissionOutcomeUnknown("Import acknowledgement incomplete; inspect remote state, do not replay or assume rollback") from None
