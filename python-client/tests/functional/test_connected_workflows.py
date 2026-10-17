"""Bounded real-deployment checks; never use TEST controls or other users' assets."""
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest

from klab_client import (ContextOptions, DeploymentProfile, HubCertificateProvider,
                         ObservationOptions, ObservationRequest, RectangularGeometry,
                         connect, save_handle)
from klab_client.errors import ConfigurationError

pytestmark = [pytest.mark.live, pytest.mark.functional]


def settings():
    profile, certificate = (os.environ.get(key) for key in
                            ("KLAB_CONNECTED_PROFILE", "KLAB_CONNECTED_CERTIFICATE"))
    if not profile or not certificate:
        raise ConfigurationError("Connected tests require explicit profile and certificate paths")
    return DeploymentProfile.load(profile), HubCertificateProvider(Path(certificate))


def test_connected_onboarding_and_profile_roundtrip(tmp_path, record_property):
    profile, credentials = settings()
    # No peer initialization for this metadata-only check.
    with connect(profile, credentials=credentials, initialize=False) as connection:
        connection.wait_ready(timeout=30)
        summary = connection.catalog.summary()
        for kind in ("resources", "reasoner", "resolver", "runtime"):
            assert any(row["type"] == kind and row["trusted"] and row["operational"] for row in summary)
        models = connection.client.resources.list("MODEL")
        assert models, "Deployment must expose real models to accept model discovery"
        record_property("model_entries", len(models))
        record_property("unique_model_urns", len({model.urn for model in models}))
        saved = tmp_path / "deployment.json"
        profile.save(saved)
        restored = DeploymentProfile.load(saved)
        assert restored.to_dict() == profile.to_dict()


def test_connected_certificate_renewal_preserves_identity(record_property):
    profile, credentials = settings()
    with connect(profile, credentials=credentials, initialize=False) as connection:
        identity = connection.identity.binding
        selection = {kind: entry.service_id for kind, entry in connection.selected.items()}
        renewed = connection.renew()
        assert renewed.binding == identity
        assert {kind: entry.service_id for kind, entry in connection.selected.items()} == selection
        connection.wait_ready(timeout=30)
        record_property("certificate_reacquisition_verified", True)
        # Deliberate expiry, principal switching and independent issued-token onboarding
        # require separate credentials; reacquisition does not certify those cases.


@pytest.fixture
def owned_context():
    # Also guard direct pytest invocations; an inherited profile is not consent to mutation.
    if os.environ.get("KLAB_CONNECTED_ALLOW_OWNED_WORK") != "1":
        raise ConfigurationError("Connected work requires explicit KLAB_CONNECTED_ALLOW_OWNED_WORK=1")
    profile, credentials = settings()
    with connect(profile, credentials=credentials) as connection:
        session = connection.client.create_session(name="Python connected acceptance: test-owned")
        context = None
        primary = None
        try:
            context = session.create_context(options=ContextOptions(
                name="Python acceptance: 20-cell pilot", persistence="EXPLICIT_ACTION"))
            yield connection, context
        except BaseException as error:
            primary = error
            raise
        finally:
            errors = []
            for name, asset in (("context", context), ("session", session)):
                if asset is not None:
                    try:
                        if asset.release() is not True:
                            errors.append("Java did not confirm release of test-owned " + name)
                    except Exception as error:
                        errors.append("Release of test-owned " + name + " failed: " + type(error).__name__)
            if errors:
                message = "; ".join(errors)
                if primary is not None:
                    primary.add_note(message)
                else:
                    raise AssertionError(message)


def test_connected_context_recovers_in_a_fresh_process(owned_context, tmp_path, record_property):
    connection, context = owned_context
    handle = tmp_path / "context.json"
    save_handle(context, handle)
    code = """
import json,os,sys
from pathlib import Path
from klab_client import DeploymentProfile,HubCertificateProvider,connect,load_handle
with connect(DeploymentProfile.load(os.environ['KLAB_CONNECTED_PROFILE']),
             credentials=HubCertificateProvider(Path(os.environ['KLAB_CONNECTED_CERTIFICATE'])),
             initialize=False) as connection:
    try:
        context=load_handle(sys.argv[1]).restore(connection.client)
        print(json.dumps({'pid':os.getpid(),'context_id':context.id,'owned':context.owned}))
    except Exception as error:
        print(json.dumps({'error_type':type(error).__name__,
                          'error':connection.client.transport.redact(str(error))}))
        raise SystemExit(1)
"""
    process = subprocess.run([sys.executable, "-B", "-c", code, str(handle)],
                             capture_output=True, text=True, timeout=90)
    record_property("fresh_process_exit", process.returncode)
    if process.returncode:
        # Do not publish arbitrary subprocess stdout/stderr or credential/provider traces.
        try:
            error = json.loads(process.stdout)
            record_property("restore_error_type", error.get("error_type"))
            record_property("restore_error", connection.client.transport.redact(error.get("error", "")))
        except (ValueError, TypeError, AttributeError):
            pass
        pytest.fail("Fresh-process context restoration failed; see sanitized JUnit properties", pytrace=False)
    result = json.loads(process.stdout)
    assert result["pid"] != os.getpid()
    assert result["context_id"] == context.id
    assert result["owned"] is False
    record_property("fresh_process_verified", True)


def test_connected_tiny_terrestrial_region_completes(owned_context, record_property):
    connection, context = owned_context
    # Inspected deployed model: terrestrial region depends on elevation. The namespace
    # is lexical/ranking context, not a promise to force that model or its dependencies.
    namespace = "staging.vxii.dependencies"
    model = connection.client.resources.retrieve(namespace + ".terrestrial-region-explainer", "MODEL")
    assert any(o.get("urn") == "earth:Terrestrial earth:Region" for o in model.raw.get("observables", []))
    request = ObservationRequest("earth:Terrestrial earth:Region",
        geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4),
        options=ObservationOptions(namespace=namespace))
    job = context.observe(request)
    record_property("job_submitted", True)
    record_property("job_id", job.id)
    primary = None
    try:
        result = job.result(timeout=90)
        assert result.id > 0
        record_property("observation_id", result.id)
    except BaseException as error:
        primary = error
        raise
    finally:
        # Never call cancellation of a terminal Job a successful cancellation test.
        try:
            if job.status().status in {"WAITING", "STARTED", "CHANGED"}:
                accepted = job.cancel()
                record_property("cleanup_cancel_requested", accepted)
        except Exception as error:
            if primary is not None:
                primary.add_note("Own Job cleanup failed: " + type(error).__name__)
            else:
                raise
