"""One fresh SDK process against an already-owned simulator; never starts a service."""
import json
from pathlib import Path
import sys

from klab_client import Client, Endpoint, ObservationRequest, RectangularGeometry, load_handle, save_handle


def main():
    mode, descriptor, directory = sys.argv[1:]
    ready = json.loads(descriptor)
    directory = Path(directory)
    endpoints = ready["endpoints"]
    with Client(endpoints["runtime"], "sim-token-alice", agent_name="alice",
                runtime_service_id=ready["service_ids"]["runtime"],
                reasoner=Endpoint(endpoints["reasoner"], "sim-token-alice"), poll_interval=.001) as client:
        if mode == "create":
            session = client.create_session()
            context = session.create_context()
            job = context.observe(ObservationRequest("geography:Elevation in m",
                geometry=RectangularGeometry(0, 2000, 0, 2000, 20, 20)))
            observation = job.result(timeout=2)
            save_handle(context, directory / "context.json")
            save_handle(job, directory / "job.json")
            save_handle(observation, directory / "observation.json")
        else:
            context = client.restore_handle(load_handle(directory / "context.json"))
            job = client.restore_handle(load_handle(directory / "job.json"))
            observation = client.restore_handle(load_handle(directory / "observation.json"))
            assert not context.owned
            assert job.result(timeout=2).id == observation.id
            assert observation.fetch_data((0, 19, 380, 399)).values == (100, 157, 138, 195)
        print(json.dumps({"backend": "SIMULATED", "mode": mode, "context": context.id,
                          "job": job.id, "observation": observation.id}))


if __name__ == "__main__":
    main()
