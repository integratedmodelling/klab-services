"""Explicit owned TEST stack only; delegates to the shared independent oracle."""
import json

import pytest

from klab_client import Client, Endpoint
from workflows.nonuniform_export import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_nonuniform_native_export_and_installed_reader(functional_state):
    config = json.loads((functional_state / "scientist.json").read_text())
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
        **{name: Endpoint(config["urls"][name], config["token"]) for name in ("reasoner", "resources", "resolver")}) as client:
        client.initialize_user_scope(local_federation=True)
        result = run(client, functional_state)
    (functional_state / "nonuniform-science.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
