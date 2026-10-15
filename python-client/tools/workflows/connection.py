"""Controlled Engine exchange/token connection to genuine owned scientific services."""
from contextlib import contextmanager
import json
from pathlib import Path
import sys

from klab_client import (ConnectionIdentity, DeploymentProfile, HubCertificateProvider,
                        IssuedTokenProvider, ObservationRequest, connect)
from klab_client.errors import AuthenticationError, AuthorizationError, ConfigurationError
from . import geojson_export
from .compute_and_analyze import verify_constant_elevation
from .scientific_checks import release_owned, require


@contextmanager
def connected_client(state, provider_kind="certificate", *, on_connect=None, collaborator=False):
    state = Path(state)
    if not (state / "connection-profile.json").is_file():
        raise ConfigurationError("Connection suites require connection-profile.json; prepare the current owned TEST fixture")
    profile = DeploymentProfile.load(state / "connection-profile.json")
    if provider_kind == "certificate":
        provider = HubCertificateProvider(state / "engine.cert")
    elif provider_kind == "token":
        configuration = json.loads((state / "scientist.json").read_text())
        principal = "other-scientist" if collaborator else configuration["username"]
        token = configuration["other_token"] if collaborator else configuration["token"]
        provider = IssuedTokenProvider(ConnectionIdentity(principal, "python-local-test-hub"),
            {url: token for url in configuration["urls"].values()})
    else:
        raise ConfigurationError("Choose certificate or token provider")
    with connect(profile, credentials=provider) as connection:
        connection.wait_ready(timeout=30)
        if on_connect is not None:
            on_connect(connection)
        yield connection.client


def run(state, provider_kind="certificate"):
    """Existing scientific oracle, selected Client, and fresh-process provider acquisition."""
    state = Path(state).resolve()
    connections = []
    worker = Path(__file__).parents[1] / "recover_connection.py"
    def recovery(reference, destination):
        return [sys.executable, str(worker), "--state-dir", str(state), "--provider", provider_kind,
                str(reference), str(destination)]
    def scientific(client, context, region):
        elevation = context.within(region).observe(ObservationRequest("geography:Elevation in m")).result(120)
        return verify_constant_elevation(client, elevation)
    report = geojson_export.run(state, client_factory=lambda: connected_client(state, provider_kind, on_connect=connections.append),
                               recovery_command=recovery, scientific_check=scientific,
                               before_recovery=lambda: connections[-1].close())
    report.update(provider=provider_kind, authentication_evidence="controlled TEST authority, not actual Hub onboarding",
                  fresh_process="original handle restored and exported without scientific resubmission")
    return report


def access(state):
    """Known existing private work: owner positive, valid collaborator negative."""
    from verify_fixture import require_access_denied
    session = context = None
    failure = None
    with connected_client(state, "token") as owner:
        try:
            session = owner.create_session(name="connection-access-owner")
            context = session.create_context(name="connection-private")
            require(owner.attach_context(context.id).id == context.id, "Owner could not reconnect to private work")
            with connected_client(state, "token", collaborator=True) as collaborator:
                positive = collaborator.create_session(name="connection-valid-collaborator")
                release_owned(positive)
                denial = require_access_denied(lambda: collaborator.attach_context(context.id))
            require(owner.attach_context(context.id).id == context.id, "Denied access affected owner's work")
            # Mint only against the private disposable TEST authority. The SDK does
            # not parse JWT claims or guess expiry from an arbitrary 401/403.
            from cryptography.hazmat.primitives import serialization
            from fixture_hub import token as issue_test_token
            configuration = json.loads((Path(state) / "scientist.json").read_text())
            key = serialization.load_pem_private_key((Path(state) / "fixture-authority.pem").read_bytes(), password=None)
            expired = issue_test_token(key, configuration["username"], lifetime=-60)
            provider = IssuedTokenProvider(ConnectionIdentity(configuration["username"], "python-local-test-hub"),
                {url: expired for url in configuration["urls"].values()})
            try:
                with connect(DeploymentProfile.load(Path(state) / "connection-profile.json"), credentials=provider) as rejected:
                    rejected.client.attach_context(context.id)
            except (AuthenticationError, AuthorizationError) as error:
                expiry_rejection = type(error).__name__
            else:
                raise AssertionError("Known expired TEST JWT was accepted")
            return {"owner_reconnected": True, "valid_collaborator_positive_control": True,
                    "private_context_denial": denial, "known_expired_TEST_JWT_rejection": expiry_rejection}
        except BaseException as error:
            failure = error
            raise
        finally:
            release_owned(context, session, failure=failure)


def readiness(state):
    observed = []
    with connected_client(state, on_connect=lambda connection: observed.extend(connection.catalog.summary())):
        require(all(entry["available"] is True and entry["operational"] is True for entry in observed),
                "Required services did not become ready")
    return {"services": observed, "disruption_evidence": "controlled offline HTTP/unit readiness tests"}
