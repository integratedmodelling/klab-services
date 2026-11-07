"""Explicit joins of upload, ADD source, plan, recorded input and reference graph claims.

No semantic annotation/update endpoint, replay, source admission or native lineage
authority is created by this facade. Hashes of binary bytes and manifests differ.
"""
from dataclasses import dataclass
import hashlib
import json
import math
import re

from .authoring import AuthoringReceipt
from .client import Job, scope_token
from .dependency_graphs import DependencyGraphs, AsyncDependencyGraphs, DependencyGraphSnapshot
from .errors import InvalidRequestError, ProtocolError
from .handles import _decode_observation, to_handle
from .imports import ImportSubmission
from .transport import _normalize_service_url

MANIFEST = 'klab.experimental.import-source/1'


@dataclass(frozen=True)
class InputProvenanceReceipt:
    context_id: str
    observation_id: int
    resource_urn: str
    model_urn: str
    namespace_urn: str
    binary_sha256: str
    binary_bytes: int
    manifest_sha256: str
    annotation_sha256: str
    graph: DependencyGraphSnapshot
    evidence: str = 'Joined server/reference claims only; not native annotation, scientific lineage or permission authority'


class _Inputs:
    _graphs: DependencyGraphs | AsyncDependencyGraphs

    def _timeout(self, timeout):
        if type(timeout) not in (int, float) or not math.isfinite(timeout) or not 0 < timeout <= 60:
            raise InvalidRequestError('Input job waits require an explicit finite timeout in (0,60] seconds')

    def _prepare(self, submission, binding, source, plan_job, observation):
        graphs = self._graphs
        graphs._fence()
        context = graphs.context
        client = graphs._guard._client
        if (not isinstance(submission, ImportSubmission) or not isinstance(binding, AuthoringReceipt)
                or binding.kind != 'NAMESPACE' or binding.operation != 'CREATE'
                or not isinstance(source, str) or not source.strip()):
            raise InvalidRequestError('Explicit upload submission, namespace ADD receipt and exact source required')
        try:
            encoded = source.encode('utf-8')
        except UnicodeError:
            raise InvalidRequestError('Input binding source must be valid UTF-8') from None
        if len(encoded) > 16384:
            raise InvalidRequestError('Input source binding exceeds reference source budget')
        if (not isinstance(submission.source_sha256, str) or re.fullmatch('[0-9a-f]{64}', submission.source_sha256) is None
                or type(submission.source_bytes) is not int or not 0 < submission.source_bytes <= 64*1024*1024):
            raise InvalidRequestError('Upload receipt requires an exact binary SHA256 and bounded byte count')
        for job, service in ((submission.job, 'resources'), (plan_job, 'resolver')):
            if (not isinstance(job, Job) or job.client is not client or job.service != service
                    or scope_token(getattr(job, 'scope', None)) != context.id
                    or (service == 'resources' and (job._decoder is not None or job.result_kind != 'mapping'))
                    or (service == 'resolver' and (job.result_kind != 'dataflow' or not callable(job._decoder)))):
                raise InvalidRequestError('Import and plan jobs must belong to the exact current client/root/service')
            job._check_binding()
        observation_context = getattr(observation, '_context', None)
        if (getattr(observation_context, 'client', None) is not context.client
                or getattr(observation_context, 'id', None) != context.id
                or to_handle(observation).context_id != context.id):
            raise InvalidRequestError('Observation must belong to the explicit current root context')
        if (submission.service_id != binding.service_id or submission.service_url != binding.service_url
                or _normalize_service_url(submission.service_url) != _normalize_service_url(
                client.transport.endpoint('resources').url)):
            raise InvalidRequestError('Upload and source binding must name the same selected Resources origin')
        graphs._public([source, submission.source_sha256, submission.suggested_urn, binding.urn])
        return hashlib.sha256(encoded).hexdigest()

    def _join(self, submission, binding, source, source_sha, plan, imported, current, graph):
        self._graphs._fence()
        self._safe([plan, imported])
        if graph.resources_id != submission.service_id:
            raise ProtocolError('Input evidence does not confirm exact Resources/root/observation')
        # Job.result validates a JSON-object result before this join.
        raw = imported
        rows = raw.get('results')
        if (not isinstance(rows, list) or len(rows) != 1 or not isinstance(rows[0], dict) or rows[0].get('resourceUrn') != submission.suggested_urn
                or rows[0].get('knowledgeClass') != 'RESOURCE' or rows[0].get('serviceId') != graph.resources_id
                or raw.get('empty') is not False):
            raise ProtocolError('Completed import did not confirm the exact uploaded resource')
        nodes = {node.node_id: node for node in graph.nodes}
        model = nodes[graph.root_node_id]
        resources = [node for node in graph.nodes if node.knowledge_class == 'RESOURCE'
            and node.urn == submission.suggested_urn and node.provider_id == graph.resources_id]
        namespaces = [node for node in graph.nodes if node.knowledge_class == 'NAMESPACE'
            and node.urn == binding.urn and node.provider_id == graph.resources_id]
        if len(resources) != 1 or len(namespaces) != 1 or model.knowledge_class != 'MODEL':
            raise ProtocolError('Graph must bind one exact model, admitted namespace and uploaded resource')
        resource, namespace = resources[0], namespaces[0]
        if namespace.source != source or namespace.source_sha256 != source_sha:
            raise ProtocolError('Admitted namespace source differs from graph source binding')
        try:
            manifest = json.loads(resource.source)
        except (ValueError, TypeError):
            raise ProtocolError('Resource dependency source must be an explicit binary-source manifest') from None
        expected = {'protocol': MANIFEST, 'resourceUrn': resource.urn, 'schemaId': submission.schema_id,
            'binarySha256': submission.source_sha256, 'binaryBytes': submission.source_bytes}
        if (not isinstance(manifest, dict) or manifest != expected or type(manifest.get('binaryBytes')) is not int
                or not 0 < manifest['binaryBytes'] <= 64 * 1024 * 1024):
            raise ProtocolError('Uploaded binary hash/bytes/schema do not match the resource manifest')
        requirements = plan.get('requirements') if isinstance(plan, dict) else None
        selected = requirements.get('results') if isinstance(requirements, dict) else None
        if (not isinstance(selected, list) or plan.get('resolutionOutcome') != 'RESOLVED'
                or plan.get('empty') is not False):
            raise ProtocolError('Explicit completed plan must report resolved model and resource requirements')
        computation = plan.get('computation')
        if (not isinstance(computation, list) or len(computation) != 1 or not isinstance(computation[0], dict)
                or computation[0].get('actuatorType') != 'OBSERVE'
                or computation[0].get('computation') != [{'urn':model.urn}] or computation[0].get('children') != []):
            raise ProtocolError('Reference join requires an explicit single selected model actuator, not candidate requirements alone')
        for node in (model, resource):
            matches = [row for row in selected if isinstance(row, dict) and row.get('resourceUrn') == node.urn
                and row.get('knowledgeClass') == node.knowledge_class and row.get('serviceId') == node.provider_id]
            if len(matches) != 1 or matches[0].get('resourceVersion') != node.version or matches[0].get('sourceSha256') != node.source_sha256:
                raise ProtocolError('Selected plan source/version/provider differs from the complete graph')
        direct = {(edge.source_id, edge.target_id) for edge in graph.edges}
        if (model.node_id, resource.node_id) not in direct or (model.node_id, namespace.node_id) not in direct:
            raise ProtocolError('Graph does not explicitly bind selected model to uploaded resource and namespace')
        recorded = current.metadata.get('input')
        expected_input = {'resourceUrn': resource.urn, 'modelUrn': model.urn,
            'sourceSha256': submission.source_sha256, 'sourceBytes': submission.source_bytes,
            'manifestSha256': resource.source_sha256, 'annotationSha256': source_sha,
            'resourceVersion': resource.version, 'modelVersion': model.version}
        if recorded != expected_input:
            raise ProtocolError('Current recorded observation input differs from upload/source/plan/graph evidence')
        return InputProvenanceReceipt(graph.context_id, current.id, resource.urn, model.urn, namespace.urn,
            submission.source_sha256, submission.source_bytes, resource.source_sha256, source_sha, graph)

    def _membership(self, observation, rows):
        self._safe(rows)
        if not isinstance(rows, list) or len(rows) != 1:
            raise ProtocolError('Input provenance requires one currently accessible observation')
        current = _decode_observation(self._graphs.context, rows[0], observation.id)
        if current.urn != observation.urn:
            raise ProtocolError('Observation membership changed during input inspection')
        return current

    def _safe(self, value):
        try:
            self._graphs._guard._safe(value)
        except InvalidRequestError:
            raise ProtocolError('Input evidence contains configured credential material') from None


