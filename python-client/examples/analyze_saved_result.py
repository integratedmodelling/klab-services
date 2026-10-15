"""Analyze and inspect recorded origins for an existing saved observation (no resubmission)."""
import argparse

from klab_client import (Client, export_for_analysis, fetch_provenance, load_handle,
    open_raster, trace_analysis)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("handle")
    parser.add_argument("output", help="Fresh local TIFF destination")
    args = parser.parse_args()
    with Client.from_env() as client:
        observation = client.restore_handle(load_handle(args.handle))
        analysis = open_raster(export_for_analysis(observation, args.output, max_bytes=64 * 1024**2))
        provenance = fetch_provenance(observation)
        traced = trace_analysis(analysis, provenance)
        print(analysis.dataset)
        print("Partial recorded links:", len(provenance.links))
        print(traced.consistency)


if __name__ == "__main__":
    main()
