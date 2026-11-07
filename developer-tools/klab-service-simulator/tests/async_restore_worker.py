"""SDK-only owned subprocess restoration, not native durable storage evidence."""
import asyncio
import json
from pathlib import Path
import sys

import klab_client
from klab_client import AsyncClient, Endpoint, load_handle


def require(condition, message):
    if not condition:
        raise AssertionError(message)


async def main(ready, directory):
    urls = ready['endpoints']
    token = 'sim-token-alice'
    options = dict(credential=token, agent_name='alice', runtime_service_id=ready['service_ids']['runtime'],
                   reasoner=Endpoint(urls['reasoner'], token), resources=Endpoint(urls['resources'], token),
                   resolver=Endpoint(urls['resolver'], token), timeout=3, poll_interval=.001)
    async with AsyncClient(urls['runtime'], **options) as client:
        context = await client.restore_handle(load_handle(directory / 'context.json'))
        job = await client.restore_handle(load_handle(directory / 'job.json'))
        observation = await client.restore_handle(load_handle(directory / 'observation.json'))
        require(not context.owned, 'Restored Context unexpectedly gained ownership')
        require((await job.result(timeout=2)).id == observation.id, 'Restored Job/result association differs')
        values = (await observation.fetch_data((0, 19, 380, 399))).values
        require(values == (100, 157, 138, 195), 'Exact finite reference cells differ after restart')
        require('klab_simulator' not in sys.modules, 'SDK-only restoration imported fixture internals')
        result = dict(context=context.id, job=job.id, observation=observation.id, values=list(values),
                      interpreter=sys.executable, sdk_file=klab_client.__file__, native='NOT_RUN')
    # Local async close is checked through the parent's independent HTTP trace.
    print(json.dumps(result))


if __name__ == '__main__':
    asyncio.run(main(json.loads(sys.argv[1]), Path(sys.argv[2])))
