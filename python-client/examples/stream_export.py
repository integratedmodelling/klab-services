"""Download an existing observation handle; credentials/endpoints come from the caller."""
import argparse
import json

from klab_client import Client, load_handle
from klab_client.errors import InvalidRequestError


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("handle", help="Version-1 observation handle JSON")
    parser.add_argument("destination")
    parser.add_argument("--media-type", required=True, help="Exact media type advertised by the Runtime")
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--max-bytes", type=int)
    args = parser.parse_args()
    reference = load_handle(args.handle)
    if reference.kind != "observation":
        raise InvalidRequestError("This example requires an observation handle")
    with Client.from_env() as client:
        observation = client.restore_handle(reference)
        result = observation.export_to_file(args.destination, media_type=args.media_type,
            overwrite=args.overwrite, max_bytes=args.max_bytes)
        print(json.dumps({"path": str(result.path), "bytes": result.bytes_written,
            "media_type": result.media_type, "schema_id": result.schema_id}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
