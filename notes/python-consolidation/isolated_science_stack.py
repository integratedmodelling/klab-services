"""Owned loopback-only consolidation stack; never attach to or stop existing services.

Uses the installed client, compiled current reactor and separately labelled v4
synthetic fixture. All processes are hidden, bounded and cleaned in finally.
No automatic replay of scientific or administrative mutations.
"""
import argparse
import hashlib
import importlib.metadata
import importlib.util
import json
import os
import secrets
import shutil
import socket
import subprocess
import sys
import time
import traceback
import uuid
import xml.etree.ElementTree as ET
from decimal import Decimal
from pathlib import Path

import httpx
import psutil

ROOT = Path(__file__).resolve().parents[2]
TEMP = Path('C:/Users/lumsd/AppData/Local/Temp/opencode')
JAVA = Path('C:/Users/lumsd/AppData/Local/Programs/Temurin21/jdk-21.0.12.1+1/bin/java.exe')
PORTS = {'reasoner': 38091, 'resources': 38092, 'resolver': 38093, 'runtime': 38094}
GRAPH = (38382, 37687, 37474)
HUB_PORT = 18090
CORE_HASH = '5c020dac9bd9d1cd4f9673b8075cf08f75ecc7194c74378bdb8f0498110ddba3'


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def sha(file):
    return hashlib.sha256(file.read_bytes()).hexdigest()


def prepare_fixture(state):
    recipe = load('closeout_fixture_recipe', ROOT / 'python-client/tools/prepare_runtime_fixture.py')
    base = recipe.CONTRACT.read_bytes()
    variant = json.loads(recipe.EXECUTION_CONTRACT.read_text())
    if recipe._sha(base) != variant['base_contract_sha256']:
        raise RuntimeError('Frozen fixture contract changed')
    contract = json.loads(base)
    model = variant['model']
    if contract['oracle']['unchanged_files'].pop(model['path']) != model['input_sha256']:
        raise RuntimeError('Frozen numerical model preimage changed')
    contract['changes'].append({key: model[key] for key in ('path', 'input_sha256', 'output_sha256', 'replacements')})
    contract['oracle']['derived_model'] = {key: model[key] for key in ('path', 'input_sha256', 'output_sha256', 'constant_expression')}
    core = ROOT / 'klab.services.reasoner/src/main/resources/knowledge/odo.owl'
    if sha(core) != CORE_HASH:
        raise RuntimeError('Current-core candidate differs from reviewed bytes')
    # Separate v4 identity; never change the frozen v2/v3 expectations or their failures.
    contract['version'] = 4
    contract['backend_core_ontology']['sha256'] = CORE_HASH
    contract['execution_contract'] = dict(variant, consolidation_version=4,
        core_change='Additive impliesPredicate property, commit 0e77650abc2bfb5db072fc00eb4f5a44f2d2cd19; constant-model oracle unchanged',
        original_frozen_acceptance='PRESERVED; old-core hash test remains failed on current core')
    (state / 'fixture-v4-contract.json').write_text(json.dumps(contract, indent=2))
    source = TEMP / 'runtime-notification-job-live-6-20261102/projects'
    destination = state / 'projects-v4'
    receipt = recipe._prepare(source, destination, contract)
    if receipt['oracle']['expected_decimal'] != '123.25' or receipt['oracle']['cells'] != 20:
        raise RuntimeError('Independent numerical oracle changed')
    return {p.name: p for p in destination.iterdir() if p.is_dir()}


