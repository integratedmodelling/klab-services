import os

import pytest

from klab_client import Client
from klab_client.errors import ConfigurationError
from klab_client.experiment import run_elevation


@pytest.mark.live
def test_live_elevation():
    required = ["KLAB_RUNTIME_URL", "KLAB_RUNTIME_TOKEN", "KLAB_REASONER_URL",
                "KLAB_REASONER_TOKEN", "KLAB_AGENT_NAME"]
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise ConfigurationError("Live acceptance requires: " + ", ".join(missing))
    with Client.from_env() as client:
        id = os.environ.get("KLAB_CONTEXT_ID")
        context = client.attach_context(id) if id else None
        run_elevation(client, context=context)
