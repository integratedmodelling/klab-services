import importlib.util
import json
import os
from pathlib import Path

import pytest

from klab_client.errors import ConfigurationError


@pytest.mark.live
def test_typed_observe_options_are_honored_by_actual_scientific_execution(record_property):
    state = os.environ.get("KLAB_FIXTURE_STATE_DIR")
    if not state:
        raise ConfigurationError("Typed live acceptance requires an explicitly prepared fixture state")
    example = Path(__file__).parents[1] / "examples/typed_observe.py"
    spec = importlib.util.spec_from_file_location("typed_example", example)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    result = module.demonstrate(Path(state))
    record_property("typed_observe", json.dumps(result))
    (Path(state) / "typed-observe.json").write_text(json.dumps(result, indent=2))
    assert result["passed"]
