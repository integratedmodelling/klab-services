"""Opt-in private TEST federation groups for the existing Hub service lookup.

This distributes fixture broker configuration, not production rights or ACL proof.
"""
import base64
import json
import re
import time
from urllib.parse import urlsplit

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding


def _decode(value):
    return base64.b64decode(value + "=" * (-len(value) % 4), altchars=b"-_", validate=True)


def service_authorized(token, public_key, issuer):
    """Accept only a fresh signed fixture service credential, never scientist JWTs."""
    try:
        if not isinstance(token, str) or len(token) > 16384:
            return False
        header, payload, signature = token.split(".")
        if json.loads(_decode(header)) != {"alg": "RS256", "typ": "JWT"}:
            return False
        public_key.verify(_decode(signature), (header + "." + payload).encode("ascii"),
            padding.PKCS1v15(), hashes.SHA256())
        claims = json.loads(_decode(payload))
        if not isinstance(claims, dict):
            return False
        now = int(time.time())
        return (claims.get("iss") == issuer and claims.get("sub") == "python-test-service"
            and claims.get("aud") == "engine" and claims.get("roles") == ["ROLE_USER", "ROLE_ENGINE"]
            and type(claims.get("iat")) is int and type(claims.get("exp")) is int
            and claims["iat"] <= now < claims["exp"])
    except (ValueError, TypeError, KeyError, UnicodeError, InvalidSignature):
        return False


def load_groups(state):
    """Absent opt-in leaves the historical fixture unchanged; malformed opt-in fails."""
    path = state / "owned-federation.json"
    if not path.exists():
        return None
    if path.stat().st_size > 8192:
        raise ValueError("Owned TEST federation configuration exceeds byte budget")
    config = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(config, dict) or set(config) != {"allow_owned_federation", "name", "broker_uri", "users"}:
        raise ValueError("Unsupported owned TEST federation configuration")
    users, name, uri = config["users"], config["name"], config["broker_uri"]
    if (config["allow_owned_federation"] is not True or not isinstance(name, str)
            or not re.fullmatch(r"python-test-[a-z0-9-]{1,60}", name)
            or not isinstance(users, list) or not 1 <= len(users) <= 8
            or any(not isinstance(user, str) or not re.fullmatch(r"python-[a-z0-9-]{1,60}", user) for user in users)
            or len(set(users)) != len(users) or not isinstance(uri, str) or len(uri) > 4096):
        raise ValueError("Explicit bounded owned TEST federation consent/identities required")
    try:
        parsed = urlsplit(uri)
        valid = (parsed.scheme == "amqp" and parsed.hostname == "127.0.0.1" and parsed.port is not None
            and 1024 <= parsed.port <= 65535 and bool(parsed.username) and bool(parsed.password)
            and parsed.path.startswith("/") and len(parsed.path) > 1 and not parsed.query and not parsed.fragment
            and not any(character.isspace() for character in uri))
    except ValueError:
        valid = False
    if not valid:
        raise ValueError("Owned TEST broker must be explicit authenticated loopback AMQP with a virtual host")
    return {user: [{"name": name, "customProperties": [
        {"key": "federation.id", "value": "true"},
        {"key": "federation.broker.url", "value": uri}]}] for user in users}
