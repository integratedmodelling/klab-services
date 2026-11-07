"""Native asynchronous transport foundation; not yet a public AsyncClient.

No worker-thread facade, hidden event loop, automatic retries or remote cleanup.
Wire decoding, error classification and origin policies share the sync transport.
"""
import asyncio
from contextlib import asynccontextmanager
import inspect
import time

import httpx

from .errors import (AuthenticationError, AuthorizationError, ConfigurationError,
                     InvalidRequestError, MissingAssetError, ProtocolError,
                     ServerError, SubmissionOutcomeUnknown, TransportError)
from .transport import Endpoint, _TransportPolicy


class AsyncTransport(_TransportPolicy):
    def __init__(self, endpoints, *, timeout=30, poll_interval=.5, verify=True,
                 http_transport=None, max_concurrency=8):
        super().__init__(endpoints, timeout=timeout, poll_interval=poll_interval)
        if type(max_concurrency) is not int or max_concurrency <= 0:
            raise ConfigurationError("Async concurrency must be a positive integer")
        self._slots = asyncio.BoundedSemaphore(max_concurrency)
        self._http = httpx.AsyncClient(timeout=timeout, verify=verify, transport=http_transport,
                                       follow_redirects=False, trust_env=False)

    async def _async_credential(self, service, endpoint, deadline=None):
        resolver = self.credential_resolver
        if resolver is None:
            return endpoint.credential
        if not (inspect.iscoroutinefunction(resolver)
                or inspect.iscoroutinefunction(getattr(resolver, "__call__", None))):
            raise ConfigurationError("Async transport requires a native async credential resolver")
        credential = await resolver(service, endpoint.url, deadline)
        if credential is not None and not isinstance(credential, str):
            raise ConfigurationError("Async credentials must be issued text or None")
        Endpoint(endpoint.url, credential)  # Same credential validation as explicit endpoints.
        self.sensitive_values = tuple(dict.fromkeys((*self.sensitive_values, credential)))
        return credential

    async def request(self, *args, **kwargs):
        deadline = kwargs.get("deadline")
        try:
            async with asyncio.timeout(None if deadline is None else max(0, deadline - time.monotonic())):
                await self._slots.acquire()
        except TimeoutError:
            raise TransportError("Request waiting deadline expired before acquiring an HTTP slot") from None
        try:
            return await self._request(*args, **kwargs)
        finally:
            self._slots.release()

    async def _request(self, service, method, route, *, scope=None, service_id=None,
                      json=None, text=None, params=None, response="json", ambiguous=False,
                       timeout=None, deadline=None, max_response_bytes=None, files=None, binary=None, relationship=None):
        endpoint = self.endpoint(service)
        content = self._content(binary, json, text, files)
        headers = self._headers(route, response=response, scope=scope, service_id=service_id,
                                text=text, max_response_bytes=max_response_bytes, binary=binary, relationship=relationship)
        if deadline is not None and time.monotonic() >= deadline:
            raise TransportError("Request waiting deadline expired")
        sent = False
        try:
            # A total deadline includes credential acquisition and every response chunk,
            # not merely HTTPX's individual connect/read inactivity timeouts.
            async with asyncio.timeout(None if deadline is None else max(0, deadline - time.monotonic())):
                credential = await self._async_credential(service, endpoint, deadline)
                if deadline is not None and time.monotonic() >= deadline:
                    raise TimeoutError
                if credential:
                    headers["Authorization"] = credential
                sent = True
                async with self._http.stream(method, endpoint.url.rstrip("/") + route,
                         headers=headers, json=json, files=files, content=content,
                        params=params, timeout=timeout or self.timeout) as streamed:
                    if max_response_bytes is not None and streamed.headers.get("content-encoding", "identity").lower() != "identity":
                        raise ProtocolError("Byte-bounded responses require identity content encoding")
                    chunks, received = [], 0
                    async for chunk in streamed.aiter_bytes(chunk_size=65536 if max_response_bytes is not None else None):
                        if deadline is not None and time.monotonic() >= deadline:
                            raise TimeoutError
                        received += len(chunk)
                        if max_response_bytes is not None and received > max_response_bytes:
                            raise ProtocolError("Response exceeded the requested byte budget")
                        chunks.append(chunk)
                    if deadline is not None and time.monotonic() >= deadline:
                        raise TimeoutError
                    decoded_headers = {key: value for key, value in streamed.headers.items()
                                       if key.lower() not in {"content-encoding", "content-length"}}
                    result = httpx.Response(streamed.status_code, headers=decoded_headers,
                                            content=b"".join(chunks))
        except asyncio.CancelledError as error:
            if ambiguous and sent:
                error.add_note("Server outcome unknown after local request cancellation; inspect remote state before retrying")
            raise
        except (httpx.TransportError, TimeoutError):
            error = SubmissionOutcomeUnknown if ambiguous and sent else TransportError
            raise error(f"{service} {method} request failed"
                        + ("; server outcome unknown; inspect remote state before retrying" if ambiguous and sent else "")) from None
        except (ValueError, TypeError):
            raise InvalidRequestError("Request cannot be serialized as the checked transport DTO") from None
        return self._response(result, service, method, route, response=response, ambiguous=ambiguous)

    @asynccontextmanager
    async def stream(self, service, route, *, media_type, scope=None, service_id=None, params=None):
        async with self._slots:
            async with self._stream(service, route, media_type=media_type, scope=scope, service_id=service_id, params=params) as response:
                yield response

    @asynccontextmanager
    async def _stream(self, service, route, *, media_type, scope=None, service_id=None, params=None):
        """Origin-bound unread GET response; caller owns bounded async iteration."""
        endpoint = self.endpoint(service)
        headers = self._headers(route, scope=scope, service_id=service_id)
        headers.update(Accept=media_type, **{"Accept-Encoding": "identity"})
        credential = await self._async_credential(service, endpoint)
        if credential:
            headers["Authorization"] = credential
        try:
            async with self._http.stream("GET", endpoint.url.rstrip("/") + route,
                    headers=headers, timeout=self.timeout, params=params) as response:
                if not 200 <= response.status_code < 300:
                    error = {400: InvalidRequestError, 401: AuthenticationError,
                             403: AuthorizationError, 404: MissingAssetError,
                             422: InvalidRequestError}.get(response.status_code, ServerError)
                    raise error(f"{service} export HTTP {response.status_code}")
                yield response
        except httpx.TransportError:
            raise TransportError(f"{service} export transport failed; no file was published") from None
        except httpx.StreamError:
            raise ProtocolError("Export response was not an unread stream") from None

    async def aclose(self):
        await self._http.aclose()

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_value, traceback):
        try:
            await self.aclose()
        except BaseException as cleanup:
            if exc_value is None:
                raise
            exc_value.add_note("Async transport cleanup failed: " + type(cleanup).__name__)