class InputProvenance(_Inputs):
    """Read completed jobs/current observation and join explicit reference evidence."""
    def __init__(self, context, *, allow_experimental=False):
        self._graphs = DependencyGraphs(context, allow_experimental=allow_experimental)

    def inspect(self, submission, binding, source, plan_job, observation, *, model_urn, timeout=5):
        self._timeout(timeout)
        source_sha = self._prepare(submission, binding, source, plan_job, observation)
        imported = submission.job.result(timeout=timeout)
        plan = plan_job.result(timeout=timeout)
        graph = self._graphs.inspect(model_urn, knowledge_class='MODEL')
        rows = self._graphs.context.client.transport.request('runtime', 'POST', '/api/v1/query',
            json={'type':'QUERY', 'resultType':'OBSERVATION', 'id':observation.id, 'limit':2},
            **{**self._graphs._options(), 'max_response_bytes':1024*1024})
        self._prepare(submission, binding, source, plan_job, observation)
        return self._join(submission, binding, source, source_sha, plan, imported, self._membership(observation, rows), graph)


class AsyncInputProvenance(_Inputs):
    """Native awaited counterpart; no sync offloading or implicit writes."""
    def __init__(self, context, *, allow_experimental=False):
        self._graphs = AsyncDependencyGraphs(context, allow_experimental=allow_experimental)

    async def inspect(self, submission, binding, source, plan_job, observation, *, model_urn, timeout=5):
        self._timeout(timeout)
        source_sha = self._prepare(submission, binding, source, plan_job, observation)
        imported = await submission.job.result(timeout=timeout)
        plan = await plan_job.result(timeout=timeout)
        graph = await self._graphs.inspect(model_urn, knowledge_class='MODEL')
        rows = await self._graphs.context.client.transport.request('runtime', 'POST', '/api/v1/query',
            json={'type':'QUERY', 'resultType':'OBSERVATION', 'id':observation.id, 'limit':2},
            **{**self._graphs._options(), 'max_response_bytes':1024*1024})
        self._prepare(submission, binding, source, plan_job, observation)
        return self._join(submission, binding, source, source_sha, plan, imported, self._membership(observation, rows), graph)
