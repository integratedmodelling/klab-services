"""Fresh optimized SDK-only async restoration after owned reference process restart."""
import json
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlsplit

import klab_client
from klab_client import Client, ObservationRequest, save_handle
from conftest import launch
from test_vertical import client_args, control, geometry


def test_fresh_async_sdk_only_process_restores_after_restart_without_replay_or_ownership(tmp_path):
    state = tmp_path / 'owned.klab-sim.sqlite'
    with launch(state_file=state) as ready:
        port = urlsplit(ready['endpoints']['runtime']).port
        with Client(ready['endpoints']['runtime'], **client_args(ready)) as client:
            session = client.create_session()
            context = session.create_context()
            job = context.observe(ObservationRequest('geography:Elevation in m', geometry=geometry()))
            observation = job.result(timeout=2)
            for name, value in (('context', context), ('job', job), ('observation', observation)):
                save_handle(value, tmp_path / (name + '.json'))
    with launch(port=port, state_file=state) as restarted:
        assert restarted['service_ids'] == ready['service_ids']
        before = control(restarted)
        env = {key: value for key, value in os.environ.items() if not key.startswith('KLAB_')}
        env['PYTHONPATH'] = str(Path(klab_client.__file__).resolve().parents[1])
        env['PYTHONOPTIMIZE'] = '1'
        try:
            result = subprocess.run([sys.executable, str(Path(__file__).with_name('async_restore_worker.py')),
                                     json.dumps(restarted), str(tmp_path)], env=env, capture_output=True,
                                    text=True, timeout=30, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            assert result.returncode == 0, result.stderr
            assert 'sim-token-' not in result.stdout + result.stderr
            restored = json.loads(result.stdout)
            assert restored['context'] == context.id and restored['job'] == job.id
            assert restored['observation'] == observation.id and restored['values'] == [100, 157, 138, 195]
            assert Path(restored['interpreter']).resolve() == Path(sys.executable).resolve()
            assert Path(restored['sdk_file']).resolve() == Path(klab_client.__file__).resolve()
            assert restored['native'] == 'NOT_RUN'
            after = control(restarted)
            assert before['counts'] == after['counts']
            calls = after['trace'][len(before['trace']):]
            assert not any(row['path'].endswith(('/createSession', '/createContext', '/submit',
                                                  '/releaseContext', '/releaseSession')) for row in calls)
        finally:
            # Explicit current owner cleanup, not hidden restoration/local-close behavior.
            with Client(restarted['endpoints']['runtime'], **client_args(restarted)) as cleanup:
                cleanup.attach_context(context.id).release()
                cleanup.runtime.release_session(session.id)
        assert not any(control(restarted)['counts'].values())
