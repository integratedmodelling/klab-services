"""Offline checks of milestone permission assertions; never live acceptance."""
from contextlib import nullcontext
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace as NS

import pytest

from klab_client import Handle
from klab_client.errors import AuthenticationError, AuthorizationError


@pytest.mark.parametrize("restoration", ["exposed", "denied", "invalid-credential"])
def test_private_restoration_is_a_separate_required_permission_boundary(monkeypatch, tmp_path, restoration):
    path = Path(__file__).parents[1] / "functional/test_discover_compare_explain.py"
    spec = importlib.util.spec_from_file_location("milestone_permissions_functional", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    reference = Handle(version=1, kind="observation", runtime_id="runtime-id",
        runtime_url="https://runtime.invalid", context_id="s.c", scope="s.c", observation_id=1)
    context = NS(configuration={"id": "s.c"}, get_context_id=lambda: "s.c")
    observation = NS(raw={}, _context=context)
    owner = NS(identity=NS(principal="owner"), client=NS(restore_handle=lambda handle: observation))
    # Deliberately permissive metadata restoration; later evidence/export deny.
    def restore(handle):
        if restoration == "denied":
            raise AuthorizationError("offline permission denied")
        if restoration == "invalid-credential":
            raise AuthenticationError("offline invalid authentication is not permission denial")
        return observation

    released = []
    guest = NS(identity=NS(principal="guest"), client=NS(
        restore_handle=restore,
        create_session=lambda **kwargs: NS(release=lambda: released.append(True) or True)))
    monkeypatch.setenv("KLAB_MILESTONE_OTHER_CERTIFICATE", "offline-only.cert")
    monkeypatch.setattr(module.milestone, "factory_from_paths", lambda *args, **kwargs: lambda: nullcontext(guest))
    monkeypatch.setattr(module, "Context", lambda *args, **kwargs: context)
    monkeypatch.setattr(module, "observation_from_wire", lambda *args: observation)

    def deny(*args, **kwargs):
        raise AuthorizationError("offline denied after metadata exposure")

    monkeypatch.setattr(module, "fetch_execution_evidence", deny)
    monkeypatch.setattr(module, "export_for_analysis", deny)
    sequential = {"matrix_verified": True, "batch": {"outcomes": [{"result_handles": [json.loads(reference.dumps())]}]}}
    def exercise():
        module.test_private_experiment_evidence_and_exports_enforce_access(
            ({"PROFILE": tmp_path / "profile.json", "OUTPUT": tmp_path}, None),
            lambda: nullcontext(owner), sequential, lambda *args: None)
    if restoration == "exposed":
        with pytest.raises(pytest.fail.Exception, match="DID NOT RAISE"):
            exercise()
    elif restoration == "invalid-credential":
        with pytest.raises(AuthenticationError):
            exercise()
    else:
        exercise()
    assert released == [True]
