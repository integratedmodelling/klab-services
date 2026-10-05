"""Explicit, origin-bound synchronous HTTP transport. No automatic retries."""
from __future__ import annotations

import math
import re
from dataclasses import dataclass, field
from urllib.parse import urlsplit

import httpx

from .errors import (
    AuthenticationError, AuthorizationError, ConfigurationError, InvalidRequestError,
    MissingAssetError, ProtocolError, ServerError, SubmissionOutcomeUnknown, TransportError,
)


@dataclass(frozen=True)
class Endpoint:
    url: str
    credential: str | None = field(default=None, repr=False)

    def __post_init__(self):
        parsed = urlsplit(self.url)
        if (parsed.scheme not in {"http", "https"} or not parsed.hostname
                or parsed.username or parsed.password or parsed.query or parsed.fragment):
            raise ConfigurationError("Endpoint must be an HTTP(S) service URL without credentials/query/fragment")
        if self.credential is not None and (not self.credential.strip() or "\r" in self.credential or "\n" in self.credential):
            raise ConfigurationError("Issued credential must be nonblank and single-line")


class Transport:
    def __init__(self, endpoints: dict[str, Endpoint], *, timeout: float = 30,
                 poll_interval: float = .5, verify: bool | str = True,
                 http_transport: httpx.BaseTransport | None = None):
        if not all(math.isfinite(v) and v > 0 for v in (timeout, poll_interval)):
            raise ConfigurationError("Timeout and polling interval must be finite and positive")
        self.endpoints = dict(endpoints)
        self.timeout = timeout
        self.poll_interval = poll_interval
        self._http = httpx.Client(timeout=timeout, verify=verify, transport=http_transport,
                                  follow_redirects=False, trust_env=False)

    def redact(self, text: str) -> str:
        for endpoint in self.endpoints.values():
            if endpoint.credential:
                text = text.replace(endpoint.credential, "[REDACTED]")
        return re.sub(r"(?i)(authorization|access[_-]?token|server-key|credential)(\s*[=:]\s*)([^\s,;]+)",
                      r"\1\2[REDACTED]", text)

    def endpoint(self, service: str) -> Endpoint:
        try:
            return self.endpoints[service]
        except KeyError:
            raise ConfigurationError(f"Configure the {service} endpoint explicitly") from None

    def request(self, service: str, method: str, route: str, *, scope: str | None = None,
                service_id: str | None = None, json=None, text: str | None = None,
                params=None, response: str = "json", ambiguous: bool = False,
                timeout: float | None = None):
        endpoint = self.endpoint(service)
        if not route.startswith("/") or ".." in route or route.startswith("//"):
            raise InvalidRequestError("Route must be a service-relative absolute path")
        headers = {"Accept": "text/plain" if response == "text" else "application/json"}
        if endpoint.credential:
            headers["Authorization"] = endpoint.credential
        if scope:
            headers["klab-scope"] = scope
        if service_id:
            headers["klab-service"] = service_id
        if text is not None:
            headers["Content-Type"] = "text/plain"
        try:
            result = self._http.request(method, endpoint.url.rstrip("/") + route,
                                        headers=headers, json=json,
                                        content=text.encode() if text is not None else None,
                                        params=params, timeout=timeout or self.timeout)
        except httpx.TransportError:
            error = SubmissionOutcomeUnknown if ambiguous else TransportError
            raise error(f"{service} {method} request failed"
                        + ("; server outcome unknown; inspect remote state before retrying" if ambiguous else "")) from None
        except (ValueError, TypeError):
            raise InvalidRequestError("Request cannot be serialized as the checked transport DTO") from None
        if not 200 <= result.status_code < 300:
            if ambiguous and result.status_code >= 500:
                raise SubmissionOutcomeUnknown(f"{service} {method} HTTP {result.status_code}; "
                    "server outcome unknown; inspect remote state before retrying: "
                    + self.redact(result.text)[:2000])
            error = {401: AuthenticationError, 403: AuthorizationError, 400: InvalidRequestError,
                     404: MissingAssetError, 422: InvalidRequestError}.get(result.status_code, ServerError)
            raise error(f"{service} {method} HTTP {result.status_code}: "
                        + self.redact(result.text)[:2000])
        if response == "text":
            return result.text
        try:
            return result.json()
        except ValueError:
            raise ProtocolError(f"{service} returned invalid JSON for {route}") from None

    def close(self):
        self._http.close()
