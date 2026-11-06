"""Fresh SDK-only process: inspect saved cells; reject every network attempt."""
import hashlib
import json
from pathlib import Path
import sys

from klab_client import ScientificData
from klab_client.errors import InvalidRequestError


def main():
    def offline(event, arguments):
        if event.startswith("socket."):
            raise AssertionError("Offline snapshots must never access a socket")
    sys.addaudithook(offline)
    directory = Path(sys.argv[1])
    oracle = json.loads((directory / "expected.json").read_text())
    snapshots = sorted(directory.glob("cells-*.json"))
    values, offsets, hashes = [], [], []
    for path in snapshots:
        text = path.read_text(encoding="utf-8")
        block = ScientificData.loads(text)
        assert block.observable == "geography:Elevation in m" and block.units == "m"
        assert block.source_units == "m" and block.artifact_type == "NUMBER"
        assert block.curve == "UNSPECIFIED" and block.slice["type"] == "INITIALIZATION"
        assert block.origin.scope == block.origin.context_id
        assert block.origin.runtime_id == oracle["runtime_id"]
        assert block.geometry.to_wire() == oracle["geometry"]
        values.extend(block.values)
        offsets.extend(block.offsets)
        hashes.append(hashlib.sha256(text.encode()).hexdigest())
        assert ScientificData.loads(block.dumps()).dumps() == text
        # Offline corruption remains an error; no service lookup or reconstruction.
        malformed = json.loads(text)
        malformed["version"] = 999
        try:
            ScientificData.loads(json.dumps(malformed))
        except InvalidRequestError:
            pass
        else:
            raise AssertionError("Unknown snapshot version was accepted")
    assert offsets == list(range(400)) and values == [value for row in oracle["values"] for value in row]
    print(json.dumps({"backend": "OFFLINE_SNAPSHOT_INSPECTION", "cells": len(values),
                      "sha256": hashes, "network_permitted": False,
                      "remote_authorization": "NOT_RECHECKED", "revision": "UNKNOWN"}))


if __name__ == "__main__":
    main()
