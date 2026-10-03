"""Offline pilot package checks, not service approval or an ontology validator.

Requires existing PyYAML and jsonschema Draft 2020-12. No downloads, subprocesses,
network reference resolution, service startup, or source writes are performed.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.metadata
import json
import sys
import yaml
from jsonschema import Draft202012Validator
from referencing import Registry

ROOT = Path(__file__).resolve().parents[2]
PROPOSAL_SCHEMA = ROOT / 'klab.services.resources/src/main/resources/schemas/llm/domain-context-proposal.schema.json'
ROLES = {'PROPOSAL', 'ONTOLOGY', 'DOSSIER', 'SOURCE_QUESTIONS'}

class UniqueLoader(yaml.SafeLoader):
    pass

def unique_mapping(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise ValueError(f'duplicate YAML key: {key}')
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping

UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)

def unique_json(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f'duplicate JSON key: {key}')
        result[key] = value
    return result

def digest(data):
    return hashlib.sha256(data).hexdigest()

def require(condition, message):
    if not condition:
        raise ValueError(message)

def artifact_path(root, relative):
    require(isinstance(relative, str) and relative, 'artifact path is required')
    path = (root / relative).resolve()
    require(path.is_relative_to(root.resolve()), 'artifact path escapes package')
    require(path.is_file(), f'missing artifact: {relative}')
    return path

def check(kind, status, *messages):
    return dict(kind=kind, status=status, messages=list(messages))

def validate_package(root, current_imports=None):
    root = Path(root).resolve()
    checks = []
    try:
        require((root / 'manifest.json').stat().st_size <= 65536, 'manifest byte limit exceeded')
        manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8-sig'), object_pairs_hook=unique_json)
        require(manifest.get('manifestVersion') == 1, 'unsupported pilot manifest version')
        pid, rid = manifest.get('proposalId'), manifest.get('revisionId')
        require(bool(pid) and bool(rid), 'proposal identity/revision are required')
        artifacts = manifest.get('artifacts', [])
        require(len(artifacts) == 4 and {a.get('role') for a in artifacts} == ROLES,
                'exactly four distinct pilot artifact roles are required')
        payloads = {}
        for entry in artifacts:
            require(entry.get('proposalId') == pid and entry.get('revisionId') == rid,
                    'artifact manifest proposal/revision mismatch')
            path = artifact_path(root, entry.get('path'))
            limit = {'PROPOSAL': 4 * 1024 * 1024, 'ONTOLOGY': 2 * 1024 * 1024}.get(entry['role'], 16 * 1024 * 1024)
            require(path.stat().st_size <= limit, 'artifact byte limit exceeded')
            raw = path.read_bytes()
            require(digest(raw) == entry.get('sha256'), f'artifact checksum mismatch: {entry["role"]}')
            payloads[entry['role']] = raw
        proposal = yaml.load(payloads['PROPOSAL'].decode('utf-8-sig'), Loader=UniqueLoader)
        body = proposal['proposal']
        require(body['id'] == pid and body['revision_id'] == rid, 'stored proposal identity/revision mismatch')
        dossier = json.loads(payloads['DOSSIER'].decode('utf-8-sig'), object_pairs_hook=unique_json)
        require(dossier.get('dossier_version') == '0.1', 'unsupported research dossier version')
        require(payloads['SOURCE_QUESTIONS'].decode('utf-8-sig').strip(), 'source questions are empty')
        concepts = {c['id']: c for c in dossier['concepts']}
        assets = {a['asset_id']: a for a in body['assets']}
        mapped = set()
        for mapping in manifest.get('assetMapping', []):
            asset, concept = mapping['proposalAssetId'], mapping['dossierConceptId']
            require(asset in assets and concept in concepts and asset not in mapped, 'invalid or duplicate asset mapping')
            require(assets[asset]['qualified_name'] == concepts[concept]['name'], 'mapped qualified names differ')
            mapped.add(asset)
        require(mapped == set(assets), 'each proposal asset needs explicit dossier mapping')
        checks.append(check('PILOT_MANIFEST', 'PASS', 'Exact four saved-byte hashes and review revision bindings verified',
                            'Research dossier may be broader than explicitly mapped proposal assets'))
    except Exception as exc:
        return {'checks': [check('PILOT_MANIFEST', 'FAIL', str(exc))], 'acceptance': 'BLOCKED'}
    try:
        schema = json.loads(PROPOSAL_SCHEMA.read_text(encoding='utf-8'))
        Draft202012Validator.check_schema(schema)
        # Unknown remote references fail; the bundled proposal schema uses only local $defs.
        validator = Draft202012Validator(schema, registry=Registry())
        errors = sorted(validator.iter_errors(proposal), key=lambda e: str(list(e.absolute_path)))
        checks.append(check('DOCUMENT_SCHEMA', 'FAIL' if errors else 'PASS',
                            *[f'{list(e.absolute_path)}: {e.message}' for e in errors]))
    except Exception as exc:
        checks.append(check('DOCUMENT_SCHEMA', 'BLOCKED', str(exc)))
    try:
        imports = manifest.get('imports', [])
        declared = {i['ontology_id']: i['version'] for i in body['existing_ontologies']}
        require(len(imports) == len(declared) and {i['id'] for i in imports} == set(declared),
                'manifest imports differ from proposal context')
        for entry in imports:
            require(entry.get('revision') == declared[entry['id']], 'manifest import revision differs from proposal')
        if current_imports is None or any(not i.get('sha256') for i in imports):
            checks.append(check('IMPORT_CONTEXT', 'BLOCKED', 'Current authoritative imported source hashes unavailable'))
        else:
            for entry in imports:
                current = current_imports.get(entry['id'])
                require(current is not None and current['revision'] == entry['revision']
                        and current['sha256'] == entry['sha256'], 'current import revision/hash mismatch')
            checks.append(check('IMPORT_CONTEXT', 'PASS', 'Matches supplied snapshot only; caller must establish its authority and freshness'))
    except Exception as exc:
        checks.append(check('IMPORT_CONTEXT', 'FAIL', str(exc)))
    checks.append(check('SCIENTIFIC_REVIEW', 'BLOCKED',
                        'No authenticated scientific decision; client manifest flags cannot grant approval',
                        'Unresolved research semantics: ' + str(len(dossier.get('ambiguities', [])))))
    checks.append(check('ROOT_CONTEXT', 'BLOCKED', 'No verified root/import semantic snapshot'))
    checks.append(check('REASONER', 'NOT_RUN', 'No service started or authoritative knowledge changed'))
    return {'manifestVersion': 1, 'proposalId': pid, 'revisionId': rid,
            'validator': {'jsonschemaVersion': importlib.metadata.version('jsonschema'),
                          'proposalSchemaSha256': digest(PROPOSAL_SCHEMA.read_bytes())},
            'checks': checks, 'acceptance': 'BLOCKED'}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package', type=Path)
    parser.add_argument('--current-imports', type=Path)
    args = parser.parse_args()
    current = json.loads(args.current_imports.read_text()) if args.current_imports else None
    result = validate_package(args.package, current)
    print(json.dumps(result, indent=2))
    # A structurally sound but semantically blocked package is valid review evidence, not an error.
    sys.exit(1 if any(c['status'] == 'FAIL' for c in result['checks']) else 0)
