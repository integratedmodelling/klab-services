"""Read an existing permitted observation into a local table; no calculation replay."""
import argparse
import json

from klab_client import Client, load_handle


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--handle", required=True, help="Existing observation handle; contains no credentials")
    parser.add_argument("--offset", action="append", required=True, type=int)
    parser.add_argument("--curve", required=True, help="Explicit server fill curve, not inferred axis order")
    parser.add_argument("--slice-json", help="Explicit known StorageScan.Slice JSON for temporal reads; no time discovery is implied")
    args = parser.parse_args()
    reference = load_handle(args.handle)
    if reference.kind != "observation":
        parser.error("Use an existing computed observation handle, not a request, context or pending Job")
    try:
        selected_slice = json.loads(args.slice_json) if args.slice_json is not None else None
    except ValueError:
        parser.error("Invalid slice JSON")
    with Client.from_env() as client:
        result = client.restore_handle(reference)
        data = result.fetch_data(args.offset, curve=args.curve, slice=selected_slice)
        frame = data.to_dataframe()
        print(frame)
        print(frame.attrs["klab"])
        # Local close only. Do not release an attached user's context or pretend a
        # CSV/edited DataFrame preserves authoritative k.LAB arithmetic/provenance.


if __name__ == "__main__":
    main()
