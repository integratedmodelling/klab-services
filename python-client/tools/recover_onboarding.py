"""Fresh-process Connection provider acquisition and original native geometry read."""
import argparse

from workflows.onboarding_journey import recover


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", required=True)
    parser.add_argument("--provider", choices=("certificate", "token"), required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("handle")
    parser.add_argument("destination")
    args = parser.parse_args()
    recover(args.state_dir, args.provider, args.handle, args.destination, args.report)
