"""Fresh-process entry point for the shared scientific analysis/provenance workflow."""
import argparse
import json
from pathlib import Path

from workflows.scientific_analysis import analyze_saved


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--configuration", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--connection-state", type=Path, help="Use the prepared token-provider connection instead of direct Client")
    args = parser.parse_args()
    factory = None
    if args.connection_state:
        from workflows.connection import connected_client
        factory = lambda: connected_client(args.connection_state, "token")
    try:
        evidence = analyze_saved(json.loads(args.configuration.read_text()), args.bundle, args.bundle / "field.tif", client_factory=factory)
    except Exception as error:
        # Do not echo response bodies, enrollment material or credentials into public logs.
        (args.bundle / "analysis-error.json").write_text(json.dumps({"error_type": type(error).__name__}))
        return 1
    (args.bundle / "analysis.json").write_text(json.dumps(evidence, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
