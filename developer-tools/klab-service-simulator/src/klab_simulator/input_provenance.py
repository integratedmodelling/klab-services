"""Literal owned import/source/plan/input integration, not native annotation science."""
import hashlib
import json

from .authoring import MODEL, NAMESPACE, RESOURCE, SOURCE
from .resource_imports import SCHEMA


def evidence(state, root, principal):
    binding = state.authoring.binding(root, principal)
    manifest = json.dumps({'protocol':'klab.experimental.import-source/1', 'resourceUrn':RESOURCE,
        'schemaId':SCHEMA, 'binarySha256':binding['sourceSha256'], 'binaryBytes':binding['sourceBytes']},
        sort_keys=True, separators=(',', ':'))
    annotation_sha = hashlib.sha256(SOURCE.encode()).hexdigest()
    manifest_sha = hashlib.sha256(manifest.encode()).hexdigest()
    model_source = 'literal selected model ' + MODEL + ' binds namespace ' + NAMESPACE + ' and resource ' + RESOURCE + '\n'
    return binding, manifest, model_source, {**binding, 'manifestSha256':manifest_sha,
        'annotationSha256':annotation_sha, 'resourceVersion':'1.0.1', 'modelVersion':'1.0.1'}


def graph_nodes(state, root, principal, node):
    _, manifest, model_source, _ = evidence(state, root, principal)
    provider = state.service_ids['resources']
    nodes = [node('a'*32, MODEL, 'MODEL', provider, model_source),
        node('b'*32, NAMESPACE, 'NAMESPACE', provider, SOURCE),
        node('c'*32, RESOURCE, 'RESOURCE', provider, manifest)]
    return nodes, (('a'*32,'b'*32), ('a'*32,'c'*32)), 'a'*32
