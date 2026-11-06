"""Ordinary scientist SDK + separate worker + explicit simulator protocol proposal."""
import argparse
import json
from pathlib import Path
from urllib.parse import urlsplit

from klab_client import Client
from klab_worker import Field, WorkerClient


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", default="http://127.0.0.1:38000/runtime")
    args = parser.parse_args()
    url = urlsplit(args.runtime)
    if url.hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("Synthetic starter is restricted to an explicitly local simulator")
    field = Field((2, 3), ("y", "x"), ((50, -50), (10, 20, 30)), "EPSG:3857", "m",
        (0, -3.5, None, 7.25, 1.5, 8), (True, True, False, True, True, True))
    with Client(args.runtime, "sim-token-alice", agent_name="alice", runtime_service_id="sim-runtime-7") as scientist:
        scientist.initialize_user_scope()
        session = scientist.create_session()
        context = session.create_context()
        try:
            with WorkerClient(args.runtime, "sim-token-alice", scope=context.id, allow_experimental=True) as owner, WorkerClient(
                    args.runtime, "sim-token-worker-alice", scope=context.id, allow_experimental=True) as worker:
                worker.register("example", operation="double")
                try:
                    submitted = owner.submit(field, operation="double")
                    worker.run_once("example", Path(__file__).with_name("double_field.py"))
                    result = owner.status(submitted["task_id"])
                    assert Field.from_dict(result["output"]).values == (0, -7, None, 14.5, 3, 16)
                    print(json.dumps({"evidence": "SIMULATED_PROVISIONAL", "origin": "SYNTHETIC_SIMULATOR",
                        "java": "REAL_NOT_RUN", "gap": "JAVA-GAP-0017", "task_state": result["state"], "values": result["output"]["values"]}))
                finally:
                    worker.release("example")
        finally:
            context.release(); session.release()


if __name__ == "__main__":
    main()
