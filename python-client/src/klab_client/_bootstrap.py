"""Bounded native connection wire adapters; secrets never enter public DTOs."""
from datetime import datetime, timezone
import json
import re
import time

import httpx

from .connection_models import ConnectionIdentity, CredentialExpiredError, ServiceReference, parse_expiry
from .errors import AuthenticationError, AuthorizationError, ProtocolError, ServerError, TransportError

ENGINE_AUTH_ROUTE = "/api/v2/engines/auth-cert"
MAX_BOOTSTRAP_BYTES = 1024 * 1024
_SECRET_KEYS = re.compile(r"token|certificate|signature|credential|password|authorization|(^key$)|publickey", re.I)


def public_fields(value, secrets=()):
    if isinstance(value, dict):
        return {key: public_fields(item, secrets) for key, item in value.items() if not _SECRET_KEYS.search(str(key))}
    if isinstance(value, list):
        return [public_fields(item, secrets) for item in value]
    if isinstance(value, str):
        for secret in secrets:
            if secret:
                value = value.replace(secret, "[REDACTED]")
    return value


def wire_request(url, method, *, payload=None, credential=None, timeout=30, verify=True, transport=None, deadline=None):
    headers = {"Accept": "application/json"}
    if credential:
        headers["Authorization"] = credential
    if deadline is not None:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TransportError("Connection waiting deadline expired")
        timeout = min(timeout, remaining)
    try:
        with httpx.Client(verify=verify, transport=transport, timeout=timeout, trust_env=False,
                          follow_redirects=False) as client:
            with client.stream(method, url, headers=headers, json=payload) as response:
                if response.status_code != 200:
                    error = {401: AuthenticationError, 403: AuthorizationError}.get(response.status_code, ServerError)
                    raise error("Connection endpoint HTTP " + str(response.status_code))
                content = bytearray()
                for chunk in response.iter_bytes():
                    if deadline is not None and time.monotonic() >= deadline:
                        raise TransportError("Connection waiting deadline expired")
                    if len(content) + len(chunk) > MAX_BOOTSTRAP_BYTES:
                        raise ProtocolError("Connection response exceeds its bounded metadata budget")
                    content.extend(chunk)
                if deadline is not None and time.monotonic() >= deadline:
                    raise TransportError("Connection waiting deadline expired")
                result = json.loads(content)
    except httpx.HTTPError:
        raise TransportError("Connection endpoint transport failed") from None
    except (ValueError, TypeError):
        raise ProtocolError("Connection endpoint returned invalid JSON") from None
    if not isinstance(result, dict):
        raise ProtocolError("Connection endpoint returned no object")
    return result


def decode_bootstrap(payload, profile, expected_principal, secrets):
    try:
        user = payload["userData"]
        identity = user["identity"]
        principal = identity["id"]
        token = user["token"]
        issuer = payload["hub"]["id"]
        if principal != expected_principal:
            raise AuthenticationError("Hub identity differs from the requested certificate principal")
        if not isinstance(token, str) or not token.strip() or any(c in token for c in "\r\n"):
            raise ProtocolError("Hub response requires a single-line issued token")
        expiry = parse_expiry(user["expiry"])
        if expiry is None or expiry <= datetime.now(timezone.utc):
            raise CredentialExpiredError("Hub authentication package has expired or has no expiry")
        safe_secrets = (*secrets, token)
        if any(secret and secret.casefold() in principal.casefold() for secret in safe_secrets):
            raise ProtocolError("Hub principal identity contains sensitive material")
        if not isinstance(issuer, str) or not issuer.strip() or any(secret and secret in issuer for secret in safe_secrets):
            raise ProtocolError("Hub response requires a safe issuer identity")
        groups = user.get("groups", [])
        if not isinstance(groups, list):
            raise ProtocolError("Hub identity groups must be a list")
        safe_identity = public_fields(identity, safe_secrets)
        result_identity = ConnectionIdentity(principal, issuer, safe_identity.get("email"),
            tuple(public_fields(groups, safe_secrets)), safe_identity)
        entries = payload.get("services", [])
        if not isinstance(entries, list) or len(entries) > profile.max_services:
            raise ProtocolError("Hub service catalog must be a bounded list")
        references = []
        for item in entries:
            declared_type = item.get("identityType", "LEGACY_NODE")
            if not isinstance(declared_type, str) or any(secret and secret.casefold() in declared_type.casefold() for secret in safe_secrets):
                raise ProtocolError("Hub declared service type must be safe text")
            urls = item.get("urls", [])
            if not isinstance(urls, list):
                raise ProtocolError("Hub service URLs must be lists")
            permissions = item.get("permissions", [])
            if not isinstance(permissions, list) or any(value not in {"QUERY", "PUBLISH"} for value in permissions):
                raise ProtocolError("Unknown Hub service permission state")
            for url in urls:
                if not isinstance(url, str) or any(secret and secret in url for secret in safe_secrets):
                    raise ProtocolError("Hub service reference URL contains sensitive material")
                if item.get("id") is not None and any(secret and secret in item["id"] for secret in safe_secrets):
                    raise ProtocolError("Hub service reference identity contains sensitive material")
                references.append(ServiceReference(declared_type, url,
                    item.get("id"), primary=item.get("primary", False), online=item.get("online"),
                    permissions=tuple(permissions), raw=public_fields(item, safe_secrets)))
                if len(references) > profile.max_services:
                    raise ProtocolError("Hub service URLs exceed the bounded catalog budget")
    except (KeyError, TypeError, AttributeError):
        raise ProtocolError("Malformed native Hub authentication package") from None
    return result_identity, expiry, token, tuple(references)
