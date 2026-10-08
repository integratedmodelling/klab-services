"""Fresh-process helper for the deterministic local-HTTP integration test."""
import argparse
import json
from pathlib import Path

from klab_client import Client, ObservationImpl, load_handle, save_handle
from klab_client.experiment import rectangle_geometry

parser = argparse.ArgumentParser()
parser.add_argument("mode", choices=("create", "restore"))
parser.add_argument("directory", type=Path)
args = parser.parse_args()
with Client.from_env() as client:
    if args.mode == "create":
        session = client.create_session()
        context = session.create_context(configuration={"persistence": "EXPLICIT_ACTION"})
        observable = client.reasoner.resolve_observable("geography:Elevation in m")
        job = context.submit(ObservationImpl(urn="", observable=observable, geometry=rectangle_geometry()))
        observation = job.result(10)
        save_handle(context, args.directory / "context.json")
        save_handle(job, args.directory / "job.json")
        save_handle(observation, args.directory / "observation.json")
        print(json.dumps({"created": True, "job_id": job.id, "observation_id": observation.id}))
    else:
        context = client.restore_handle(load_handle(args.directory / "context.json"))
        job = client.restore_handle(load_handle(args.directory / "job.json"))
        observation = client.restore_handle(load_handle(args.directory / "observation.json"))
        assert context.owned is False and observation.id == job.result(10).id == 42
        assert observation.fetch_data([0]).values == (123.25,)
        print(json.dumps({"restored": True, "job_id": job.id, "observation_id": observation.id}))
