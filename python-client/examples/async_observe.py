"""Explicit async observation in an existing context; does not dispose user work.

Use issued credentials/configured URLs from the same documented KLAB_* environment
as Client.from_env. Nothing runs on import. No automatic submission retries.
"""
import argparse
import asyncio

from klab_client import AsyncClient, ObservationRequest


async def run(context_id, definition, timeout):
    async with AsyncClient.from_env() as client:
        await client.runtime.capabilities()
        context = await client.attach_context(context_id)
        job = await context.observe(ObservationRequest(definition))
        print(f"Submitted Job {job.id}; timeout/cancellation retains this Job, not permission to resubmit")
        result = await job.result(timeout=timeout)
        print(f"Observation {result.id}; units={result.units!r}")
        # Closing Python is local detachment, not context release. Retain original
        # context/Job identifiers to resume with context.job(id) in an async client.
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--context", required=True)
    parser.add_argument("--observable", required=True)
    parser.add_argument("--timeout", type=float, default=120)
    parser.add_argument("--allow-observation", action="store_true")
    args = parser.parse_args()
    if not args.allow_observation:
        parser.error("--allow-observation is required: this creates remote scientific work")
    asyncio.run(run(args.context, args.observable, args.timeout))


if __name__ == "__main__":
    main()
