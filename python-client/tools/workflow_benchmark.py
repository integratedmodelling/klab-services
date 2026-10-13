"""Compatibility entry point for the compute-and-analyze functional workflow."""
from workflows.compute_and_analyze import (
    ResourceMonitor, initialize, main, make_client, read_sample, run_workload, sample, summarize,
)

__all__ = ["ResourceMonitor", "initialize", "main", "make_client", "read_sample",
           "run_workload", "sample", "summarize"]

if __name__ == "__main__":
    raise SystemExit(main())
