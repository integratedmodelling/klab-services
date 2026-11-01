"""One-shot test transport; not a public k.LAB API."""

import json
import math
import sys


def double(values):
    """Double finite numbers, preserving None as missing data."""
    if not isinstance(values, list):
        raise ValueError("values must be a list")
    result = []
    for value in values:
        if value is None:
            result.append(None)
            continue
        if type(value) not in (int, float):
            raise ValueError("values must contain only finite numbers or null")
        try:
            if not math.isfinite(value) or not math.isfinite(value * 2):
                raise ValueError("input and doubled output must be finite")
        except OverflowError as error:
            raise ValueError("number exceeds finite double range") from error
        result.append(value * 2)
    return result


def respond(request):
    if not isinstance(request, dict) or set(request) != {"protocol_version", "operation", "values"}:
        raise ValueError("expected protocol_version, operation and values")
    if type(request["protocol_version"]) is not int or request["protocol_version"] != 1:
        raise ValueError("protocol_version must be integer 1")
    if request["operation"] != "double":
        raise ValueError("operation must be double")
    return {"protocol_version": 1, "values": double(request["values"])}


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
        request = json.loads(sys.stdin.buffer.read().decode("utf-8"),
                             parse_constant=reject_constant, object_pairs_hook=unique_object)
        response = json.dumps(respond(request), allow_nan=False)
        sys.stdout.buffer.write((response + "\n").encode("utf-8"))
        return 0
    except (ValueError, OverflowError) as error:
        sys.stderr.buffer.write(("worker: " + str(error) + "\n").encode("utf-8"))
        return 1


if __name__ == "__main__":
    sys.exit(main())
