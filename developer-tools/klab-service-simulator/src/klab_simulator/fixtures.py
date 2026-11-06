"""Pinned original evidence projected for distribution; never regenerate expected cells."""
import hashlib
import json
from importlib.resources import files

ORACLE_SHA = "3fe5383b15232a3e09bf5188c9bfef41439d2fbac929970d6b682bf52e580607"
CROP_SHA = "2c9fcd0748b9ef3d8c036a59af5c1419a826ba14262a3a80d089b1d59023fb26"
NONUNIFORM_ORACLE_SHA = "f0848f723b485547c4b37f725e0e1aed63db1c29c49280481e7bcd0439ac9213"
NONUNIFORM_TIFF_SHA = "f9cbe08df11b4c92f4b8f5691690632b297a6706f30b47ba10a88d726161c8fd"


def authentic_fixture():
    root = files("klab_simulator").joinpath("fixtures")
    oracle = root.joinpath("usgs-oracle.json").read_bytes()
    crop = root.joinpath("usgs-native-20x20.tif").read_bytes()
    if hashlib.sha256(oracle).hexdigest() != ORACLE_SHA or hashlib.sha256(crop).hexdigest() != CROP_SHA:
        raise ValueError("Authentic fixture bytes drifted; do not regenerate the oracle")
    result = json.loads(oracle)
    values = result["allCellsNorthToSouthWestToEast"]
    if len(values) != 20 or any(len(row) != 20 for row in values):
        raise ValueError("Frozen authentic fixture must contain exactly400cells")
    return result


def nonuniform_fixture():
    """Independent pre-existing N oracle; initial synthetic TIFF is pinned, never regenerated."""
    root = files("klab_simulator").joinpath("fixtures")
    oracle = root.joinpath("synthetic-nonuniform-oracle.json").read_bytes()
    native = root.joinpath("synthetic-nonuniform-native.tif").read_bytes()
    if hashlib.sha256(oracle).hexdigest() != NONUNIFORM_ORACLE_SHA or hashlib.sha256(native).hexdigest() != NONUNIFORM_TIFF_SHA:
        raise ValueError("Frozen synthetic scientific adapter fixture drifted; do not regenerate")
    return json.loads(oracle), native
