"""Native awaited exports; bounded synchronous filesystem publication, no rendering."""
import os
from pathlib import Path
import tempfile
from urllib.parse import quote

from .client import scope_token
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from .exports import (CHUNK_BYTES, ExportIOError, ExportResult, _bound_observation,
                      _formats, _length, _media)


async def export_formats(client, observation):
    context = _bound_observation(client, observation)
    capabilities = await client.runtime.capabilities()
    client._known_runtime_identity(context.configuration)
    return _formats(capabilities.raw)


async def export_to_file(client, observation, path, *, media_type, overwrite=False, max_bytes=None):
    expected_type, expected_parameters = _media(media_type, InvalidRequestError)
    if type(overwrite) is not bool or (max_bytes is not None and (type(max_bytes) is not int or max_bytes <= 0)):
        raise InvalidRequestError("Export requires boolean overwrite and an optional positive byte limit")
    try:
        destination = Path(path)
        if "\x00" in os.fspath(destination):
            raise ValueError("Invalid destination pathname")
        if not destination.parent.is_dir() or (not overwrite and os.path.lexists(destination)):
            raise ExportIOError("Export destination parent must exist and default publication cannot overwrite")
    except (TypeError, ValueError, OSError):
        raise ExportIOError("Invalid export destination") from None
    formats = [item for item in await export_formats(client, observation) if item.media_type == media_type]
    if len(formats) != 1:
        raise UnsupportedOperationError("Requested representation is not uniquely advertised by this Runtime")
    context = _bound_observation(client, observation)
    urn = quote(context.id + f".{observation.id}", safe="")
    temporary = None
    failure = close_note = None
    written = 0
    try:
        descriptor, name = tempfile.mkstemp(prefix=".klab-export-", dir=destination.parent)
        temporary = Path(name)
        try:
            async with client.transport.stream("runtime", "/export/OBSERVATION/" + urn, media_type=media_type,
                    scope=scope_token(context), service_id=client._known_runtime_identity(context.configuration)) as response:
                actual_type, actual_parameters = _media(response.headers.get("content-type"), ProtocolError)
                if actual_type != expected_type or any(actual_parameters.get(k) != v for k, v in expected_parameters.items()):
                    raise ProtocolError("Export response representation differs from the request")
                if response.status_code != 200 or response.headers.get("content-encoding", "identity").lower() != "identity":
                    raise ProtocolError("Export requires a complete identity-encoded response")
                length = _length(response)
                if max_bytes is not None and length is not None and length > max_bytes:
                    raise ProtocolError("Export exceeds the requested byte limit")
                async for chunk in response.aiter_raw(chunk_size=CHUNK_BYTES):
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
        except BaseException as error:
            failure = error
            raise
        finally:
            try:
                os.close(descriptor)
            except BaseException as cleanup:
                close_note = "Export descriptor cleanup failed: " + type(cleanup).__name__
                if failure is None:
                    raise
                failure.add_note(close_note)
        if overwrite:
            os.replace(temporary, destination)
            temporary = None
        else:
            os.link(temporary, destination)
    except OSError:
        failure = ExportIOError("Export write/publication failed; inspect the requested destination")
        if close_note is not None:
            failure.add_note(close_note)
        raise failure from None
    except BaseException as error:
        failure = error
        raise
    finally:
        if temporary is not None:
            try:
                temporary.unlink(missing_ok=True)
            except OSError:
                if failure is None:
                    raise ExportIOError("Export temporary cleanup failed; inspect the requested destination") from None
                failure.add_note("Export temporary cleanup failed: OSError")
    return ExportResult(destination, written, media_type, formats[0].schema_id)
