"""Fresh process restores existing scientific work through the Connection facade."""
import argparse
import json

from klab_client import load_handle
from klab_client.errors import InvalidRequestError
from workflows.connection import connected_client


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", required=True)
    parser.add_argument("--provider", choices=("certificate", "token"), required=True)
    parser.add_argument("handle")
    parser.add_argument("destination")
    args = parser.parse_args()
    reference = load_handle(args.handle)
    if reference.kind != "observation":
        raise InvalidRequestError("Recovery requires the original observation handle")
    with connected_client(args.state_dir, args.provider) as client:
        observation = client.restore_handle(reference)
        result = observation.export_to_file(args.destination, media_type="application/geo+json")
        print(json.dumps({"observation_id": observation.id, "bytes": result.bytes_written}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
