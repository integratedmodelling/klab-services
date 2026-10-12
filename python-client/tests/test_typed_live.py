import importlib.util
import json
import os
from pathlib import Path

import pytest

from klab_client.errors import ConfigurationError
from klab_client import ObservationOptions, ObservationRequest, RectangularGeometry
from klab_client.errors import InvalidRequestError


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


@pytest.mark.live
def test_bound_computed_quality_is_rejected_as_observer_without_any_http(record_property):
    state_value = os.environ.get("KLAB_FIXTURE_STATE_DIR")
    if not state_value:
        raise ConfigurationError("Observer live regression requires an explicitly prepared fixture state")
    state = Path(state_value)
    example = Path(__file__).parents[1] / "examples/typed_observe.py"
    spec = importlib.util.spec_from_file_location("typed_observer_example", example)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    config = json.loads((state / "scientist.json").read_text())
    calls = []
    with module.fixture_client(config) as client:
        client.initialize_user_scope(local_federation=True)
        client.transport._http.event_hooks["request"].append(lambda request: calls.append(request.url.path))
        session = client.create_session(name="typed-observer-regression")
        context = session.create_context()
        try:
            region = context.observe(ObservationRequest("earth:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
            focused = context.within(region)
            quality = focused.observe("geography:Elevation in m").result(120)
            calls.clear()
            with pytest.raises(InvalidRequestError, match="AGENT"):
                focused.observe("geography:Aspect in degree_angle", options=ObservationOptions(observer=quality))
            assert not calls  # Neither semantic resolution nor submission may precede rejection.
            record_property("observer_quality_rejection", json.dumps({"passed": True, "http_requests": calls}))
        finally:
            assert context.release() and session.release()
