"""Independent scientific acceptance assertions; never manufactures live data."""
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path

from klab_client.errors import ConfigurationError


def verify_traversals(yx, xy, inverted_y):
    """Coordinate oracle for the maintained rectangular 5 x 4 grid.

    These formulas use documented axis order, not a server-supplied remapping.
    Data.FillCurve: YX x varies fastest; XY y varies fastest; XInvY reverses y.
    """
    if any(len(values) != 20 for values in (yx, xy, inverted_y)):
        raise AssertionError("Expected all 20 cells for each traversal")
    for x in range(5):
        for y in range(4):
            expected = yx[y * 5 + x]
            if xy[x * 4 + y] != expected or inverted_y[x * 4 + (3 - y)] != expected:
                raise AssertionError(f"Traversal changed value/missingness at grid cell ({x}, {y})")


def _number(value, label):
    if isinstance(value, bool) or not isinstance(value, (str, int, Decimal)):
        raise ConfigurationError(f"Reference {label} must be an exact decimal string or number")
    try:
        result = Decimal(value)
    except InvalidOperation:
        raise ConfigurationError(f"Reference {label} is not numeric") from None
    if not result.is_finite():
        raise ConfigurationError(f"Reference {label} must be finite")
    return result


def load_reference(path):
    """Load independent source/Java oracle with explicit scientific tolerance."""
    if not path:
        raise ConfigurationError("Reference acceptance requires KLAB_ELEVATION_REFERENCE; obtain independent values first")
    try:
        contents = Path(path).read_bytes()
        reference = json.loads(contents, parse_float=Decimal)
    except (OSError, ValueError):
        raise ConfigurationError("Cannot read a valid JSON elevation reference") from None
    if not isinstance(reference, dict):
        raise ConfigurationError("Elevation reference must be a JSON object")
    required = {
        "schema_version": 1, "semantic_definition": "geography:Elevation in m", "units": "m",
        "crs": "EPSG:3857", "bounds": [200000, 200500, 6000000, 6000400],
        "shape": [5, 4], "curve": "D2_YX",
        "slice": {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0},
    }
    for key, expected in required.items():
        if reference.get(key) != expected:
            raise ConfigurationError(f"Reference {key} must match the maintained experiment ({expected})")
    for key in ("source", "dataset_id", "dataset_revision", "sampling", "vertical_datum"):
        if not isinstance(reference.get(key), str) or not reference[key].strip():
            raise ConfigurationError(f"Reference requires nonblank {key} provenance")
    if reference.get("method") not in {"independent-provider", "supported-java-workflow"}:
        raise ConfigurationError("Reference method must identify independent-provider or supported-java-workflow")
    values = reference.get("values_yx")
    if not isinstance(values, list) or len(values) != 20:
        raise ConfigurationError("Reference requires all 20 values_yx, with null for missing cells")
    reference["values_yx"] = tuple(None if v is None else _number(v, "value") for v in values)
    if all(v is None for v in reference["values_yx"]):
        raise ConfigurationError("An all-missing reference cannot establish scientific acceptance")
    for key in ("absolute_tolerance_m", "relative_tolerance"):
        reference[key] = _number(reference.get(key), key)
        if reference[key] < 0:
            raise ConfigurationError("Reference tolerances cannot be negative")
    reference["sha256"] = hashlib.sha256(contents).hexdigest()
    return reference


def verify_reference(values, reference):
    if len(values) != 20:
        raise AssertionError("Reference comparison requires all 20 actual cells")
    errors = []
    for offset, (actual, expected) in enumerate(zip(values, reference["values_yx"])):
        if actual is None or expected is None:
            if actual is not None or expected is not None:
                raise AssertionError(f"Reference missingness differs at YX offset {offset}")
            continue
        actual = Decimal(actual)
        if not actual.is_finite():
            raise AssertionError(f"Nonfinite live value at YX offset {offset}")
        error = abs(actual - expected)
        tolerance = max(reference["absolute_tolerance_m"], abs(expected) * reference["relative_tolerance"])
        if error > tolerance:
            raise AssertionError(f"Reference mismatch at YX offset {offset}: measured {actual}, expected {expected}, tolerance {tolerance}")
        errors.append(error)
    return max(errors)
