"""Real-service suites require explicitly prepared and authorized test state."""
import os
from pathlib import Path

import pytest

from klab_client.errors import ConfigurationError


@pytest.fixture
def functional_state():
    value = os.environ.get("KLAB_FIXTURE_STATE_DIR")
    if not value or not (Path(value) / "scientist.json").is_file():
        raise ConfigurationError("Functional acceptance requires explicit prepared KLAB_FIXTURE_STATE_DIR")
    return Path(value)