def prepare_minimal_fixture(state):
    """A new synthetic worldview, not an alteration/acceptance of imod or v4."""
    if sha(ROOT / 'klab.services.reasoner/src/main/resources/knowledge/odo.owl') != CORE_HASH:
        raise RuntimeError('Current-core bytes differ from the reviewed candidate')
    world = state / 'projects-v5/consolidation'
    model = state / 'projects-v5/consolidation.fixture'
    for project in (world, model):
        (project / 'META-INF').mkdir(parents=True)
        (project / 'src').mkdir()
    (world / 'META-INF/manifest.json').write_text(json.dumps({
        'description': 'Minimal synthetic consolidation worldview; not the shared imod worldview',
        'version': {'major': 1, 'minor': 0, 'build': 0}, 'definedWorldview': 'consolidation',
        'privileges': {'public': True}, 'prerequisiteProjects': [],
        'metadata': {'klab.user.observer': 'consolidation:Scientist'}}))
    (world / 'src/consolidation.kwv').write_text('''ontology consolidation version 1.0 in domain root
  with core "http://integratedmodelling.org/odo/0.2.0" as odo;
abstract configuration Configuration is core odo:Configuration;
abstract thing Subject is core odo:Subject;
abstract agent Agent is core odo:Agent;
abstract process Process is core odo:Process;
abstract event Event is core odo:Event;
abstract relationship Relationship is core odo:Relationship;
abstract quality Quality is core odo:Quality;
abstract quantity Quantity is core odo:QuantifiableQuality;
abstract length Length is core odo:Length;
abstract identity Identity is core odo:Identity;
abstract attribute Attribute is core odo:Attribute;
abstract realm Realm is core odo:Realm;
abstract role Role is core odo:Role;
thing Region is consolidation:Subject;
agent Scientist is consolidation:Agent;
length Elevation is consolidation:Length of consolidation:Region;
''')
    # Existing generic native strategies are unchanged; they contain no imod names.
    source = TEMP / 'runtime-notification-job-live-6-20261102/projects/imod/strategies/observations.obs'
    (world / 'strategies').mkdir()
    shutil.copy2(source, world / 'strategies/observations.obs')
    (model / 'META-INF/manifest.json').write_text(json.dumps({
        'description': 'Controlled Java expression, not measured elevation or a published-study replication',
        'version': {'major': 1, 'minor': 0, 'build': 0}, 'worldview': 'consolidation',
        'privileges': {'public': True}, 'prerequisiteProjects': [], 'metadata': {}}))
    (model / 'src/consolidation.fixture.kim').write_text('''namespace consolidation.fixture;
model consolidation:Elevation of consolidation:Region in m set to [100 + 23.25];
''')
    files = {p.relative_to(state / 'projects-v5').as_posix(): sha(p)
             for p in (state / 'projects-v5').rglob('*') if p.is_file()}
    (state / 'fixture-v5-contract.json').write_text(json.dumps({
        'version': 5, 'kind': 'separate minimal synthetic worldview; no shared worldview migration',
        'core_sha256': CORE_HASH, 'files': files,
        'oracle': {'independent_calculation': '100 + 93/4', 'expected_decimal': '123.25',
                   'units': 'm', 'cells': 20, 'missing_cells': 0, 'crs': 'EPSG:3857'},
        'semantic_admission': 'NOT_YET_VERIFIED', 'original_frozen_acceptance_replaced': False}, indent=2))
    return {'consolidation': world, 'consolidation.fixture': model}


def classpath(module):
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    modules = {}
    for pom in ROOT.glob('**/pom.xml'):
        if 'target' not in pom.parts:
            artifact = ET.parse(pom).getroot().findtext('m:artifactId', namespaces=ns)
            if (pom.parent / 'target/classes').is_dir():
                modules[artifact] = pom.parent / 'target/classes'
    file = ROOT / module / 'local-stack-classpath.txt'
    if not file.is_file():
        file = ROOT / module / 'target/local-stack-classpath.txt'
    parts = [str(ROOT / module / 'target/classes')]
    for raw in file.read_text().strip().split(os.pathsep):
        path = Path(raw)
        parts.append(str(modules.get(path.parent.parent.name, path)))
    return os.pathsep.join(dict.fromkeys(parts))


