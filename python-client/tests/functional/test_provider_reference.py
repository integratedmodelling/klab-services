"""Actual provider execution; independent reference data must be supplied explicitly."""
import os

import pytest

from workflows.provider_reference import exercise_live
from workflows.scientific_checks import load_reference

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_live_elevation(record_property):
    exercise_live(record_property)


def test_live_elevation_reference(record_property):
    exercise_live(record_property, reference=load_reference(os.environ.get("KLAB_ELEVATION_REFERENCE")))
