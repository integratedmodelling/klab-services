"""Run with: python examples/elevation.py (see README for deployment setup)."""
import os

from klab_client import Client
from klab_client.experiment import run_elevation


def main():
    with Client.from_env() as client:
        id = os.environ.get("KLAB_CONTEXT_ID")
        context = client.attach_context(id) if id else None
        run_elevation(client, context=context)


if __name__ == "__main__":
    main()