def run(state, minimal=False, geospatial_component=None, managed_agent=False):
    import klab_client
    from klab_client import (
        Client,
        ContextOptions,
        Endpoint,
        ObservationRequest,
        RectangularGeometry,
    )
    from klab_client.errors import KlabError
    if geospatial_component is not None:
        geospatial_component = geospatial_component.absolute()
        if (not minimal or not geospatial_component.is_file()
                or geospatial_component.resolve() != geospatial_component):
            raise RuntimeError('New component requires an explicit direct regular file and the minimal owned fixture')
    if Path(klab_client.__file__).resolve().is_relative_to(ROOT):
        raise RuntimeError('Installed noneditable client required')
    owned_ports = (*PORTS.values(), *GRAPH, HUB_PORT, *((38095,) if managed_agent else ()))
    for port in owned_ports:
        with socket.socket() as check:
            check.bind(('127.0.0.1', port))
    state.mkdir(exist_ok=False)
    owner = subprocess.check_output(['whoami', '/user', '/fo', 'csv', '/nh'], text=True).strip().split(',')[-1].strip('"')
    subprocess.run(['icacls', str(state), '/grant:r', f'*{owner}:(OI)(CI)F',
                    '*S-1-5-18:(OI)(CI)F', '*S-1-5-32-544:(OI)(CI)F', '/inheritance:r'],
                   check=True, capture_output=True)
    shutil.copy2(Path(__file__), state / 'launcher-source.py')
    package = Path(klab_client.__file__).parent
    (state / 'installed-client-identity.json').write_text(json.dumps({
        'path': str(package), 'files': {p.relative_to(package).as_posix(): sha(p)
                                     for p in package.rglob('*.py')}}, indent=2))
    (state / 'python-freeze.txt').write_bytes(subprocess.check_output([sys.executable, '-m', 'pip', 'freeze']))
    version = subprocess.run([str(JAVA), '-version'], capture_output=True, check=True)
    (state / 'java-version.txt').write_bytes(version.stdout + version.stderr)
    classes = {p.relative_to(ROOT).as_posix(): sha(p) for p in ROOT.glob('**/target/classes/**/*.class')}
    (state / 'compiled-classes.json').write_text(json.dumps(classes, indent=2))
    processes, handles = [], []
    existing = {p.pid: p.create_time() for p in psutil.process_iter() if p.name().lower() in ('java.exe', 'javaw.exe')}
    result = {'status': 'IN_PROGRESS', 'client_file': klab_client.__file__,
              'client_version': importlib.metadata.version('klab-python-client'),
              'launcher_sha256': sha(Path(__file__).resolve()),
              'candidate': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'ports': PORTS, 'graph_ports': GRAPH, 'scientific_jobs_submitted': 0,
              'original_frozen_acceptance_replaced': False}
    if geospatial_component is not None:
        result['dependency_proposal'] = {'component_path': str(geospatial_component),
            'sha256': sha(geospatial_component), 'component_pr': 11, 'host_pr': 194,
            'adoption': 'TEST_ONLY; GitHub acceptance recorded separately'}
    def spawn(name, command, cwd):
        log = (state / f'{name}.log').open('xb'); handles.append(log)
        env = {k: v for k, v in os.environ.items() if not k.startswith('KLAB_')
               and k not in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')}
        p = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=log,
                             creationflags=subprocess.CREATE_NO_WINDOW)
        processes.append((name, p)); return p
    try:
        broker_uri = None
        if managed_agent:
            if not minimal:
                raise RuntimeError('Managed acceptance requires the explicit minimal fixture')
            broker_profile = {'port': 38095, 'username': 'owned-agent',
                              'password': secrets.token_urlsafe(32), 'vhost': 'owned.agent'}
            profile_file = state / 'private-agent-broker.json'
            profile_file.write_text(json.dumps(broker_profile))
            broker_uri = ('amqp://' + broker_profile['username'] + ':' + broker_profile['password']
                          + '@127.0.0.1:38095/owned.agent')
            args = ['-Xmx256m', '-DQPID_WORK=' + str(state / 'broker-work'), '--class-path',
                    classpath('support/klab.amqp.server'), str(ROOT / 'notes/python-consolidation/OwnedAgentBroker.java'),
                    str(profile_file)]
            file = state / 'private-agent-broker.args'
            file.write_text('\n'.join('"' + a.replace('\\', '/') + '"' for a in args))
            broker_process = spawn('owned-agent-broker', [str(JAVA), '@' + str(file)], ROOT)
            deadline = time.monotonic() + 40
            while time.monotonic() < deadline and broker_process.poll() is None:
                with socket.socket() as probe:
                    if probe.connect_ex(('127.0.0.1', 38095)) == 0:
                        break
                time.sleep(.2)
            else:
                raise RuntimeError('Owned broker did not start; no managed registration attempted')
        projects = prepare_minimal_fixture(state) if minimal else prepare_fixture(state)
        worldview = 'consolidation' if minimal else 'imod'
        hub = load('closeout_authority', ROOT / 'python-client/tools/fixture_hub.py')
        hub.SERVICES = PORTS
        key = hub.rsa.generate_private_key(public_exponent=65537, key_size=2048)
        secret = secrets.token_urlsafe(32)
        (state / 'fixture-authority.pem').write_bytes(key.private_bytes(hub.serialization.Encoding.PEM,
            hub.serialization.PrivateFormat.PKCS8, hub.serialization.NoEncryption()))
        (state / 'fixture-authority.json').write_text(json.dumps({'enrollment_secret': secret}))
        for name, port in PORTS.items():
            directory = state / 'home/.klab/services' / name; directory.mkdir(parents=True)
            (directory / 'service.cert').write_text('\n'.join([
                f'klab.certificate.type={name.upper()}', 'klab.certificate.level=TEST',
                f'klab.nodename=python-test-{name}', f'klab.partner.hub=http://127.0.0.1:{HUB_PORT}',
                'klab.partner.email=test@example.invalid', f'klab.url=http://127.0.0.1:{port}/{name}',
                f'klab.certificate={secret}', f'klab.signature={secret}', '']))
            config = {'serviceId': str(uuid.uuid4())}
            if name == 'runtime':
                config.update(url=f'http://127.0.0.1:{port}/{name}', graphDatabaseUrl=f'bolt://127.0.0.1:{GRAPH[1]}')
            if name == 'resources':
                config.update(servicePath=name, workspaces={'consolidation-v4': list(projects)},
                    projectConfiguration={n: {'served': True, 'worldview': n == worldview, 'locallyManaged': True,
                        'authoritative': False, 'syncIntervalMinutes': 0, 'localPath': str(p), 'workspaceName': 'consolidation-v4',
                        'storageType': 'FILE', 'privileges': {'public': True}} for n, p in projects.items()})
            (directory / f'{name}.yaml').write_text(json.dumps(config))
        spawn('authority', [sys.executable, str(Path(__file__).resolve()), 'authority', '--state', str(state)], ROOT)
        stack = load('closeout_stack_contract', ROOT / 'python-client/tools/local_stack.py')
        launches = {}
        for name in ('graphdb', 'resources', 'reasoner', 'resolver', 'runtime'):
            module, main, _, route = stack.SERVICES[name]
            port = GRAPH[0] if name == 'graphdb' else PORTS[name]
            args = ['-Xmx768m', '-Xlog:class+load=info', '-Duser.home=' + str(state / 'home'), '-Dserver.address=127.0.0.1',
                    f'-Dserver.port={port}',
                    '-Dspring.main.banner-mode=off', '-cp', classpath(module), main]
            if broker_uri is not None:
                args.insert(0, '-Dklab.local.broker.uri=' + broker_uri)
            if name == 'graphdb':
                args += [f'--server.port={port}', f'--app.neo4j.bolt-port={GRAPH[1]}', f'--app.neo4j.http-port={GRAPH[2]}']
            else:
                args += ['-dataDir', str(state / 'home/.klab'), '-port', str(port), '-contextPath', '/' + name,
                         '-cert', str(state / f'home/.klab/services/{name}/service.cert')]
            argfile = state / f'{name}.args'
            argfile.write_text('\n'.join('"' + a.replace('\\', '/').replace('"', '\\"') + '"' for a in args))
            p = spawn(name, [str(JAVA), '@' + str(argfile)], ROOT / module)
            launches[name] = (p, [str(JAVA), '@' + str(argfile)], ROOT / module, route)
            deadline = time.monotonic() + 120
            with httpx.Client(trust_env=False, timeout=2) as http:
                while time.monotonic() < deadline and p.poll() is None:
                    try:
                        if http.get(f'http://127.0.0.1:{port}{route}').status_code == 200:
                            break
                    except httpx.TransportError:
                        pass
                    time.sleep(.5)
                else:
                    raise RuntimeError(f'{name} startup did not reach its owned public endpoint')
            print(name, 'owned public endpoint ready; not scientific acceptance', flush=True)
        result['startup'] = 'PASSED'
        if minimal:
            journal = []
            result['component_imports'] = journal
            components = TEMP / 'runtime-notification-job-live-6-20261102/components'
            selected_components = sorted(components.glob('klab.component.*.kar'))
            if geospatial_component is not None:
                selected_components = [p for p in selected_components if 'geospatial' not in p.name]
                selected_components.append(geospatial_component)
            with httpx.Client(trust_env=False, timeout=30) as http:
                for service in ('resources', 'runtime'):
                    base = f'http://127.0.0.1:{PORTS[service]}/{service}'
                    headers = {'Authorization': '018fc355-c123-7608-be4a-89ea1059c33e',
                        'server-key': (state / f'home/.klab/services/{service}/secret.key').read_text().strip()}
                    for file in selected_components:
                        if len(journal) >= 4:
                            raise RuntimeError('Owned administrative import budget exceeded')
                        entry = {'service': service, 'file': file.name, 'sha256': sha(file), 'state': 'INTENT_UNKNOWN'}
                        journal.append(entry)
                        (state / 'import-journal.json').write_text(json.dumps(journal, indent=2))
                        if (geospatial_component is not None and file == geospatial_component
                                and sha(file) != result['dependency_proposal']['sha256']):
                            raise RuntimeError('Test component bytes changed before import')
                        # The product plugin emits a flat component JAR; .kar is the
                        # import-protocol filename for those same unmodified bytes.
                        filename = file.name if file.suffix == '.kar' else file.stem + '.kar'
                        with file.open('rb') as stream:
                            response = http.post(base + '/import/component.kar.import/X:X:X:X', headers=headers,
                                files={'file': (filename, stream, 'application/java-archive')})
                        if response.status_code != 200:
                            raise RuntimeError(f'Owned {service} component import HTTP {response.status_code}; never replay')
                        job = response.json()
                        if type(job) is not int or job <= 0:
                            raise RuntimeError('Import returned no valid Job ID; never replay')
                        entry.update(job=job, state='JOB_RETURNED')
                        deadline = time.monotonic() + 120
                        while time.monotonic() < deadline:
                            response = http.get(base + f'/jobs/status/{job}', headers=headers)
                            response.raise_for_status()
                            status = response.json().get('status')
                            if status not in ('WAITING', 'STARTED', 'CHANGED'):
                                if status != 'FINISHED':
                                    raise RuntimeError('Owned component import failed; never replay')
                                entry['state'] = 'CONFIRMED_FINISHED'
                                break
                            time.sleep(.5)
                        else:
                            raise RuntimeError('Owned import outcome unknown at deadline; never replay')
                        (state / 'import-journal.json').write_text(json.dumps(journal, indent=2))
            # Existing project indexing precedes component imports; restart only our Resources.
            old, command, cwd, route = launches['resources']
            old.terminate(); old.wait(timeout=20)
            current = spawn('resources-reindexed', command, cwd)
            deadline = time.monotonic() + 120
            with httpx.Client(trust_env=False, timeout=2) as http:
                while time.monotonic() < deadline and current.poll() is None:
                    try:
                        if http.get(f'http://127.0.0.1:{PORTS["resources"]}{route}').status_code == 200:
                            break
                    except httpx.TransportError:
                        pass
                    time.sleep(.5)
                else:
                    raise RuntimeError('Owned Resources reindex restart failed')
        hub.issue(state, key)
        config = json.loads((state / 'scientist.json').read_text())
        with Client(config['urls']['runtime'], config['token'], agent_name=config['username'],
                    **{n: Endpoint(config['urls'][n], config['token']) for n in ('reasoner', 'resources', 'resolver')}) as client:
            client.initialize_user_scope(local_federation=True)
            result['authentication_and_selection'] = 'PASSED'
            # Discovery must succeed before allocating any scientific context or Job.
            definition = ('consolidation:Elevation of consolidation:Region in m' if minimal else 'geography:Elevation in m')
            meaning = client.reasoner.resolve_observable(definition)
            if meaning is None:
                raise RuntimeError('Current synthetic worldview was not semantically admitted')
            result['semantic_discovery'] = 'PASSED'
            if minimal:
                from klab_client import (
                    fetch_execution_evidence,
                    fetch_provenance,
                    restore_handle,
                    to_handle,
                )
                from klab_client.experiment import verify_grid
                session = context = None
                try:
                    session = client.create_session(name='owned-consolidation-synthetic-v5')
                    context = session.create_context(options=ContextOptions(name='Independent constant-field oracle',
                        persistence='EXPLICIT_ACTION'))
                    result['owned_scope_ids'] = {'session': session.id, 'context': context.id}
                    if managed_agent:
                        from klab_client import AgentExecutions, NotificationBroker
                        lifecycle = {'registration': 'INTENT_UNKNOWN', 'start': 'NOT_SENT', 'stop': 'NOT_SENT',
                                     'remote_cleanup': 'UNKNOWN', 'states': []}
                        result['managed_agent'] = lifecycle
                        creator = AgentExecutions(context, principal_key='owned-scientist',
                            runtime_incarnation='owned-jvm-' + str(launches['runtime'][0].pid))
                        agent = creator.create('behavior consolidation.ownedagent\n  "Owned acceptance"\n  version 1.0\n  using core.console as console\n\naction main:\n  console.println("owned acceptance")\n',
                                               allow_execute=True)
                        lifecycle.update(registration='CONFIRMED', agent_urn=agent.receipt.binding.agent_urn,
                                         messaging_connected=agent.receipt.messaging_connected)
                        try:
                            agent.attach(NotificationBroker('amqp://127.0.0.1:38095/owned.agent',
                                broker_profile['username'], broker_profile['password'], allow_insecure_loopback=True),
                                federation_id='local.federation', timeout=5, publication='unconfirmed')
                            def wait_state(expected):
                                deadline = time.monotonic() + 20
                                while time.monotonic() < deadline:
                                    message = agent.read()
                                    if message is not None and message.status is not None:
                                        lifecycle['states'].append(message.status.state)
                                        if message.status.state == expected:
                                            return
                                raise RuntimeError('Managed lifecycle state not observed before deadline')
                            agent.request_status()
                            wait_state('READY')
                            lifecycle['start'] = 'INTENT_UNKNOWN'
                            lifecycle['start'] = agent.start().start_outcome
                            wait_state('RUNNING')
                            lifecycle['stop'] = 'INTENT_UNKNOWN'
                            lifecycle['stop'] = agent.stop().stop_outcome
                            wait_state('STOPPED')
                            lifecycle['acceptance'] = 'OBSERVED_READY_RUNNING_STOPPED; not remote cleanup proof'
                        finally:
                            agent.close()
                            lifecycle['local_cleanup_confirmed'] = bool(agent._channel and agent._channel.local_cleanup_confirmed)
                    result['scientific_jobs_submitted'] += 1
                    region_job = context.observe(ObservationRequest('consolidation:Region',
                        geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))
                    result['region_job'] = region_job.id
                    region = region_job.result(120); verify_grid(region)
                    result['scientific_jobs_submitted'] += 1
                    job = context.within(region).observe(definition)
                    result['elevation_job'] = job.id
                    observation = job.result(120); verify_grid(observation)
                    expected = Decimal(100) + Decimal(93) / Decimal(4)
                    values = observation.fetch_data(range(20), curve='D2_YX').values
                    if values != (expected,) * 20 or observation.units != 'm':
                        raise RuntimeError('Java result differs from independent 123.25m/20-valid-cell oracle')
                    result['numerical'] = {'status': 'PASSED', 'cells': 20, 'missing': 0,
                        'value': str(expected), 'units': observation.units, 'geometry': observation.geometry.raw}
                    evidence = fetch_execution_evidence(observation)
                    provenance = fetch_provenance(observation)
                    result['execution_evidence'] = {'partial': evidence.partial,
                        'durable_root_commit': evidence.durable_root_commit,
                        'diagnostics': list(evidence.diagnostics),
                        'activities': [{'id': activity.id, 'type': activity.activity_type,
                            'outcome': activity.outcome, 'chart_phases': [chart.phase for chart in activity.charts],
                            'missing': list(activity.missing)} for activity in evidence.activities]}
                    result['provenance'] = {'partial': provenance.partial, 'consistency': provenance.consistency,
                        'offset': provenance.offset, 'next_offset': provenance.next_offset,
                        'links': [{'relationship': link.relationship, 'evidence': link.evidence,
                            'source': {'id': link.source.id, 'kind': link.source.kind, 'type': link.source.activity_type,
                                       'outcome': link.source.outcome},
                            'target': {'id': link.target.id, 'kind': link.target.kind, 'type': link.target.activity_type,
                                       'outcome': link.target.outcome}} for link in provenance.links]}
                    reference = to_handle(observation)
                    with Client(config['urls']['runtime'], config['token'], agent_name=config['username'],
                        **{n: Endpoint(config['urls'][n], config['token']) for n in ('reasoner', 'resources', 'resolver')}) as reconnected:
                        reconnected.initialize_user_scope(local_federation=True)
                        restored = restore_handle(reconnected, reference)
                        if restored.fetch_data(range(20), curve='D2_YX').values != values:
                            raise RuntimeError('Restored observation changed the numerical result')
                    result['restoration'] = 'PASSED; independent Client/HTTP transport and fresh authorized server reads'
                finally:
                    primary = sys.exc_info()[0]
                    release_errors = []
                    for label, owned in (('context_release', context), ('session_release', session)):
                        if owned is not None:
                            try:
                                result[label] = owned.release()
                            except KlabError as error:
                                result[label] = 'UNKNOWN'
                                release_errors.append(type(error).__name__)
                    if release_errors:
                        result['release_errors'] = release_errors
                        if primary is None:
                            raise RuntimeError('Owned scope release outcome unknown; never replay')
                if result.get('context_release') is not True or result.get('session_release') is not True:
                    raise RuntimeError('Owned remote scope release was not confirmed')
                remaining = client.runtime.get_context_info()
                if any(item.session_id == session.id or context.id in item.context_ids for item in remaining):
                    raise RuntimeError('Released owned scopes remain in the authoritative context listing')
                result['remote_cleanup_check'] = 'PASSED; owned session/context absent from fresh authorized listing'
                result['status'] = 'SCIENTIFIC_WORKFLOW_PASSED; FINAL_MILESTONE_RECONCILIATION_PENDING'
        if not minimal:
            result['status'] = 'READINESS_ONLY; NUMERICAL_ACCEPTANCE_NOT_RUN'
    except (KlabError, OSError, RuntimeError, ValueError, LookupError, AssertionError,
            subprocess.SubprocessError, httpx.HTTPError, ImportError) as error:
        (state / 'private-failure-traceback.txt').write_text(traceback.format_exc())
        result.update(status='BLOCKED', error_type=type(error).__name__,
                      error=str(error) if isinstance(error, RuntimeError) else 'Inspect private logs; no mutation replay')
    finally:
        identities = [{'name': name, 'pid': p.pid} for name, p in processes]
        for name, p in reversed(processes):
            if p.poll() is None:
                p.terminate()
                try:
                    p.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    p.kill(); p.wait(timeout=20)
        for log in handles:
            log.close()
        result['owned_processes'] = identities
        result['owned_process_cleanup'] = all(p.poll() is not None for _, p in processes)
        result['existing_java_unchanged'] = all(psutil.pid_exists(pid) and psutil.Process(pid).create_time() == created
            for pid, created in existing.items())
        open_ports = []
        for port in owned_ports:
            with socket.socket() as probe:
                if probe.connect_ex(('127.0.0.1', port)) == 0:
                    open_ports.append(port)
        result['owned_listener_cleanup'] = not open_ports
        if open_ports or not result['owned_process_cleanup'] or not result['existing_java_unchanged']:
            result.update(status='BLOCKED', cleanup_error='Owned cleanup or pre-existing Java identity check failed')
        (state / 'result.json').write_text(json.dumps(result, indent=2))
        print(json.dumps(result, indent=2), flush=True)
    return 0 if result['status'].startswith(('READINESS_ONLY', 'SCIENTIFIC_WORKFLOW_PASSED')) else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('run', 'authority'))
    parser.add_argument('--state', type=Path, required=True)
    parser.add_argument('--minimal', action='store_true', help='Separate v5 synthetic worldview; original fixture not superseded')
    parser.add_argument('--geospatial-component', type=Path, help='Explicit built dependency proposal; not deployment adoption')
    parser.add_argument('--managed-agent', action='store_true', help='One genuine deferred Runtime behavior, no replay')
    args = parser.parse_args()
    if args.action == 'authority':
        hub = load('closeout_authority', ROOT / 'python-client/tools/fixture_hub.py'); hub.SERVICES = PORTS
        hub.serve(args.state)
    else:
        raise SystemExit(run(args.state.resolve(), args.minimal, args.geospatial_component, args.managed_agent))
