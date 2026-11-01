"""NDVI for aligned, unitless surface-reflectance bands in the range [0, 1].

Synthetic demonstration contract: not a satellite product decoder. Cloud/quality
masking and scale/offset correction must be applied before supplying these bands.
"""

import json
import math
import sys


def ndvi(red, nir):
    """Return (NIR - red)/(NIR + red); missing/zero-denominator cells are None."""
    if not isinstance(red, list) or not isinstance(nir, list) or len(red) != len(nir):
        raise ValueError("red and nir must be equal-length lists")
    for band in (red, nir):
        for value in band:
            if value is not None and (type(value) not in (int, float)
                                      or not 0 <= value <= 1 or not math.isfinite(value)):
                raise ValueError("reflectance must be a finite number in [0, 1] or null")
    return [None if r is None or n is None or r + n == 0 else (n - r) / (n + r)
            for r, n in zip(red, nir)]


def respond(payload):
    if not isinstance(payload, dict) or set(payload) != {"protocol_version", "operation", "red", "nir"}:
        raise ValueError("expected protocol_version, operation, red and nir")
    if type(payload["protocol_version"]) is not int or payload["protocol_version"] != 1:
        raise ValueError("protocol_version must be integer 1")
    if payload["operation"] != "ndvi":
        raise ValueError("operation must be ndvi")
    return {"protocol_version": 1, "values": ndvi(payload["red"], payload["nir"])}


def reject_constant(value):
    raise ValueError("non-finite JSON constant: " + value)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON field: " + key)
        result[key] = value
    return result


def main():
    try:
        payload = json.loads(sys.stdin.buffer.read().decode("utf-8"),
                             parse_constant=reject_constant, object_pairs_hook=unique_object)
        response = json.dumps(respond(payload), allow_nan=False)
        sys.stdout.buffer.write((response + "\n").encode("utf-8"))
        return 0
    except ValueError as error:
        sys.stderr.buffer.write(("ndvi: " + str(error) + "\n").encode("utf-8"))
        return 1


if __name__ == "__main__":
    sys.exit(main())
