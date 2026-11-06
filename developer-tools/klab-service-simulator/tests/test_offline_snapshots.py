"""CAP12/RC-P1.2: HTTP-produced snapshots survive process exit and owned release."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

import klab_client
from klab_client import Client, ObservationRequest
from conftest import ROOT
from test_vertical import client_args, control, geometry


def test_fresh_sdk_only_process_inspects400_frozen_cells_offline_after_owned_release(simulator, tmp_path):
    oracle = json.loads((ROOT / "src/klab_simulator/fixtures/synthetic-oracle.json").read_text())
    hashes = []
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session()
        context = session.create_context()
        try:
            observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
            for index, offsets in enumerate((range(256), range(256, 400))):
                block = observation.fetch_data(offsets)
                text = block.dumps()
                assert "sim-token-" not in text and "sim-admin-control" not in text
                (tmp_path / f"cells-{index}.json").write_text(text, encoding="utf-8")
                hashes.append(hashlib.sha256(text.encode()).hexdigest())
            (tmp_path / "expected.json").write_text(json.dumps({"values": oracle["values"],
                "runtime_id": simulator["service_ids"]["runtime"], "geometry": block.geometry.to_wire()}), encoding="utf-8")
        finally:
            context.release()
            session.release()
    before = control(simulator)
    assert not any(before["counts"].values())
    env = {key: value for key, value in os.environ.items() if not key.startswith("KLAB_") and key != "PYTHONPATH"}
    env["PYTHONPATH"] = str(Path(klab_client.__file__).resolve().parents[1])
    result = subprocess.run([sys.executable, str(ROOT / "tests/offline_snapshot_worker.py"), str(tmp_path)],
        cwd=tmp_path, env=env, capture_output=True, text=True, timeout=15,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    assert result.returncode == 0, result.stderr
    report = json.loads(result.stdout)
    assert report["cells"] == 400 and report["sha256"] == hashes
    assert report["network_permitted"] is False and report["remote_authorization"] == "NOT_RECHECKED"
    after = control(simulator)
    assert not any(after["counts"].values())
    service_trace = lambda report: [row for row in report["trace"] if not row["path"].startswith("/__sim__/")]
    assert service_trace(after) == service_trace(before)
